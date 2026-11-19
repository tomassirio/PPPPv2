package dev.pppp.service.game;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The trainer a Gold, Silver or Crystal player walks around as. GetPlayerSprite (engine/overworld/overworld.asm) takes
 * the player's sprite from ChrisStateSprites (data/sprites/player_sprites.asm: player state, sprite; PLAYER_NORMAL
 * first), Crystal's girls from KrisStateSprites, and falls back to an immediate `ld a, SPRITE_CHRIS`. {@link #patch}
 * puts the chosen walking sprite in those three places of the console's in-memory ROM, so the game draws it and
 * the other players' ghosts follow (Ghosts reads the sprite from RAM). Biking and surfing keep their own sprites.
 * Colours: Gold and Silver give the player their sprite's own palette (PlayerObjectTemplate has none, so
 * GetSpritePalette); Crystal's SpawnPlayer (engine/overworld/player_object.asm) forces PAL_NPC_RED, or PAL_NPC_BLUE
 * for Kris (`ln e, PAL_NPC_*, OBJECTTYPE_SCRIPT`), which the patch sets to the sprite's own palette from
 * OverworldSprites (PAL_NPC_x is PAL_OW_x + 8, constants/sprite_data_constants.asm; clearing it to 0 still drew red).
 * Sprite ids from constants/sprite_constants.asm (the same in pokegold and pokecrystal up to $5f; Crystal adds Kris
 * at $60); addresses from pokegold.sym, pokesilver.sym, pokecrystal11.sym.
 */
public final class PlayerSprites {
    private PlayerSprites() {}

    /** A trainer to pick: key (stored, as the pret constant without SPRITE_), name shown, sprite id. */
    public record Choice(String key, String name, int id) {}

    public static final String DEFAULT = "CHRIS";

    /** The walking human sprites worth being, in picker order; each is checked against the ROM (walking type). */
    private static final List<Choice> ALL = List.of(
            new Choice("CHRIS", "Chris", 0x01), new Choice("KRIS", "Kris", 0x60), new Choice("RED", "Red", 0x06),
            new Choice("BLUE", "Blue", 0x07), new Choice("RIVAL", "Silver", 0x04), new Choice("OAK", "Prof. Oak", 0x05),
            new Choice("ELM", "Prof. Elm", 0x10), new Choice("BILL", "Bill", 0x08), new Choice("KURT", "Kurt", 0x0B),
            new Choice("FALKNER", "Falkner", 0x12), new Choice("BUGSY", "Bugsy", 0x14),
            new Choice("WHITNEY", "Whitney", 0x13), new Choice("MORTY", "Morty", 0x15), new Choice("CHUCK", "Chuck", 0x16),
            new Choice("JASMINE", "Jasmine", 0x17), new Choice("PRYCE", "Pryce", 0x18), new Choice("CLAIR", "Clair", 0x19),
            new Choice("BROCK", "Brock", 0x1A), new Choice("MISTY", "Misty", 0x1D), new Choice("SURGE", "Lt. Surge", 0x1F),
            new Choice("ERIKA", "Erika", 0x20), new Choice("JANINE", "Janine", 0x0A), new Choice("SABRINA", "Sabrina", 0x22),
            new Choice("BLAINE", "Blaine", 0x0D), new Choice("WILL", "Will", 0x11), new Choice("KOGA", "Koga", 0x21),
            new Choice("BRUNO", "Bruno", 0x1C), new Choice("KAREN", "Karen", 0x1B), new Choice("LANCE", "Lance", 0x1E),
            new Choice("COOLTRAINER_M", "Cooltrainer ♂", 0x23), new Choice("COOLTRAINER_F", "Cooltrainer ♀", 0x24),
            new Choice("YOUNGSTER", "Youngster", 0x27), new Choice("LASS", "Lass", 0x28),
            new Choice("BUG_CATCHER", "Bug Catcher", 0x25), new Choice("TWIN", "Twin", 0x26),
            new Choice("BEAUTY", "Beauty", 0x2A), new Choice("TEACHER", "Teacher", 0x29),
            new Choice("SUPER_NERD", "Super Nerd", 0x2B), new Choice("ROCKER", "Rocker", 0x2C),
            new Choice("POKEFAN_M", "Pokéfan ♂", 0x2D), new Choice("POKEFAN_F", "Pokéfan ♀", 0x2E),
            new Choice("FISHER", "Fisher", 0x3A), new Choice("SCIENTIST", "Scientist", 0x3C),
            new Choice("KIMONO_GIRL", "Kimono Girl", 0x3D), new Choice("SAGE", "Sage", 0x3E),
            new Choice("GENTLEMAN", "Gentleman", 0x40), new Choice("BLACK_BELT", "Black Belt", 0x41),
            new Choice("SAILOR", "Sailor", 0x49), new Choice("BIKER", "Biker", 0x4A), new Choice("OFFICER", "Officer", 0x43),
            new Choice("ROCKET", "Rocket Grunt ♂", 0x35), new Choice("ROCKET_GIRL", "Rocket Grunt ♀", 0x36),
            new Choice("GRAMPS", "Gramps", 0x2F), new Choice("GRANNY", "Granny", 0x30), new Choice("NURSE", "Nurse Joy", 0x37));

    private static final int SPRITE_CHRIS = 0x01, SPRITE_CHRIS_BIKE = 0x02, SPRITE_KRIS = 0x60, SPRITE_KRIS_BIKE = 0x61,
            PLAYER_NORMAL = 0, PLAYER_BIKE = 1, LD_A = 0x3E, JR = 0x18, LD_E = 0x1E, SCAN = 0x40,
            NPC_RED = 0x80, NPC_BLUE = 0x90, // PAL_NPC_RED / BLUE << 4 | OBJECTTYPE_SCRIPT
            NPC_PALETTES = 8, SPRITE_ENTRY = 6, SPRITE_PALETTE = 5; // PAL_NPC_RED; overworld_sprite: dw, db x4

    /**
     * ROM offsets of GetPlayerSprite, ChrisStateSprites, KrisStateSprites (-1 in Gold/Silver), OverworldSprites, the
     * last overworld sprite id (NUM_OVERWORLD_SPRITES), and SpawnPlayer where it forces a palette (-1 in Gold/Silver).
     */
    private record Layout(int getPlayerSprite, int chris, int kris, int overworldSprites, int lastSprite, int spawnPlayer) {}

    private static final Layout GOLD_SILVER = new Layout(rom(0x05, 0x4185), rom(0x05, 0x41A8), -1, rom(0x05, 0x47DE), 0x5F, -1),
            CRYSTAL = new Layout(rom(0x05, 0x4183), rom(0x05, 0x41B7), rom(0x05, 0x41C0), rom(0x05, 0x4736), 0x66,
                    rom(0x02, 0x4029));

    /** The choices this ROM can draw (walking sprites there), or none for a ROM that is not Gold, Silver or Crystal. */
    public static List<Choice> choices(byte[] rom) {
        Layout at = layout(rom);
        if (at == null) return List.of();
        return ALL.stream().filter(c -> c.id <= at.lastSprite && Ghosts.frames(rom, at.overworldSprites, c.id) != null).toList();
    }

    /** The choice for key in this ROM, or null when it has no such trainer. */
    public static Choice choice(byte[] rom, String key) {
        return key == null ? null : choices(rom).stream().filter(c -> c.key.equals(key)).findFirst().orElse(null);
    }

    /** Sprite bytes of the two possible friend objects in TradeCenter and Colosseum. The map callback hides
     * one according to the cable end; neither object is the local player. Resolve the choice in the viewing ROM. */
    public static Map<Integer, Integer> cableFriend(byte[] rom, String key) {
        Layout at = layout(rom);
        if (at == null) return Map.of();
        int[] rooms = at == CRYSTAL ? new int[]{0x193443, 0x1934D1} : new int[]{0x1714ED, 0x17156E};
        for (int offset : rooms) {
            if (offset + 26 > rom.length || rom[offset - 1] != 2) return Map.of();
            for (int side = 0; side < 2; side++) {
                int obj = offset + 13 * side;
                if (rom[obj] != SPRITE_CHRIS || rom[obj + 1] != 8 || rom[obj + 2] != 7 + 3 * side
                        || rom[obj + 3] != 9 - side || rom[obj + 11] != side) return Map.of();
            }
        }
        Choice c = key == null || DEFAULT.equals(key) ? null : choice(rom, key);
        int sprite = c == null ? SPRITE_CHRIS : c.id;
        var result = new HashMap<Integer, Integer>();
        for (int offset : rooms) {
            result.put(offset, sprite);
            result.put(offset + 13, sprite);
        }
        return result;
    }

    /**
     * The ROM with the player walking as key's trainer; untouched for the default, an unknown key, another game, or
     * bytes that are not where pret has them.
     */
    public static byte[] patch(byte[] rom, String key) {
        Layout at = layout(rom);
        Choice c = at == null || key == null || DEFAULT.equals(key) ? null : choice(rom, key);
        if (c == null) return rom;
        if (!table(rom, at.chris, SPRITE_CHRIS, SPRITE_CHRIS_BIKE)) return rom;
        if (at.kris >= 0 && !table(rom, at.kris, SPRITE_KRIS, SPRITE_KRIS_BIKE)) return rom;
        int fallback = -1;
        for (int i = at.getPlayerSprite; i < at.getPlayerSprite + SCAN; i++) {
            if ((rom[i] & 0xFF) == LD_A && (rom[i + 1] & 0xFF) == SPRITE_CHRIS && (rom[i + 2] & 0xFF) == JR) fallback = i + 1;
        }
        if (fallback < 0) return rom;
        byte[] out = rom.clone();
        out[at.chris + 1] = (byte) c.id;
        if (at.kris >= 0) out[at.kris + 1] = (byte) c.id;
        out[fallback] = (byte) c.id;
        if (at.spawnPlayer >= 0) { // Crystal: the sprite's own colours instead of red (blue for Kris)
            int palette = rom[at.overworldSprites + (c.id - 1) * SPRITE_ENTRY + SPRITE_PALETTE] & 7;
            for (int i = at.spawnPlayer; i < at.spawnPlayer + SCAN; i++) {
                int v = rom[i + 1] & 0xFF;
                if ((rom[i] & 0xFF) == LD_E && (v == NPC_RED || v == NPC_BLUE)) out[i + 1] = (byte) ((NPC_PALETTES + palette) << 4);
            }
        }
        return out;
    }

    /**
     * The trainer as a 16x96 PNG sheet, like PokemonGen2#playerSprite: six 16x16 frames down, up, left, then each
     * one's walking frame, in the sprite's own palette. Null when this ROM cannot draw it.
     */
    public static byte[] sheet(byte[] rom, String key) {
        Layout at = layout(rom);
        Choice c = at == null ? null : choice(rom, key == null ? DEFAULT : key);
        int[][] frames = c == null ? null : Ghosts.frames(rom, at.overworldSprites, c.id);
        if (frames == null) return null;
        var img = new BufferedImage(16, 96, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < 6; f++) img.setRGB(0, f * 16, 16, 16, frames[f], 0, 16);
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            return null;
        }
        return out.toByteArray();
    }

    /** PLAYER_NORMAL, the standing sprite, PLAYER_BIKE, the bike sprite: the table pret has. */
    private static boolean table(byte[] rom, int at, int normal, int bike) {
        return (rom[at] & 0xFF) == PLAYER_NORMAL && (rom[at + 1] & 0xFF) == normal
                && (rom[at + 2] & 0xFF) == PLAYER_BIKE && (rom[at + 3] & 0xFF) == bike;
    }

    private static Layout layout(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        return crystal == null ? null : crystal ? CRYSTAL : GOLD_SILVER;
    }

    private static int rom(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }
}
