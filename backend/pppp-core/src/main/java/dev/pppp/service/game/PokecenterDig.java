package dev.pppp.service.game;

import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import eu.rekawek.coffeegb.core.AddressSpace;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Before a link cable goes in, each Gold, Silver or Crystal player digs out to the Pokémon Center they last walked
 * into, with the game's own Dig: its sound, the player spinning into the ground and back up, and the door warp.
 * <p>
 * How: the game runs one queued "map re-entry" script when the player stands free on the overworld
 * (engine/overworld/events.asm, PlayerEvents: RunMemScript reads wMapReentryScriptQueueFlag, bank, address). That
 * gets the tail of EscapeRopeOrDig.UsedDigOrEscapeRopeScript (engine/events/overworld.asm), from its playsound on,
 * past the waitbutton and closetext of the "used DIG!" text. Dig's destination is wNextWarp, wNextMapGroup,
 * wNextMapNumber (newloadmap MAPSETUP_DOOR, EnterMapWarp): here the inside of the Center's door, as if walked in.
 * wPrevWarp and wPrevMap are the door outside, which EnterMapWarp keeps for Dig and the respawn, as walking in does.
 * The Center is the one the game respawns at: wLastSpawnMapGroup/Number is the town the player last entered a Center
 * from (EnterMapWarp.SetSpawn), and its door is the warp there into a Pokémon Center map.
 * <p>
 * A player may leave where Fly (a town or route) or Dig (a cave or dungeon) would work, standing free: no battle, no
 * script, text box or menu, not mid-step or mid-warp, not in the Bug-Catching Contest. Already in a Pokémon Center,
 * they stay. Addresses from pret's pokegold.sym, pokesilver.sym and pokecrystal11.sym; constants from
 * constants/ram_constants.asm and map_data_constants.asm.
 */
@Service
public class PokecenterDig {
    private static final Logger log = LoggerFactory.getLogger(PokecenterDig.class);

    /**
     * WRAM: wBattleMode, wLinkMode, wScriptRunning (wScriptMode just before), wWindowStackSize, wMapStatus
     * (wMapEventStatus next), wStatusFlags2, wMapReentryScriptQueueFlag (bank, address next), wNextWarp (wNextMapGroup,
     * wNextMapNumber, wPrevWarp, wPrevMapGroup, wPrevMapNumber next), wLastSpawnMapGroup (number next), wMapGroup
     * (number next). digScripts: where UsedDigOrEscapeRopeScript may sit in bank 3 (Gold and Silver share the WRAM
     * but not this), the one whose bytes check out is used. Script opcodes: waitbutton, closetext, playsound
     * (macros/scripts/events.asm).
     */
    record Layout(int battleMode, int linkMode, int scriptRunning, int windowStackSize, int mapStatus,
                          int statusFlags2, int reentryScript, int nextWarp, int lastSpawn, int mapGroup,
                          int[] digScripts, int[] opcodes) {}

    private static final Layout GOLD_SILVER = new Layout(0xD116, 0xD042, 0xD15F, 0xCEAF, 0xD159, 0xD572, 0xD183,
            0xD043, 0xD9FB, 0xDA00, new int[] {0x4C3F, 0x4C3D}, new int[] {0x53, 0x49, 0x84}),
            CRYSTAL = new Layout(0xD22D, 0xC2DC, 0xD438, 0xCF78, 0xD432, 0xD84D, 0xD45C,
                    0xD146, 0xDCB2, 0xDCB5, new int[] {0x4C3C}, new int[] {0x54, 0x49, 0x85});

    private static final int DIG_BANK = 3, SVBK = 0xFF70, SCRIPT_READ = 1, MAPSTATUS_HANDLE = 2, MAPEVENTS_ON = 0,
            BUG_CONTEST_TIMER = 1 << 2, TOWN = 1, ROUTE = 2, CAVE = 4, DUNGEON = 7;

    record Game(Layout at, PokemonGen2 maps, int script) {}

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final Map<String, Optional<Game>> games = new ConcurrentHashMap<>();

    public PokecenterDig(PlayerConsoles consoles, CartridgeService cartridges) {
        this.consoles = consoles;
        this.cartridges = cartridges;
    }

    /**
     * Why the player cannot leave where they are now ("in a battle"), or null when they can: free, already in a
     * Pokémon Center, or not playing Gold, Silver or Crystal (nothing to check). Their console must be open.
     */
    public String blocker(String userId) {
        return run(userId, false);
    }

    /** Sends the player digging to their Pokémon Center, when nothing blocks them. Returns the blocker, as {@link #blocker}. */
    public String dig(String userId) {
        return run(userId, true);
    }

    private String run(String userId, boolean dig) {
        String cartridge = consoles.game(userId);
        Game game = cartridge == null ? null : games.computeIfAbsent(cartridge, this::load).orElse(null);
        return game == null ? null : consoles.withBus(userId, bus -> run(game, bus, dig, userId));
    }

    /** On the console's thread: the blocker, and when there is none and dig is set, the dig queued. */
    static String run(Game game, AddressSpace bus, boolean dig, String userId) {
        String why = check(game, bus);
        Layout at = game.at;
        if (why == null && dig && !game.maps.pokecenter(u8(bus, at.mapGroup), u8(bus, at.mapGroup + 1))) {
            queue(userId, game, bus);
        }
        return why;
    }

    private static String check(Game game, AddressSpace bus) {
        Layout at = game.at;
        int svbk = bus.getByte(SVBK);
        if (svbk != 0xFF && (svbk & 7) > 1) return "busy"; // WRAM bank 1 is out: the game is mid-routine
        if (u8(bus, at.battleMode) != 0) return "in a battle";
        if (u8(bus, at.linkMode) != 0) return "in a link room";
        if (u8(bus, at.scriptRunning) != 0 || u8(bus, at.scriptRunning - 1) == SCRIPT_READ
                || u8(bus, at.windowStackSize) != 0 || u8(bus, at.reentryScript) != 0) {
            return "in a menu, a dialog or an event";
        }
        if (u8(bus, at.mapStatus) != MAPSTATUS_HANDLE || u8(bus, at.mapStatus + 1) != MAPEVENTS_ON) return "on the move";
        if ((u8(bus, at.statusFlags2) & BUG_CONTEST_TIMER) != 0) return "in the Bug-Catching Contest";
        int group = u8(bus, at.mapGroup), number = u8(bus, at.mapGroup + 1);
        if (game.maps.pokecenter(group, number)) return null;
        int env = game.maps.environment(group, number);
        if (env != TOWN && env != ROUTE && env != CAVE && env != DUNGEON) return "indoors, where neither Fly nor Dig works";
        return null;
    }

    /** Queues the dig, whatever the player is doing; false when they never entered a Pokémon Center. */
    static boolean queue(String userId, Game game, AddressSpace bus) {
        Layout at = game.at;
        int town = u8(bus, at.lastSpawn), townNumber = u8(bus, at.lastSpawn + 1);
        int[] door = game.maps.pokecenterDoor(town, townNumber);
        if (door == null) { // a new game that never entered a Center
            log.info("No Pokémon Center to dig {} to (last spawn {}:{})", userId, town, townNumber);
            return false;
        }
        int[] bytes = {door[1], door[2], door[3], door[0], town, townNumber}; // wNextWarp .. wPrevMapNumber
        for (int i = 0; i < bytes.length; i++) bus.setByte(at.nextWarp + i, bytes[i]);
        bus.setByte(at.reentryScript + 1, DIG_BANK);
        bus.setByte(at.reentryScript + 2, (game.script + 2) & 0xFF); // past waitbutton and closetext
        bus.setByte(at.reentryScript + 3, (game.script + 2) >> 8);
        bus.setByte(at.reentryScript, 1); // last: the flag arms it
        log.info("Digging {} to the Pokémon Center at {}:{}", userId, door[2], door[3]);
        return true;
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

    /** Gold, Silver or Crystal (English) with the Dig script where pret has it; empty for any other ROM. */
    static Optional<Game> game(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        PokemonGen2 maps = crystal == null ? null : PokemonGen2.forRom(rom);
        if (maps == null) return Optional.empty();
        Layout at = crystal ? CRYSTAL : GOLD_SILVER;
        for (int script : at.digScripts) {
            int o = DIG_BANK * 0x4000 + script - 0x4000;
            if ((rom[o] & 0xFF) == at.opcodes[0] && (rom[o + 1] & 0xFF) == at.opcodes[1] && (rom[o + 2] & 0xFF) == at.opcodes[2]) {
                return Optional.of(new Game(at, maps, script));
            }
        }
        return Optional.empty();
    }

    private static int u8(AddressSpace bus, int address) {
        return bus.getByte(address) & 0xFF;
    }
}
