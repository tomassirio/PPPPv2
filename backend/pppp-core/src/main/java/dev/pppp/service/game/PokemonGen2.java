package dev.pppp.service.game;

import dev.pppp.model.GameSummary;
import dev.pppp.model.PartyMon;
import dev.pppp.model.Progress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Pokémon Gold, Silver and Crystal (English releases): the scoreboard counters, badges, the Pokédex for the side
 * banners, achievements and Trainer Score, with no party box. Its state is a {@link Gen2Save}. Pokémon front pics
 * and the Johto badges are drawn from the ROM ({@link #icon}, {@link #badge}).
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
     * wPartyMons = wPartyCount + 8, wPartyMonNicknames = wPartyCount + $16A, wMapGroup = wPartyCount - $22
     * (wMapNumber right after), wGameTimeMinutes = wGameTimeHours + 2, wPokedexSeen = wPokedexCaught + 32.
     * ROM: ItemNames and MoveNames are lists of "@"-terminated names; Landmarks (data/maps/landmarks.asm) is
     * x, y and a pointer to the name, 4 bytes per landmark, names in the same bank; MapGroupPointers
     * (data/maps/maps.asm) points into its own bank at each group's map headers.
     */
    private record Layout(int playerId, int playHours, int coins, int badges, int tmsHms, int hallOfFame,
                          int eventFlags, int phoneList, int partyCount, int dexCaught, int unownDex, int romNames,
                          int romBaseData, int picPointers, int unownPicPointers, int monPalettes, int badgeGfx,
                          int predefPals, int itemNames, int moveNames, int landmarks, int numLandmarks,
                          int mapGroups, boolean crystal) {}

    /** ROM offset of bank:address (address in the switchable $4000-$7fff window). */
    private static int rom(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }

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
            rom(0x6C, 0x4B74), // PokemonNames
            rom(0x14, 0x5B0B), // BaseData
            rom(0x12, 0x4000), // PokemonPicPointers
            rom(0x1F, 0x4000), // UnownPicPointers
            rom(0x02, 0x6D3D), // PokemonPalettes
            rom(0x09, 0x622F), // BadgeGFX
            rom(0x02, 0x6265), // PredefPals
            rom(0x6C, 0x4000), // ItemNames
            rom(0x6C, 0x5574), // MoveNames
            rom(0x24, 0x6382), // Landmarks
            95, // NUM_LANDMARKS (constants/landmark_constants.asm: SPECIAL_MAP .. FAST_SHIP)
            rom(0x25, 0x40ED), // MapGroupPointers
            false),
            CRYSTAL = new Layout(0xD47B, 0xD4C4, 0xD855, 0xD857, 0xD859, 0xD95E, 0xDA72, 0xDC7C, 0xDCD7, 0xDE99,
                    0xDED9, // same order as above
                    rom(0x14, 0x7384), // PokemonNames
                    rom(0x14, 0x5424), // BaseData
                    rom(0x48, 0x4000), // PokemonPicPointers
                    rom(0x49, 0x4000), // UnownPicPointers
                    rom(0x02, 0x68CE), // PokemonPalettes
                    rom(0x09, 0x6043), // BadgeGFX
                    rom(0x02, 0x5DF6), // PredefPals
                    rom(0x72, 0x4000), // ItemNames
                    rom(0x72, 0x5F29), // MoveNames
                    rom(0x72, 0x68C3), // Landmarks
                    96, // NUM_LANDMARKS: Crystal adds BATTLE_TOWER after LIGHTHOUSE, so later ids differ from Gold's
                    rom(0x25, 0x4000), // MapGroupPointers
                    true);

    private static final int DEX_SIZE = 251, DEX_BYTES = 32, NAME_LENGTH = 11, EGG = 0xFD, // pokemon_constants.asm
            MON_SIZE = 0x30, MON_ITEM = 0x01, MON_MOVES = 0x02, MON_ID = 0x06, MON_DVS = 0x15, MON_LEVEL = 0x1F,
            MON_STATUS = 0x20, MON_HP = 0x22, MON_MAXHP = 0x24, NUM_MOVES = 4, // party_struct (big-endian HP)
            PARTY_NICKNAMES = 0x16A, MAP_GROUP = -0x22, // from wPartyCount, see Layout
            NUM_ITEMS = 0xFF, NUM_ATTACKS = 251, ITEM_NAME_LENGTH = 13, // item_constants, move_constants, text_constants
            NUM_MAP_GROUPS = 26, MAP_HEADER_SIZE = 9, MAP_LOCATION = 5, // map_constants.asm; the "map" macro
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
    /**
     * The summary's badges: bit numbers above in the order they are earned (Chuck's Storm Badge before Jasmine's
     * Mineral Badge, whose medicine comes from Cianwood), with their gyms (pret maps/*Gym*.asm; Blaine is on the
     * Seafoam Islands in Gen 2).
     */
    private static final int[] BADGE_ORDER = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final String[] GYMS = {"Violet Gym", "Azalea Gym", "Goldenrod Gym", "Ecruteak Gym", "Olivine Gym",
            "Cianwood Gym", "Mahogany Gym", "Blackthorn Gym", "Pewter Gym", "Cerulean Gym", "Vermilion Gym",
            "Celadon Gym", "Fuchsia Gym", "Saffron Gym", "Seafoam Gym", "Viridian Gym"}; // by bit, as BADGE_NAMES
    /**
     * Pictures. PokemonPicPointers / UnownPicPointers (data/pokemon/pic_pointers.asm, unown_pic_pointers.asm): 6
     * bytes per species / Unown letter, the front pic first as bank, address (dba_pic). BASE_PIC_SIZE: offset 17 of
     * the base data, $55/$66/$77 (constants/pokemon_data_constants.asm). PokemonPalettes (data/pokemon/
     * palettes.asm): 8 bytes per species from 0 (a dummy), the normal palette's two middle colours then the
     * shiny one's; the game adds white and black (LoadPalette_White_Col1_Col2_Black). UNOWN_A: Unown is drawn as
     * its letter (GetFrontpicPointer), A for the Pokédex banner.
     */
    private static final int PIC_POINTER_SIZE = 6, BASE_PIC_SIZE = 17, PALETTE_SIZE = 8, UNOWN = 201, UNOWN_A = 1;
    /**
     * Badges: BadgeGFX (engine/menus/trainer_card.asm, gfx/trainer_card/badges.png) is plain 2bpp, 4 tiles per
     * Johto badge in wJohtoBadges bit order (top-left, top-right, bottom-left, bottom-right:
     * TrainerCard_JohtoBadgesOAM, .facing1), drawn as objects with OBJ palette 0. _CGB_TrainerCard
     * (engine/gfx/cgb_layouts.asm) loads 8 leader palettes into wBGPals1 and then PREDEFPAL_CGB_BADGE (36th, from 0,
     * in constants/scgb_constants.asm) right after, into wOBPals1 palette 0. PredefPals: 8 bytes per palette
     * (GetPredefPal). The trainer card has no Kanto badges: the ROM holds no art for them.
     */
    private static final int BADGE_BYTES = 4 * 16, PREDEFPAL_CGB_BADGE = 36;

    private final Layout at;
    private final byte[] rom;
    private final Map<Integer, byte[]> icons = new ConcurrentHashMap<>(), badgePngs = new ConcurrentHashMap<>();
    private final String[] names = new String[DEX_SIZE + 1]; // species id = Pokédex number in this generation
    private final int[][] types = new int[DEX_SIZE + 1][]; // {type1, type2}; -1s when the base data did not check out
    private final String[] itemNames, moveNames, landmarkNames; // by id; [0] (none, SPECIAL_MAP) is null

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
        this.rom = rom;
        for (int i = 1; i <= DEX_SIZE; i++) {
            String name = PokemonRed.titleCase(PokemonRed.text(rom, at.romNames + (i - 1) * 10, 10));
            names[i] = name.isEmpty() ? "#" + i : name;
            int base = at.romBaseData + (i - 1) * BASE_DATA_SIZE; // each entry starts with its dex number
            boolean ok = base + BASE_DATA_SIZE <= rom.length && (rom[base] & 0xFF) == i;
            types[i] = ok ? new int[] {rom[base + BASE_TYPES] & 0xFF, rom[base + BASE_TYPES + 1] & 0xFF} : new int[] {-1, -1};
        }
        itemNames = nameList(rom, at.itemNames, NUM_ITEMS);
        moveNames = nameList(rom, at.moveNames, NUM_ATTACKS);
        landmarkNames = new String[at.numLandmarks];
        for (int i = 1; i < at.numLandmarks && at.landmarks + i * 4 + 4 <= rom.length; i++) {
            int name = rom(at.landmarks / 0x4000, u16le(rom, at.landmarks + i * 4 + 2));
            if (name + 20 <= rom.length) {
                landmarkNames[i] = PokemonRed.titleCase(PokemonRed.text(rom, name, 20)).replace(" Of ", " of ");
            }
        }
    }

    /** A list of "@"-terminated names (pret list_start / li) as title case, by id from 1. */
    private static String[] nameList(byte[] rom, int at, int count) {
        String[] out = new String[count + 1];
        for (int id = 1; id <= count && at + ITEM_NAME_LENGTH <= rom.length; id++) {
            int end = at;
            while (end - at < ITEM_NAME_LENGTH && rom[end] != 0x50) end++;
            out[id] = PokemonRed.titleCase(PokemonRed.text(rom, at, end - at));
            at = end + 1;
        }
        return out;
    }

    private static int u16le(byte[] rom, int at) {
        return (rom[at] & 0xFF) | (rom[at + 1] & 0xFF) << 8;
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
        List<PartyMon> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int mon = mons + i * MON_SIZE, id = ram.u8(mon), level = ram.u8(mon + MON_LEVEL);
            int hp = ram.u16(mon + MON_HP), maxHp = ram.u16(mon + MON_MAXHP);
            boolean egg = ram.u8(species + i) == EGG; // an egg is listed as EGG, its struct holds the species
            // A member mid-write: try again next second.
            if (id < 1 || id > DEX_SIZE || hp > maxHp) return null;
            if (!egg && (id != ram.u8(species + i) || level < 1 || level > 100)) return null;
            boolean shiny = shiny(ram.u8(mon + MON_DVS), ram.u8(mon + MON_DVS + 1));
            if (!egg) {
                party.add(new Gen2Save.Mon(id, level, shiny, ram.u16(mon + MON_ID) == playerId, types[id][0],
                        types[id][1]));
            }
            members.add(member(ram, mon, at.partyCount + PARTY_NICKNAMES + i * NAME_LENGTH, egg, shiny));
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
                hms, coins, unown.cardinality(), phone, ram.u16(at.playHours), ram.u8(at.playHours + 2),
                location(ram), List.copyOf(members));
    }

    /** A party member for the profile: the struct at mon, its nickname at nick. */
    private PartyMon member(Ram ram, int mon, int nick, boolean egg, boolean shiny) {
        int id = ram.u8(mon), hp = ram.u16(mon + MON_HP), item = ram.u8(mon + MON_ITEM);
        String nickname = PokemonRed.text(ram.slice(nick, NAME_LENGTH), 0, NAME_LENGTH);
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < NUM_MOVES; i++) {
            int move = ram.u8(mon + MON_MOVES + i);
            if (move > 0 && move <= NUM_ATTACKS) moves.add(moveNames[move]);
        }
        List<String> typeNames = egg ? List.of() : IntStream.of(types[id]).distinct().mapToObj(PokemonGen2::typeName)
                .filter(java.util.Objects::nonNull).toList();
        return new PartyMon(id, egg ? "Egg" : names[id],
                egg || nickname.isEmpty() || nickname.equalsIgnoreCase(names[id]) ? null : nickname,
                ram.u8(mon + MON_LEVEL), hp, ram.u16(mon + MON_MAXHP), egg ? null : status(ram.u8(mon + MON_STATUS), hp),
                item == 0 ? null : itemNames[item], List.copyOf(moves), shiny, egg, typeNames);
    }

    /** The status byte (constants/battle_constants.asm: SLP counter in bits 0-2, PSN 3, BRN 4, FRZ 5, PAR 6). */
    static String status(int s, int hp) {
        if (hp == 0) return "FNT";
        if ((s & 0x07) != 0) return "SLP";
        if ((s & 0x08) != 0) return "PSN";
        if ((s & 0x10) != 0) return "BRN";
        if ((s & 0x20) != 0) return "FRZ";
        if ((s & 0x40) != 0) return "PAR";
        return null;
    }

    /** pret constants/type_constants.asm, lower case; null for the unused ids. */
    static String typeName(int type) {
        return switch (type) {
            case 0x00 -> "normal";
            case 0x01 -> "fighting";
            case 0x02 -> "flying";
            case 0x03 -> "poison";
            case 0x04 -> "ground";
            case 0x05 -> "rock";
            case 0x07 -> "bug";
            case 0x08 -> "ghost";
            case 0x09 -> "steel";
            case 0x14 -> "fire";
            case 0x15 -> "water";
            case 0x16 -> "grass";
            case 0x17 -> "electric";
            case 0x18 -> "psychic";
            case 0x19 -> "ice";
            case 0x1A -> "dragon";
            case 0x1B -> "dark";
            default -> null;
        };
    }

    /**
     * The landmark of the map the player is on, as GetWorldMapLocation reads it: the map header's location byte
     * (MapGroupPointers[wMapGroup - 1] + (wMapNumber - 1) * 9 + 5). Null for SPECIAL_MAP (0: Pokémon Center 2F,
     * the link rooms) or a group out of range (between maps).
     */
    private String location(Ram ram) {
        int group = ram.u8(at.partyCount + MAP_GROUP), number = ram.u8(at.partyCount + MAP_GROUP + 1);
        if (group < 1 || group > NUM_MAP_GROUPS || number < 1) return null;
        int header = rom(at.mapGroups / 0x4000, u16le(rom, at.mapGroups + (group - 1) * 2))
                + (number - 1) * MAP_HEADER_SIZE + MAP_LOCATION;
        if (header < 0 || header >= rom.length) return null;
        int landmark = rom[header] & 0xFF;
        return landmark < landmarkNames.length ? landmarkNames[landmark] : null;
    }

    /** wGameTimeHours:wGameTimeMinutes as "H:MM"; null when the minutes are not a clock's (mid-write). */
    static String playTime(Gen2Save s) {
        return s.playMinutes() >= 60 ? null : String.format("%d:%02d", s.playHours(), s.playMinutes());
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
        int bits = state == null ? 0 : state.johtoBadges() | state.kantoBadges() << 8;
        List<GameSummary.Badge> badges = new ArrayList<>();
        for (int bit : BADGE_ORDER) badges.add(new GameSummary.Badge(BADGE_NAMES[bit], GYMS[bit], (bits & 1 << bit) != 0));
        return new GameSummary("Pokémon Gold, Silver or Crystal", List.of(
                new GameSummary.Counter("Badges", s.badges().size(), BADGE_NAMES.length),
                new GameSummary.Counter("Pokédex", s.caught().size(), DEX_SIZE),
                new GameSummary.Counter("Seen", s.seen().size(), DEX_SIZE)), List.of(), badges,
                collection(state), state == null ? null : playTime(state));
    }

    /** The Pokédex for the side banners; names only of what has been seen (seen includes caught). */
    private GameSummary.Collection collection(Gen2Save state) {
        BitSet seen = state == null ? new BitSet() : state.seen(), caught = state == null ? new BitSet() : state.caught();
        List<String> shown = new ArrayList<>(DEX_SIZE);
        for (int i = 1; i <= DEX_SIZE; i++) shown.add(seen.get(i) ? names[i] : null);
        return new GameSummary.Collection(DEX_SIZE, seen.stream().boxed().toList(), caught.stream().boxed().toList(),
                shown);
    }

    /** The front pic of Pokédex number id (Unown as A) in its normal colours, 56x56, from the ROM; null if unreadable. */
    @Override
    public byte[] icon(int id) {
        if (id < 1 || id > DEX_SIZE) return null;
        return cached(icons, id, () -> {
            int table = id == UNOWN ? at.unownPicPointers + (UNOWN_A - 1) * PIC_POINTER_SIZE
                    : at.picPointers + (id - 1) * PIC_POINTER_SIZE;
            int pic = rom(picBank(rom[table] & 0xFF), (rom[table + 1] & 0xFF) | (rom[table + 2] & 0xFF) << 8);
            int base = at.romBaseData + (id - 1) * BASE_DATA_SIZE;
            if ((rom[base] & 0xFF) != id) return null; // not the base data we expect
            int pal = at.monPalettes + id * PALETTE_SIZE;
            int[] argb = {0xFFFFFFFF, Gen2Pic.rgb(rom, pal), Gen2Pic.rgb(rom, pal + 2), 0xFF000000};
            return Gen2Pic.png(Gen2Pic.frontpic(Gen2Pic.decompress(rom, pic), rom[base + BASE_PIC_SIZE] & 0xF), argb);
        });
    }

    /**
     * FixPicBank (engine/gfx/load_pics.asm) turns the pointer table's bank byte into the real bank. Gold/Silver:
     * $13 -> "Pics 12" ($1f), $14 -> "Pics 13" ($20), $1f -> "Pics 14" ($2e), others as they are. Crystal:
     * PicsBanks[a - (BANK("Pics 1") - PICS_FIX)], and Pics 1-24 are banks $48-$5f in a row, so a + PICS_FIX ($36).
     * Banks from pret's pokegold.map, pokesilver.map, pokecrystal11.map.
     */
    private int picBank(int stored) {
        if (at.crystal) return stored + 0x36;
        return switch (stored) {
            case 0x13 -> 0x1F;
            case 0x14 -> 0x20;
            case 0x1F -> 0x2E;
            default -> stored;
        };
    }

    /** A Johto badge (n = its place in the summary's badges, 0-7) from the trainer card's art; null for Kanto. */
    @Override
    public byte[] badge(int n) {
        if (n < 0 || n >= BADGE_ORDER.length || BADGE_ORDER[n] >= 8) return null;
        return cached(badgePngs, n, () -> {
            int pal = at.predefPals + PREDEFPAL_CGB_BADGE * PALETTE_SIZE;
            int[] argb = {0, Gen2Pic.rgb(rom, pal + 2), Gen2Pic.rgb(rom, pal + 4), Gen2Pic.rgb(rom, pal + 6)};
            return GenOnePic.sheet(rom, at.badgeGfx + BADGE_ORDER[n] * BADGE_BYTES, 1, argb);
        });
    }

    /** Draws once per cartridge; a ROM that does not decode (a bad dump, a hack) gives null rather than a 500. */
    private static byte[] cached(Map<Integer, byte[]> cache, int key, java.util.function.Supplier<byte[]> draw) {
        byte[] png = cache.get(key);
        if (png != null) return png;
        try {
            png = draw.get();
        } catch (RuntimeException e) {
            return null;
        }
        if (png != null) cache.put(key, png);
        return png;
    }
}
