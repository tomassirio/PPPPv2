package dev.pppp.service.game;

import dev.pppp.model.GameSummary;
import dev.pppp.model.Progress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pokémon Gold, Silver and Crystal (English releases): the scoreboard counters, achievements and Trainer Score,
 * with no party box. Its state is a {@link Gen2Save}.
 *
 * <p>Sources, all pret ("symbols" branch for the .sym files, master otherwise): WRAM addresses are the symbols of
 * pokegold.sym (pokesilver.sym agrees on all of WRAM) and pokecrystal11.sym (pokecrystal.sym agrees on all of
 * WRAM; the local Crystal ROM is Rev 1); struct layouts are macros/ram.asm (box_struct, party_struct); list sizes
 * ram/wram.asm with constants/item_data_constants.asm; event flag numbers constants/event_flags.asm. The local
 * ROMs hash to pret's roms.sha1 (pokegold, pokesilver, pokecrystal11), and every event flag used here was found
 * as the operand of its setevent/clearevent in those ROMs' scripts.
 *
 * <p>Not yet run against a real game. Everything is WRAM bank 1: EmulatorHost.readBytes gives null while the game
 * has another bank switched in, {@link #read} rejects readings that are not a well-formed save (boot, title
 * screen, mid-write), and ProgressTracker only counts a reading seen twice in a row.
 */
final class PokemonGen2 implements GameProfile<Gen2Save> {
    /**
     * Bus addresses (bank 1) and ROM offsets per release. Fixed neighbours: wPlayerName = wPlayerID + 2,
     * wKantoBadges = wJohtoBadges + 1, the bag after wTMsHMs (below), wPartySpecies = wPartyCount + 1,
     * wPartyMons = wPartyCount + 8, wPokedexSeen = wPokedexCaught + 32.
     */
    private record Layout(int playerId, int playHours, int coins, int badges, int tmsHms, int hallOfFame,
                          int eventFlags, int phoneList, int partyCount, int dexCaught, int unownDex, int romNames,
                          int romBaseData) {}

    private static final Layout GOLD_SILVER = new Layout(
            0xD1A1, // wPlayerID
            0xD1EB, // wGameTimeHours (big-endian, home/game_time.asm)
            0xD57A, // wCoins (big-endian, engine/events/money.asm)
            0xD57C, // wJohtoBadges
            0xD57E, // wTMsHMs
            0xD683, // wHallOfFameCount
            0xD7B7, // wEventFlags
            0xD9C6, // wPhoneList
            0xDA22, // wPartyCount
            0xDBE4, // wPokedexCaught
            0xDC24, // wUnownDex
            0x6C * 0x4000 + 0x0B74, // PokemonNames, 6c:4b74
            0x14 * 0x4000 + 0x1B0B), // BaseData, 14:5b0b
            CRYSTAL = new Layout(0xD47B, 0xD4C4, 0xD855, 0xD857, 0xD859, 0xD95E, 0xDA72, 0xDC7C, 0xDCD7, 0xDE99,
                    0xDED9, // same order as above
                    0x14 * 0x4000 + 0x3384, // PokemonNames, 14:7384
                    0x14 * 0x4000 + 0x1424); // BaseData, 14:5424

    private static final int DEX_SIZE = 251, DEX_BYTES = 32, NAME_LENGTH = 11, EGG = 0xFD, // pokemon_constants.asm
            MON_SIZE = 0x30, MON_ID = 0x06, MON_DVS = 0x15, MON_LEVEL = 0x1F, // party_struct
            BASE_DATA_SIZE = 0x20, BASE_TYPES = 0x07, // constants/pokemon_data_constants.asm
            NUM_TMS = 50, NUM_HMS = 7, // add_tm / add_hm in item_constants.asm
            MAX_ITEMS = 20, MAX_KEY_ITEMS = 25, MAX_BALLS = 12, MAX_PC_ITEMS = 50, // item_data_constants.asm
            CONTACT_LIST_SIZE = 10, NUM_UNOWN = 26, MAX_COINS = 9999; // phone_, pokemon_, misc_constants.asm
    /** Offsets from wTMsHMs of wNumItems, wNumKeyItems, wNumBalls, wNumPCItems (each count precedes its list). */
    private static final int ITEMS = NUM_TMS + NUM_HMS, KEY_ITEMS = ITEMS + 1 + MAX_ITEMS * 2 + 1,
            BALLS = KEY_ITEMS + 1 + MAX_KEY_ITEMS + 1, PC_ITEMS = BALLS + 1 + MAX_BALLS * 2 + 1;
    /**
     * constants/event_flags.asm, the same numbers in pokegold and pokecrystal. Red's flag hides him: set by the
     * new-game init script, cleared on entering the Hall of Fame (maps/HallOfFame.asm), set again by "disappear"
     * once he is beaten (maps/SilverCaveRoom3.asm; losing whites out before that).
     */
    private static final int EVENT_GOT_A_POKEMON_FROM_ELM = 26, EVENT_CLEARED_RADIO_TOWER = 33,
            EVENT_BEAT_ELITE_FOUR = 68, EVENT_GOT_MASTER_BALL_FROM_ELM = 124, EVENT_RED_IN_MT_SILVER = 1890;
    /** Bit order of wJohtoBadges, then wKantoBadges (pokecrystal constants/engine_flags.asm). */
    private static final String[] BADGE_NAMES = {"Zephyr", "Hive", "Plain", "Fog", "Mineral", "Storm", "Glacier", "Rising",
            "Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth"};

    private final Layout at;
    private final String[] names = new String[DEX_SIZE + 1]; // species id = Pokédex number in this generation
    private final int[][] types = new int[DEX_SIZE + 1][]; // {type1, type2}; -1s when the base data did not check out

    /** By header title and game code (0x134, 0x13F), which also tells the English release from the others. */
    static PokemonGen2 forRom(byte[] rom) {
        if (rom.length < 0x150) return null;
        String header = new String(rom, 0x134, 15, StandardCharsets.US_ASCII);
        Layout layout = header.equals("POKEMON_GLDAAUE") || header.equals("POKEMON_SLVAAXE") ? GOLD_SILVER
                : header.equals("PM_CRYSTAL\0BYTE") ? CRYSTAL : null;
        return layout == null || rom.length < layout.romNames + DEX_SIZE * 10 ? null : new PokemonGen2(rom, layout);
    }

    private PokemonGen2(byte[] rom, Layout at) {
        this.at = at;
        for (int i = 1; i <= DEX_SIZE; i++) {
            String name = PokemonRed.titleCase(PokemonRed.text(rom, at.romNames + (i - 1) * 10, 10));
            names[i] = name.isEmpty() ? "#" + i : name;
            int base = at.romBaseData + (i - 1) * BASE_DATA_SIZE; // each entry starts with its dex number
            boolean ok = base + BASE_DATA_SIZE <= rom.length && (rom[base] & 0xFF) == i;
            types[i] = ok ? new int[] {rom[base + BASE_TYPES] & 0xFF, rom[base + BASE_TYPES + 1] & 0xFF} : new int[] {-1, -1};
        }
    }

    @Override
    public int ramStart() {
        return at.playerId;
    }

    @Override
    public int ramLength() {
        return at.unownDex + NUM_UNOWN - at.playerId;
    }

    @Override
    public Gen2Save read(Ram ram) {
        // As in PokemonRed: a named player and well-formed lists rule out boot, the title screen and loading.
        int name = at.playerId + 2, first = ram.u8(name);
        if (first < 0x80 || first > 0xBF
                || PokemonRed.text(ram.slice(name, NAME_LENGTH), 0, NAME_LENGTH).length() == NAME_LENGTH) {
            return null;
        }
        int count = ram.u8(at.partyCount), species = at.partyCount + 1, mons = at.partyCount + 8;
        if (count > 6 || ram.u8(species + count) != 0xFF) return null;
        int playerId = ram.u16(at.playerId);
        List<Gen2Save.Mon> party = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int mon = mons + i * MON_SIZE, id = ram.u8(mon), level = ram.u8(mon + MON_LEVEL);
            if (ram.u8(species + i) == EGG) continue; // an egg is listed as EGG, its struct holds the species
            // A member mid-write: try again next second.
            if (id < 1 || id > DEX_SIZE || id != ram.u8(species + i) || level < 1 || level > 100) return null;
            party.add(new Gen2Save.Mon(id, level, shiny(ram.u8(mon + MON_DVS), ram.u8(mon + MON_DVS + 1)),
                    ram.u16(mon + MON_ID) == playerId, types[id][0], types[id][1]));
        }
        // The bag and PC lists end in -1 right after their last entry (engine/menus/intro_menu.asm .InitList).
        Set<Integer> items = new HashSet<>();
        int bag = at.tmsHms;
        if (!list(ram, bag + ITEMS, MAX_ITEMS, 2, items) || !list(ram, bag + KEY_ITEMS, MAX_KEY_ITEMS, 1, items)
                || !list(ram, bag + BALLS, MAX_BALLS, 2, items) || !list(ram, bag + PC_ITEMS, MAX_PC_ITEMS, 2, items)) {
            return null;
        }
        int coins = ram.u16(at.coins);
        if (coins > MAX_COINS) return null;
        int hms = 0;
        for (int i = 0; i < NUM_HMS; i++) if (ram.u8(bag + NUM_TMS + i) > 0) hms++;
        BitSet unown = new BitSet(); // forms (1-26) in the order registered, then 0s (engine/pokedex/unown_dex.asm)
        for (int i = 0; i < NUM_UNOWN && ram.u8(at.unownDex + i) != 0; i++) {
            int form = ram.u8(at.unownDex + i);
            if (form <= NUM_UNOWN) unown.set(form);
        }
        int phone = 0; // contact ids; a deleted number leaves a 0 (engine/phone/phone.asm DelCellNum)
        for (int i = 0; i < CONTACT_LIST_SIZE; i++) if (ram.u8(at.phoneList + i) != 0) phone++;
        BitSet caught = dex(ram, at.dexCaught), seen = dex(ram, at.dexCaught + DEX_BYTES);
        seen.or(caught);
        return new Gen2Save(playerId, ram.u8(at.badges), ram.u8(at.badges + 1), ram.u8(at.hallOfFame),
                event(ram, EVENT_BEAT_ELITE_FOUR) && event(ram, EVENT_RED_IN_MT_SILVER),
                event(ram, EVENT_GOT_A_POKEMON_FROM_ELM), event(ram, EVENT_CLEARED_RADIO_TOWER),
                event(ram, EVENT_GOT_MASTER_BALL_FROM_ELM), caught, seen, count, List.copyOf(party), Set.copyOf(items),
                hms, coins, unown.cardinality(), phone, ram.u16(at.playHours));
    }

    /**
     * Shiny by the DVs' two bytes (attack/defense, speed/special nybbles), as CheckShininess in engine/gfx/color.asm:
     * attack has bit 1 set (SHINY_ATK_MASK), defense, speed and special are 10.
     */
    static boolean shiny(int atkDef, int spdSpc) {
        return (atkDef & 0x20) != 0 && (atkDef & 0xF) == 10 && spdSpc == 0xAA;
    }

    /** Adds a count-prefixed item list's ids; false when it is not well-formed. */
    private static boolean list(Ram ram, int countAt, int max, int entrySize, Set<Integer> into) {
        int n = ram.u8(countAt);
        if (n > max || ram.u8(countAt + 1 + n * entrySize) != 0xFF) return false;
        for (int i = 0; i < n; i++) into.add(ram.u8(countAt + 1 + i * entrySize));
        return true;
    }

    /** wEventFlags is a bit array: flag n is bit n % 8 of byte n / 8 (home/flag.asm FlagAction). */
    private boolean event(Ram ram, int flag) {
        return (ram.u8(at.eventFlags + flag / 8) & 1 << flag % 8) != 0;
    }

    /** A Pokédex flag array as dex numbers: bit n = #n. */
    private static BitSet dex(Ram ram, int address) {
        BitSet raw = BitSet.valueOf(ram.slice(address, DEX_BYTES)), out = new BitSet(DEX_SIZE + 1);
        raw.stream().filter(i -> i < DEX_SIZE).forEach(i -> out.set(i + 1));
        return out;
    }

    @Override
    public Gen2Save save(Gen2Save state) {
        return state;
    }

    @Override
    public Progress progress(Gen2Save s) {
        Set<String> badges = new LinkedHashSet<>();
        int bits = s.johtoBadges() | s.kantoBadges() << 8;
        for (int i = 0; i < BADGE_NAMES.length; i++) if ((bits & 1 << i) != 0) badges.add(BADGE_NAMES[i]);
        int level = s.party().stream().mapToInt(Gen2Save.Mon::level).max().orElse(0);
        return new Progress(badges, names(s.caught()), names(s.seen()), level, s.hallOfFame() > 0);
    }

    private Set<String> names(BitSet dex) {
        Set<String> out = new LinkedHashSet<>();
        dex.stream().forEach(i -> out.add(names[i]));
        return out;
    }

    @Override
    public GameSummary summary(Gen2Save state) {
        Progress s = state == null ? Progress.EMPTY : progress(state);
        return new GameSummary("Pokémon Gold, Silver or Crystal", List.of(
                new GameSummary.Counter("Badges", s.badges().size(), BADGE_NAMES.length),
                new GameSummary.Counter("Pokédex", s.caught().size(), DEX_SIZE),
                new GameSummary.Counter("Seen", s.seen().size(), DEX_SIZE)), List.of(), List.of(),
                new GameSummary.Collection(0, List.of(), List.of(), List.of()), null);
    }
}
