package dev.pppp.service.game;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.service.CartridgeService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Other players on your map, drawn into your own screen. Every Gold, Silver or Crystal console reports where its
 * player stands each frame it shows, and draws the players who stand on the same map, half see-through, over its
 * picture. Maps match by pret map constant, so Gold and Crystal players meet. Nothing is written to any game: a
 * ghost cannot be bumped into or talked to.
 * <p>
 * The player always sits at screen pixel 64, 60 and the background scrolls under them (hSCX/hSCY, one pixel a
 * frame while walking). The game resets the scroll whenever it redraws the screen (a menu, a warp), so the scroll
 * is anchored to the tile coordinates on every frame the player stands still (scroll on a 16-pixel boundary).
 * wXCoord/wYCoord flip to the next tile a frame or two before the scroll ends, so the position comes from the
 * scroll alone. Addresses from pret's pokegold.sym and pokecrystal11.sym; checked against the real ROMs.
 */
@Service
public class Ghosts {
    private static final Logger log = LoggerFactory.getLogger(Ghosts.class);

    /**
     * mapGroup: wMapGroup (wMapNumber, wYCoord, wXCoord follow); playerStruct: wPlayerStruct (object_struct:
     * OBJECT_SPRITE at 0, OBJECT_DIRECTION at 8); scriptMode: wScriptMode (SCRIPT_READ = 1 while a script, a text
     * box or a menu runs); windowStackSize: wWindowStackSize; battleMode: wBattleMode; scroll: hSCX (hSCY next);
     * sprites: OverworldSprites (data/sprites/sprites.asm).
     */
    record Layout(int mapGroup, int playerStruct, int scriptMode, int windowStackSize, int battleMode,
                          int scroll, int sprites) {}

    static final Layout GOLD_SILVER = new Layout(0xDA00, 0xD1FD, 0xD15E, 0xCEAF, 0xD116, 0xFFD1, rom(0x05, 0x47DE)),
            CRYSTAL = new Layout(0xDCB5, 0xD4D6, 0xD437, 0xCF78, 0xD22D, 0xFFCF, rom(0x05, 0x4736));

    /** Where the player's sprite sits on screen (its OAM entries: y 76, x 72). */
    static final int SCREEN_X = 64, SCREEN_Y = 60;
    private static final int SIZE = 16, SCREEN_W = 160, SCREEN_H = 144;
    private static final int OBJECT_DIRECTION = 8, SCRIPT_READ = 1, WALKING_SPRITE = 1, SPRITE_CHRIS = 1,
            SPRITE_ENTRY = 6, FRAME_BYTES = 4 * 16, NUM_SPRITES = 0x66; // overworld_sprite: dw gfx; db size, bank, type, palette
    /** The link rooms show the real partner, a ghost would stand on top of them. */
    private static final Set<String> LINK_ROOMS = Set.of("TRADE_CENTER", "COLOSSEUM", "TIME_CAPSULE",
            "MOBILE_TRADE_ROOM", "MOBILE_BATTLE_ROOM");
    /** constants/sprite_data_constants.asm PAL_OW_*: red, blue, green, brown, pink, silver, tree, rock (the main colour). */
    private static final int[] PALETTE_MAIN = {0xE04848, 0x4878E8, 0x38A048, 0xA06830, 0xF070B0, 0x9098A0, 0x58A048, 0x907050};
    private static final int SKIN = 0xF8D0A8, OUTLINE = 0x181818;
    /** How much of the ghost shows over the game, out of 256. */
    private static final int ALPHA = 192;

    /** A player on a map: world pixels of their sprite's top left, and the 16x16 frame to draw (0 = see-through). */
    record Spot(String map, int x, int y, int[] sprite) {}

    /** A player on someone's screen (a ghost, or themselves): whose, and the screen pixel of the sprite's top left (may be off the edge). */
    public record OnScreen(String user, int x, int y) {}

    private record Game(Layout at, byte[] rom, String[][] maps, Map<Integer, int[][]> sprites) {}

    /** A ghost as drawn into a frame: its 16x16 sprite and the screen pixel of its top left. */
    record Drawn(int[] sprite, int x, int y) {}

    /** The scroll's anchor: scroll = anchor + 16 * tile while standing still. Per player, owned by their console's thread. */
    private static final class Camera {
        int anchorX, anchorY;
        boolean anchored;
        String map;
    }

    private final CartridgeService cartridges;
    private final Map<String, Optional<Game>> games = new ConcurrentHashMap<>();
    private final Map<String, Camera> cameras = new ConcurrentHashMap<>();
    private final Map<String, Spot> spots = new ConcurrentHashMap<>();
    private final Map<String, List<OnScreen>> onScreen = new ConcurrentHashMap<>();
    /** The ghosts each player's last readable frame showed, for the frames whose RAM cannot be read. */
    private final Map<String, List<Drawn>> drawn = new ConcurrentHashMap<>();

    public Ghosts(CartridgeService cartridges) {
        this.cartridges = cartridges;
    }

    /**
     * On the player's console thread, for each frame it shows: records where they stand and draws the other
     * players on their map into pixels. A console that is not Gold, Silver or Crystal is left alone.
     */
    public void frame(String userId, String cartridgeId, EmulatorHost host, int[] pixels) {
        Game game = games.computeIfAbsent(cartridgeId, this::load).orElse(null);
        if (game == null) return;
        Layout at = game.at;
        byte[] map = host.readBytes(at.mapGroup, 4), player = host.readBytes(at.playerStruct, OBJECT_DIRECTION + 1),
                mode = host.readBytes(at.scriptMode, 1), battle = host.readBytes(at.battleMode, 1),
                windows = host.readBytes(at.windowStackSize, 1), scroll = host.readBytes(at.scroll, 2);
        // Another WRAM bank is in (Crystal swaps them many times a second): nothing can be read this frame, so the
        // ghosts stay where the last frame had them instead of blinking out (onScreen keeps the last list too).
        if (map == null || player == null || mode == null || battle == null || windows == null) {
            drawLast(userId, pixels);
            return;
        }
        // In a menu, a text box, a cutscene or a battle the scroll means something else: others keep seeing
        // the player where they last stood, and the player sees no one.
        if (mode[0] == SCRIPT_READ || battle[0] != 0 || windows[0] != 0) {
            onScreen.put(userId, List.of());
            drawn.remove(userId);
            return;
        }
        String here = mapId(game, map[0] & 0xFF, map[1] & 0xFF);
        Camera camera = cameras.computeIfAbsent(userId, id -> new Camera());
        int tileY = map[2] & 0xFF, tileX = map[3] & 0xFF, scrollX = scroll[0] & 0xFF, scrollY = scroll[1] & 0xFF;
        if (scrollX % SIZE == 0 && scrollY % SIZE == 0) {
            camera.anchorX = scrollX - SIZE * tileX;
            camera.anchorY = scrollY - SIZE * tileY;
            camera.anchored = true;
            camera.map = here;
        }
        if (!camera.anchored || here == null || !here.equals(camera.map) || LINK_ROOMS.contains(here)) {
            spots.remove(userId); // a new map not redrawn yet, or a link room
            onScreen.put(userId, List.of());
            drawn.remove(userId);
            return;
        }
        int x = world(tileX, scrollX, camera.anchorX), y = world(tileY, scrollY, camera.anchorY);
        spots.put(userId, new Spot(here, x, y, sprite(game, player[0] & 0xFF, player[OBJECT_DIRECTION] >> 2 & 3, x, y)));
        List<OnScreen> seen = new ArrayList<>(List.of(new OnScreen(userId, SCREEN_X, SCREEN_Y))); // the player, first
        List<Drawn> ghosts = new ArrayList<>();
        spots.forEach((other, s) -> {
            int sx = SCREEN_X + s.x - x, sy = SCREEN_Y + s.y - y;
            if (other.equals(userId) || !here.equals(s.map) || sx <= -SIZE || sx >= SCREEN_W || sy <= -SIZE || sy >= SCREEN_H) return;
            ghosts.add(new Drawn(s.sprite, sx, sy));
            seen.add(new OnScreen(other, sx, sy));
        });
        drawAll(userId, pixels, ghosts);
        onScreen.put(userId, List.copyOf(seen));
    }

    /**
     * Who is on the player's screen in the frame just drawn: the player themselves first, then the ghosts. Empty in a
     * menu, a text box, a battle, a link room or while a new map loads.
     */
    public List<OnScreen> onScreen(String userId) {
        return onScreen.getOrDefault(userId, List.of());
    }

    /** The player left: they vanish from everyone's screen. */
    public void leave(String userId) {
        spots.remove(userId);
        cameras.remove(userId);
        onScreen.remove(userId);
        drawn.remove(userId);
    }

    /** Draws these ghosts into the player's frame and remembers them for drawLast. */
    void drawAll(String userId, int[] pixels, List<Drawn> ghosts) {
        for (Drawn g : ghosts) draw(pixels, g.sprite, g.x, g.y);
        drawn.put(userId, List.copyOf(ghosts));
    }

    /** Draws the ghosts of the player's last readable frame again. */
    void drawLast(String userId, int[] pixels) {
        for (Drawn g : drawn.getOrDefault(userId, List.of())) draw(pixels, g.sprite, g.x, g.y);
    }

    /**
     * World pixel of a coordinate: 16 * tile, plus how far the scroll has moved off that tile, which is within a
     * step (a ledge hop is two) of it; a byte's wrap-around covers the scroll register's.
     */
    static int world(int tile, int scroll, int anchor) {
        return SIZE * tile + (byte) (scroll - anchor - SIZE * tile);
    }

    /** Blends sprite (16x16, 0 = see-through) into the 160x144 screen with its top left at x, y, clipped. */
    static void draw(int[] pixels, int[] sprite, int x, int y) {
        for (int row = Math.max(0, -y); row < SIZE && y + row < SCREEN_H; row++) {
            for (int col = Math.max(0, -x); col < SIZE && x + col < SCREEN_W; col++) {
                int c = sprite[row * SIZE + col];
                if (c == 0) continue;
                int i = (y + row) * SCREEN_W + x + col, under = pixels[i];
                pixels[i] = mix(c >> 16 & 0xFF, under >> 16 & 0xFF) << 16 | mix(c >> 8 & 0xFF, under >> 8 & 0xFF) << 8
                        | mix(c & 0xFF, under & 0xFF);
            }
        }
    }

    private static int mix(int top, int under) {
        return (top * ALPHA + under * (256 - ALPHA)) >> 8;
    }

    /**
     * The frame a walking sprite shows: its graphics are six 16x16 frames (down, up, left; then the same walking),
     * right is left mirrored. Mid-step shows the walking frame, down and up mirrored on every other step.
     */
    private static int[] sprite(Game game, int id, int facing, int x, int y) {
        int[][] frames = game.sprites.computeIfAbsent(id, i -> frames(game, i));
        if (frames == null) frames = game.sprites.computeIfAbsent(SPRITE_CHRIS, i -> frames(game, i));
        int[] frame = {0, 1, 2, 2}; // by facing: DOWN, UP, LEFT, RIGHT (OBJECT_DIRECTION >> 2)
        int offset = Math.floorMod(x, SIZE) + Math.floorMod(y, SIZE);
        boolean walking = offset >= 4 && offset < 12, mirror = facing == 3;
        if (walking && facing < 2 && Math.floorDiv(x + y, SIZE) % 2 != 0) mirror = true;
        return frames[(walking ? 3 : 0) + frame[facing] + (mirror ? 6 : 0)];
    }

    /** Frames 0-5 as drawn, 6-11 mirrored; null when the id is no walking sprite of this ROM. */
    private static int[][] frames(Game game, int id) {
        return frames(game.rom, game.at.sprites, id);
    }

    /** As above from a ROM and its OverworldSprites table (also PlayerSprites' previews). */
    static int[][] frames(byte[] rom, int table, int id) {
        if (id < 1 || id > NUM_SPRITES) return null;
        int entry = table + (id - 1) * SPRITE_ENTRY;
        int address = (rom[entry] & 0xFF) | (rom[entry + 1] & 0xFF) << 8, bank = rom[entry + 3] & 0xFF,
                type = rom[entry + 4] & 0xFF, palette = rom[entry + 5] & 7;
        int gfx = rom(bank, address);
        if (type != WALKING_SPRITE || address < 0x4000 || gfx + 6 * FRAME_BYTES > rom.length) return null;
        int[] colours = {0, 0xFF000000 | SKIN, 0xFF000000 | PALETTE_MAIN[palette], 0xFF000000 | OUTLINE};
        int[][] out = new int[12][SIZE * SIZE];
        for (int f = 0; f < 6; f++) {
            for (int tile = 0; tile < 4; tile++) { // top left, top right, bottom left, bottom right
                for (int row = 0; row < 8; row++) {
                    int at = gfx + f * FRAME_BYTES + tile * 16 + row * 2, lo = rom[at] & 0xFF, hi = rom[at + 1] & 0xFF;
                    for (int col = 0; col < 8; col++) {
                        int c = colours[(lo >> 7 - col & 1) | (hi >> 7 - col & 1) << 1];
                        int px = tile % 2 * 8 + col, py = tile / 2 * 8 + row;
                        out[f][py * SIZE + px] = c;
                        out[f + 6][py * SIZE + SIZE - 1 - px] = c;
                    }
                }
            }
        }
        return out;
    }

    private static String mapId(Game game, int group, int number) {
        return group >= 1 && group < game.maps.length && number >= 1 && number < game.maps[group].length
                ? game.maps[group][number] : null;
    }

    private Optional<Game> load(String cartridgeId) {
        try {
            byte[] rom = Files.readAllBytes(cartridges.require(cartridgeId));
            Boolean crystal = PokemonGen2.crystal(rom);
            if (crystal == null) return Optional.empty();
            return Optional.of(new Game(crystal ? CRYSTAL : GOLD_SILVER, rom, PokemonGen2.mapConstants(crystal),
                    new ConcurrentHashMap<>()));
        } catch (IOException | RuntimeException e) {
            log.warn("No ghosts for {}: {}", cartridgeId, e.getMessage());
            return Optional.empty();
        }
    }

    private static int rom(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }
}
