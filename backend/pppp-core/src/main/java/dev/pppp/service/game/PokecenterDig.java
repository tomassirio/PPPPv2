package dev.pppp.service.game;

import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import eu.rekawek.coffeegb.core.AddressSpace;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Before a link cable goes in, each Gold, Silver or Crystal player digs out to the Cable Club of the Pokémon Center
 * they last walked into, and comes up in front of the receptionist for their link: the trade one (left) or the battle
 * one (middle), facing her. The game's own Dig sound and movement; the stairs then lead down into that Center.
 * <p>
 * How: the game runs one queued "map re-entry" script when the player stands free on the overworld
 * (engine/overworld/events.asm, PlayerEvents: RunMemScript reads wMapReentryScriptQueueFlag, bank, address). Dig can
 * only arrive on a warp tile, so {@link #patch} writes two scripts (trade, battle) into the zero-filled tail of ROM
 * bank 3, built from the bytes of EscapeRopeOrDig.UsedDigOrEscapeRopeScript (engine/events/overworld.asm) in the
 * same bank, so its movement pointers hold: playsound SFX_WARP_TO, applymovement PLAYER .DigOut; then loadmem of
 * wMapGroup, wMapNumber, wXCoord, wYCoord (POKECENTER_2F, the tile below the receptionist) and wDefaultSpawnpoint =
 * SPAWN_N_A, so EnterMapSpawnPoint keeps them; Dig's loadvar VAR_MOVEMENT, PLAYER_NORMAL; newloadmap
 * MAPSETUP_TELEPORT; playsound SFX_WARP_FROM, applymovement PLAYER .DigReturn; turnobject PLAYER, UP; end.
 * The 2F stairs are warp -1, back to wBackupWarpNumber/MapGroup/MapNumber, which arriving by the stairs sets
 * (home/map.asm); {@link #queue} sets them to the 1F stairs of the Center: the one the player stands in, or the one
 * the game respawns at (wLastSpawnMapGroup/Number is the town the player last entered a Center from,
 * EnterMapWarp.SetSpawn; its door is the warp there into a Pokémon Center map).
 * <p>
 * A player may leave where Fly (a town or route) or Dig (a cave or dungeon) would work, or from a Pokémon Center,
 * standing free: no battle, no script, text box or menu, not mid-step or mid-warp, not in the Bug-Catching Contest.
 * Addresses from pret's pokegold.sym, pokesilver.sym and pokecrystal11.sym; constants from
 * constants/ram_constants.asm, map_data_constants.asm and map_setup_constants.asm; Pokecenter2F's objects from
 * maps/Pokecenter2F.asm (the same in both games).
 */
@Service
public class PokecenterDig {
    private static final Logger log = LoggerFactory.getLogger(PokecenterDig.class);

    /**
     * WRAM: wBattleMode, wLinkMode, wScriptRunning (wScriptMode just before), wWindowStackSize, wMapStatus
     * (wMapEventStatus next), wStatusFlags2, wMapReentryScriptQueueFlag (bank, address next), wBackupWarpNumber
     * (wBackupMapGroup, wBackupMapNumber next), wLastSpawnMapGroup (number next), wMapGroup (wMapNumber, wYCoord,
     * wXCoord next), wDefaultSpawnpoint. digScripts: where UsedDigOrEscapeRopeScript may sit in bank 3 (Gold and
     * Silver share the WRAM but not this), the one whose bytes check out is used. Script opcodes
     * (macros/scripts/events.asm): waitbutton, closetext, playsound, applymovement, loadvar, newloadmap, end,
     * turnobject, loadmem.
     */
    record Layout(int battleMode, int linkMode, int scriptRunning, int windowStackSize, int mapStatus,
                  int statusFlags2, int reentryScript, int backupWarp, int lastSpawn, int mapGroup,
                  int defaultSpawnpoint, int[] digScripts, Op op) {}

    record Op(int waitbutton, int closetext, int playsound, int applymovement, int loadvar, int newloadmap, int end,
              int turnobject, int loadmem) {}

    private static final Layout GOLD_SILVER = new Layout(0xD116, 0xD042, 0xD15F, 0xCEAF, 0xD159, 0xD572, 0xD183,
            0xD9F5, 0xD9FB, 0xDA00, 0xCEEC, new int[] {0x4C3F, 0x4C3D},
            new Op(0x53, 0x49, 0x84, 0x68, 0x1E, 0x89, 0x90, 0x75, 0x1B)),
            CRYSTAL = new Layout(0xD22D, 0xC2DC, 0xD438, 0xCF78, 0xD432, 0xD84D, 0xD45C,
                    0xDCAC, 0xDCB2, 0xDCB5, 0xD001, new int[] {0x4C3C},
                    new Op(0x54, 0x49, 0x85, 0x69, 0x1E, 0x8A, 0x91, 0x76, 0x1B));

    private static final int DIG_BANK = 3, SVBK = 0xFF70, SCRIPT_READ = 1, MAPSTATUS_HANDLE = 2, MAPEVENTS_ON = 0,
            BUG_CONTEST_TIMER = 1 << 2, TOWN = 1, ROUTE = 2, CAVE = 4, DUNGEON = 7, MAPSETUP_DOOR = 0xF5,
            MAPSETUP_TELEPORT = 0xF4, SPAWN_N_A = 0xFF, PLAYER = 0, UP = 1, SCRIPT_SIZE = 0x40;
    /** wLinkTimeoutFrames (WRAM bank 0); 12 extensions of 256 frames: 8.5 s + 51 s, about a minute. */
    private static final int GOLD_SILVER_LINK_TIMEOUT = 0xCE5B, CRYSTAL_LINK_TIMEOUT = 0xCF5B, WAIT_EXTENSIONS = 12;
    /** Where the two scripts go: bank 3 is zero from $7a0b (Crystal) / $7a3e (Gold) to its end. */
    private static final int TRADE_SCRIPT = 0x7F00, BATTLE_SCRIPT = TRADE_SCRIPT + SCRIPT_SIZE;
    /** Pokecenter2F: the receptionists stand at (5, 2) trade and (9, 2) battle; the player one step below. */
    private static final int TRADE_X = 5, BATTLE_X = 9, RECEPTIONIST_Y = 3;
    /** The link rooms behind the receptionists. */
    static final Set<String> LINK_ROOMS = Set.of("TRADE_CENTER", "COLOSSEUM", "TIME_CAPSULE");

    /** tail: the Dig script past its text; pc2f: {group, number} of POKECENTER_2F. */
    record Game(Layout at, PokemonGen2 maps, int tail, int[] pc2f) {}

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final Map<String, Optional<Game>> games = new ConcurrentHashMap<>();

    public PokecenterDig(PlayerConsoles consoles, CartridgeService cartridges) {
        this.consoles = consoles;
        this.cartridges = cartridges;
    }

    /**
     * Why the player cannot leave where they are now ("in a battle"), or null when they can: free, or not playing
     * Gold, Silver or Crystal (nothing to check). Their console must be open.
     */
    public String blocker(String userId) {
        Game game = game(userId);
        return game == null ? null : consoles.withBus(userId, bus -> check(game, bus));
    }

    /**
     * Sends the player digging to the receptionist for kind (TRADE or BATTLE), when nothing blocks them. Returns the
     * blocker, as {@link #blocker}.
     */
    public String dig(String userId, String kind) {
        Game game = game(userId);
        return game == null ? null : consoles.withBus(userId, bus -> {
            String why = check(game, bus);
            if (why == null) queue(userId, game, bus, kind);
            return why;
        });
    }

    /** The link room the player stands in (TRADE_CENTER, COLOSSEUM, TIME_CAPSULE), or null. */
    public String linkRoom(String userId) {
        Game game = game(userId);
        byte[] map = game == null ? null : consoles.readBytes(userId, game.at.mapGroup, 2);
        String id = map == null ? null : game.maps.mapId(map[0] & 0xFF, map[1] & 0xFF);
        return id != null && LINK_ROOMS.contains(id) ? id : null;
    }

    private Game game(String userId) {
        String cartridge = consoles.game(userId);
        return cartridge == null ? null : games.computeIfAbsent(cartridge, this::load).orElse(null);
    }

    static String check(Game game, AddressSpace bus) {
        Layout at = game.at;
        int svbk = bus.getByte(SVBK);
        if (svbk != 0xFF && (svbk & 7) > 1) return "busy"; // WRAM bank 1 is out: the game is mid-routine
        if (u8(bus, at.battleMode) != 0) return "in a battle";
        int group = u8(bus, at.mapGroup), number = u8(bus, at.mapGroup + 1);
        if (u8(bus, at.linkMode) != 0 || LINK_ROOMS.contains(game.maps.mapId(group, number))) return "in a link room";
        if (u8(bus, at.scriptRunning) != 0 || u8(bus, at.scriptRunning - 1) == SCRIPT_READ
                || u8(bus, at.windowStackSize) != 0 || u8(bus, at.reentryScript) != 0) {
            return "in a menu, a dialog or an event";
        }
        if (u8(bus, at.mapStatus) != MAPSTATUS_HANDLE || u8(bus, at.mapStatus + 1) != MAPEVENTS_ON) return "on the move";
        if ((u8(bus, at.statusFlags2) & BUG_CONTEST_TIMER) != 0) return "in the Bug-Catching Contest";
        if (game.maps.pokecenter(group, number)) return null;
        int env = game.maps.environment(group, number);
        if (env != TOWN && env != ROUTE && env != CAVE && env != DUNGEON) return "indoors, where neither Fly nor Dig works";
        return null;
    }

    /**
     * Queues the dig, whatever the player is doing (check first). False when there is nowhere to go: they never
     * entered a Pokémon Center. Standing at the receptionist already, nothing happens and it is true.
     */
    static boolean queue(String userId, Game game, AddressSpace bus, String kind) {
        Layout at = game.at;
        int group = u8(bus, at.mapGroup), number = u8(bus, at.mapGroup + 1);
        int x = "BATTLE".equals(kind) ? BATTLE_X : TRADE_X;
        boolean upstairs = group == game.pc2f[0] && number == game.pc2f[1];
        if (upstairs && u8(bus, at.mapGroup + 3) == x && u8(bus, at.mapGroup + 2) == RECEPTIONIST_Y) return true;
        if (!upstairs) { // the 2F stairs lead back down into this Center (1F), or the respawn one
            int g = group, n = number;
            if (!game.maps.pokecenter(g, n)) {
                int[] door = game.maps.pokecenterDoor(u8(bus, at.lastSpawn), u8(bus, at.lastSpawn + 1));
                if (door == null) {
                    log.info("No Pokémon Center to dig {} to", userId);
                    return false;
                }
                g = door[2];
                n = door[3];
            }
            int stairs = game.maps.warpTo(g, n, game.pc2f[0], game.pc2f[1]);
            if (stairs == 0) {
                log.warn("No stairs up in Pokémon Center {}:{}", g, n);
                return false;
            }
            bus.setByte(at.backupWarp, stairs);
            bus.setByte(at.backupWarp + 1, g);
            bus.setByte(at.backupWarp + 2, n);
        }
        int script = "BATTLE".equals(kind) ? BATTLE_SCRIPT : TRADE_SCRIPT;
        bus.setByte(at.reentryScript + 1, DIG_BANK);
        bus.setByte(at.reentryScript + 2, script & 0xFF);
        bus.setByte(at.reentryScript + 3, script >> 8);
        bus.setByte(at.reentryScript, 1); // last: the flag arms it
        log.info("Digging {} to the {} receptionist", userId, kind);
        return true;
    }

    /**
     * The ROM with the two scripts in bank 3 (see the class comment); untouched when it is not Gold, Silver or
     * Crystal as pret has them or the space is not free. Called on the bytes a console inserts.
     */
    public static byte[] patch(byte[] rom) {
        Game game = game(rom).orElse(null);
        if (game == null) return rom;
        byte[] out = rom.clone();
        write(out, game, TRADE_SCRIPT, TRADE_X);
        write(out, game, BATTLE_SCRIPT, BATTLE_X);
        return out;
    }

    private static void write(byte[] rom, Game game, int address, int x) {
        Layout at = game.at;
        Op op = at.op;
        int t = offset(game.tail), o = offset(address);
        var b = new java.io.ByteArrayOutputStream();
        b.write(rom, t, 7); // playsound SFX_WARP_TO, applymovement PLAYER, .DigOut
        int[][] mem = {{at.mapGroup, game.pc2f[0]}, {at.mapGroup + 1, game.pc2f[1]}, {at.mapGroup + 3, x},
                {at.mapGroup + 2, RECEPTIONIST_Y}, {at.defaultSpawnpoint, SPAWN_N_A}};
        for (int[] m : mem) {
            b.write(op.loadmem);
            b.write(m[0] & 0xFF);
            b.write(m[0] >> 8);
            b.write(m[1]);
        }
        b.write(rom, t + 14, 3); // loadvar VAR_MOVEMENT, PLAYER_NORMAL
        b.write(op.newloadmap);
        b.write(MAPSETUP_TELEPORT);
        b.write(rom, t + 19, 7); // playsound SFX_WARP_FROM, applymovement PLAYER, .DigReturn
        b.write(op.turnobject);
        b.write(PLAYER);
        b.write(UP);
        b.write(op.end);
        byte[] script = b.toByteArray();
        System.arraycopy(script, 0, rom, o, script.length);
    }

    private Optional<Game> load(String cartridgeId) {
        try {
            Optional<Game> game = game(Files.readAllBytes(cartridges.require(cartridgeId)));
            if (game.isEmpty()) log.info("No digging to a Pokémon Center in {}", cartridgeId);
            return game;
        } catch (IOException | RuntimeException e) {
            log.warn("No digging to a Pokémon Center for {}: {}", cartridgeId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gold, Silver or Crystal (English, unpatched) with the Dig script where pret has it, laid out as expected, and
     * free space for the two scripts; empty for any other ROM. The tail checked: playsound at 0, applymovement at 3 and 22,
     * loadvar at 14, newloadmap MAPSETUP_DOOR at 17, playsound at 19, end at 26.
     */
    static Optional<Game> game(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        PokemonGen2 maps = crystal == null ? null : PokemonGen2.forRom(rom);
        int[] pc2f = maps == null ? null : maps.map("POKECENTER_2F");
        if (pc2f == null) return Optional.empty();
        Layout at = crystal ? CRYSTAL : GOLD_SILVER;
        Op op = at.op;
        for (int script : at.digScripts) {
            int o = offset(script);
            int[] expect = {op.waitbutton, op.closetext, op.playsound};
            boolean head = true;
            for (int i = 0; i < 3; i++) head &= (rom[o + i] & 0xFF) == expect[i];
            if (!head) continue;
            int t = o + 2;
            boolean tail = (rom[t + 3] & 0xFF) == op.applymovement && (rom[t + 14] & 0xFF) == op.loadvar
                    && (rom[t + 17] & 0xFF) == op.newloadmap && (rom[t + 18] & 0xFF) == MAPSETUP_DOOR
                    && (rom[t + 19] & 0xFF) == op.playsound && (rom[t + 22] & 0xFF) == op.applymovement
                    && (rom[t + 26] & 0xFF) == op.end;
            boolean free = true;
            for (int i = offset(TRADE_SCRIPT); i < offset(BATTLE_SCRIPT) + SCRIPT_SIZE; i++) free &= rom[i] == 0;
            if (tail && free) return Optional.of(new Game(at, maps, script + 2, pc2f));
        }
        return Optional.empty();
    }

    /**
     * A frame hook for a console (one each): while its cable is plugged in and its player stands upstairs in a
     * Pokémon Center, the receptionist's "Please wait..." waits about a minute for the friend instead of 8.5 s.
     * WaitForLinkedFriend (engine/link/link.asm) counts wLinkTimeoutFrames down from $02FF, one a frame, the low byte
     * first; halfway through its last 256 frames the high byte goes back to 2, at most WAIT_EXTENSIONS times per
     * wait (a fresh $02FF starts a new one). A browser player has to get through the dialog and the save first, and
     * one person playing both ends has to switch windows. The other link timeouts (the same variable, link only) only
     * get longer. The game is told by its ROM header; anything but Gold, Silver or Crystal is left alone.
     */
    public static Consumer<AddressSpace> patientReceptionist(BooleanSupplier linked) {
        return new Consumer<>() {
            Boolean crystal;
            int[] pc2f;
            int extensions;

            @Override
            public void accept(AddressSpace bus) {
                if (!linked.getAsBoolean()) return;
                if (crystal == null) {
                    byte[] title = new byte[15];
                    for (int i = 0; i < 15; i++) title[i] = (byte) bus.getByte(0x134 + i);
                    String t = new String(title, java.nio.charset.StandardCharsets.US_ASCII);
                    crystal = t.equals("PM_CRYSTAL\0BYTE") ? Boolean.TRUE
                            : t.equals("POKEMON_GLDAAUE") || t.equals("POKEMON_SLVAAXE") ? Boolean.FALSE : null;
                    if (crystal == null) {
                        crystal = Boolean.FALSE;
                        pc2f = new int[] {-1, -1}; // not a game we know: never matches
                        return;
                    }
                    String[][] ids = PokemonGen2.mapConstants(crystal);
                    for (int g = 1; g < ids.length && pc2f == null; g++) {
                        for (int n = 1; n < ids[g].length; n++) if ("POKECENTER_2F".equals(ids[g][n])) pc2f = new int[] {g, n};
                    }
                    if (pc2f == null) pc2f = new int[] {-1, -1};
                }
                Layout at = crystal ? CRYSTAL : GOLD_SILVER;
                int timeout = crystal ? CRYSTAL_LINK_TIMEOUT : GOLD_SILVER_LINK_TIMEOUT;
                int lo = u8(bus, timeout), hi = u8(bus, timeout + 1);
                if (hi == 2 && lo == 0xFF) extensions = 0;
                if (hi != 1 || lo != 0x80 || extensions >= WAIT_EXTENSIONS) return;
                int svbk = bus.getByte(SVBK);
                if (svbk != 0xFF && (svbk & 7) > 1) return;
                if (u8(bus, at.mapGroup) != pc2f[0] || u8(bus, at.mapGroup + 1) != pc2f[1]) return;
                bus.setByte(timeout + 1, 2);
                extensions++;
            }
        };
    }

    private static int offset(int address) {
        return DIG_BANK * 0x4000 + address - 0x4000;
    }

    private static int u8(AddressSpace bus, int address) {
        return bus.getByte(address) & 0xFF;
    }
}
