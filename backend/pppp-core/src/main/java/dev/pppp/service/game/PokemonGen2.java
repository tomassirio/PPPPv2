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
                          int eventFlags, int curBox, int phoneList, int partyCount, int dexCaught, int unownDex,
                          int sBox, int sLinkBattleWins, int sBattleTowerChallengeState, int romNames,
                          int romBaseData, int picPointers, int unownPicPointers, int monPalettes, int badgeGfx,
                          int predefPals, int itemNames, int moveNames, int landmarks, int numLandmarks,
                          int mapGroups, int dexPointers, int dexBanks, int kantoLandmark, int playerDirection,
                          int digWarp, boolean crystal) {}

    /** Offset in the battery save of SRAM bank:address (address in the $a000-$bfff window, 8 KB per bank). */
    private static int sram(int bank, int address) {
        return bank * 0x2000 + address - 0xA000;
    }

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
            0xD8BC, // wCurBox
            0xD9C6, // wPhoneList
            0xDA22, // wPartyCount
            0xDBE4, // wPokedexCaught
            0xDC24, // wUnownDex
            sram(1, 0xAD6C), // sBox (pokegold.sym, as everything in SRAM)
            sram(1, 0xB1BA), // sLinkBattleWins
            -1, // no Battle Tower
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
            rom(0x11, 0x4360), // PokedexDataPointerTable
            -1, // entries in banks $68 + (n - 1) / 64 (GetDexEntryPointer)
            0x2E, // KANTO_LANDMARK (constants/landmark_constants.asm): Pallet Town
            0xD205, // wPlayerDirection
            0xD9F2, // wDigWarpNumber (wDigMapGroup, wDigMapNumber next)
            false),
            CRYSTAL = new Layout(0xD47B, 0xD4C4, 0xD855, 0xD857, 0xD859, 0xD95E, 0xDA72, 0xDB72, 0xDC7C, 0xDCD7,
                    0xDE99, 0xDED9, // same order as above
                    sram(1, 0xAD10), // sBox (pokecrystal11.sym)
                    sram(1, 0xB260), // sLinkBattleWins
                    sram(1, 0xBE45), // sBattleTowerChallengeState
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
                    rom(0x11, 0x4378), // PokedexDataPointerTable
                    rom(0x11, 0x4351), // GetDexEntryPointer.PokedexEntryBanks: a bank per 64 numbers
                    0x2F, // KANTO_LANDMARK: one later than Gold's, after Crystal's Battle Tower
                    0xD4DE, // wPlayerDirection
                    0xDCA9, // wDigWarpNumber
                    true);

    private static final int DEX_SIZE = 251, DEX_BYTES = 32, NAME_LENGTH = 11, EGG = 0xFD, // pokemon_constants.asm
            MON_SIZE = 0x30, MON_ITEM = 0x01, MON_MOVES = 0x02, MON_ID = 0x06, MON_DVS = 0x15, MON_LEVEL = 0x1F,
            MON_STATUS = 0x20, MON_HP = 0x22, MON_MAXHP = 0x24, NUM_MOVES = 4, // party_struct (big-endian HP)
            MON_HAPPINESS = 0x1B, MON_POKERUS = 0x1C,
            MONEY = -7, MOMS_MONEY = -4, MAX_MONEY = 999_999, // wMoney, wMomsMoney from wCoins (3 bytes each)
            PARTY_NICKNAMES = 0x16A, MAP_GROUP = -0x22, // from wPartyCount, see Layout
            NUM_ITEMS = 0xFF, NUM_ATTACKS = 251, ITEM_NAME_LENGTH = 13, // item_constants, move_constants, text_constants
            MAP_HEADER_SIZE = 9, MAP_TILESET = 1, MAP_ENVIRONMENT = 2, MAP_LOCATION = 5, // the "map" macro
            TOWN = 1, ROUTE = 2, // constants/map_data_constants.asm: the outdoor environments
            DIZZY_PUNCH = 0x92, EXTREMESPEED = 0xF5, // constants/move_constants.asm
            BASE_DATA_SIZE = 0x20, BASE_TYPES = 0x07, // constants/pokemon_data_constants.asm
            NUM_TMS = 50, NUM_HMS = 7, // add_tm / add_hm in item_constants.asm
            SAVE_CHECK_VALUE_1 = 99, NUM_BOXES = 14, MONS_PER_BOX = 20, BOX_MON_SIZE = 0x20, BOX_SIZE = 0x450,
            MAX_LINK_RECORD = 9999, // misc_constants.asm, pokemon_constants.asm, the box macro (+ 2 padding)
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
    /** Each move's type name by move id (null where the ROM's Moves table is not where pret has it). */
    private final String[] moveTypes = new String[NUM_ATTACKS + 1];
    /**
     * Moves (data/moves/moves.asm, bank $10): 7 bytes per move from move 1, animation (= the move id), effect,
     * power, type, accuracy, PP, effect chance. pokecrystal11.sym 10:5afb, pokegold/pokesilver.sym 10:5afe.
     */
    private static final int MOVE_SIZE = 7, MOVE_TYPE = 3;
    /** By wMapGroup, wMapNumber (from 1): the pret map constant, and where the player is ({@link #place}). */
    private final String[][] mapIds, places;
    /** By wMapGroup, wMapNumber: the map's spot on the Pokégear town map, or null (no landmark there). */
    private final dev.pppp.model.MapPin[][] pins;
    /**
     * By wMapGroup, wMapNumber: a town or route (the world map); the map header's environment and tileset; and the
     * map's warps as {x, y, destination warp, group, number}, by warp number - 1.
     */
    private final boolean[][] outdoor;
    private final int[][] environment, tileset;
    private final int[][][][] warps;

    /** By header title and game code (0x134, 0x13F), which also tells the English release from the others. */
    static PokemonGen2 forRom(byte[] rom) {
        Layout layout = layout(rom);
        return layout == null || rom.length < layout.romNames + DEX_SIZE * 10 ? null : new PokemonGen2(rom, layout);
    }

    /** True for Crystal, false for Gold or Silver, null for any other ROM (the check {@link #forRom} makes). */
    static Boolean crystal(byte[] rom) {
        Layout layout = layout(rom);
        return layout == null ? null : layout.crystal;
    }

    private static Layout layout(byte[] rom) {
        if (rom.length < 0x150) return null;
        String header = new String(rom, 0x134, 15, StandardCharsets.US_ASCII);
        return header.equals("POKEMON_GLDAAUE") || header.equals("POKEMON_SLVAAXE") ? GOLD_SILVER
                : header.equals("PM_CRYSTAL\0BYTE") ? CRYSTAL : null;
    }

    /** "Gold", "Silver" or "Crystal", by the header title. */
    String version() {
        return at.crystal ? "Crystal" : rom[0x13C] == 'G' ? "Gold" : "Silver"; // POKEMON_GLD / POKEMON_SLV
    }

    /**
     * SRAM (pret ram/sram.asm; offsets from the .sym files in {@link Layout}): sCheckValue1 is SAVE_CHECK_VALUE_1
     * once the game was saved (engine/menus/save.asm). Boxes (the "box" macro, macros/ram.asm): a count, MONS_PER_BOX
     * + 1 species ending in -1, then 20 box_structs of $20 bytes. sBox is the box wCurBox names; its own copy among
     * sBox1-14 (banks 2 and 3, 7 each, $450 apart) is stale until the player switches boxes, so it is skipped. Gen 2
     * keeps no box in WRAM. A box that is not well-formed (never initialised, mid-write) counts as empty.
     */
    static Gen2Save.Sram sram(boolean crystal, byte[] sav, int curBox) {
        Layout at = crystal ? CRYSTAL : GOLD_SILVER;
        if (sav == null || sav.length < 0x8000 || (sav[sram(1, 0xA008)] & 0xFF) != SAVE_CHECK_VALUE_1) return null;
        BitSet boxed = new BitSet();
        box(sav, at.sBox, boxed);
        for (int n = 0; n < NUM_BOXES; n++) {
            if (n != curBox) box(sav, sram(2 + n / 7, 0xA000 + n % 7 * BOX_SIZE), boxed);
        }
        int wins = Math.min(MAX_LINK_RECORD, u16be(sav, at.sLinkBattleWins));
        int losses = Math.min(MAX_LINK_RECORD, u16be(sav, at.sLinkBattleWins + 2));
        return new Gen2Save.Sram(boxed, wins, losses, at.crystal ? sav[at.sBattleTowerChallengeState] & 0xFF : 0);
    }

    private static void box(byte[] sav, int box, BitSet into) {
        int count = sav[box] & 0xFF;
        if (count > MONS_PER_BOX || (sav[box + 1 + count] & 0xFF) != 0xFF) return;
        for (int i = 0; i < count; i++) {
            int species = sav[box + 1 + i] & 0xFF, struct = sav[box + 1 + MONS_PER_BOX + 1 + i * BOX_MON_SIZE] & 0xFF;
            if (species != EGG && species == struct && species >= 1 && species <= DEX_SIZE) into.set(species);
        }
    }

    private static int u16be(byte[] b, int at) {
        return (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
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
        int movesTable = at.crystal ? rom(0x10, 0x5AFB) : rom(0x10, 0x5AFE);
        for (int m = 1; m <= NUM_ATTACKS; m++) {
            int entry = movesTable + (m - 1) * MOVE_SIZE;
            if (entry + MOVE_SIZE <= rom.length && (rom[entry] & 0xFF) == m) moveTypes[m] = typeName(rom[entry + MOVE_TYPE] & 0xFF);
        }
        landmarkNames = new String[at.numLandmarks];
        for (int i = 1; i < at.numLandmarks && at.landmarks + i * 4 + 4 <= rom.length; i++) {
            int name = rom(at.landmarks / 0x4000, u16le(rom, at.landmarks + i * 4 + 2));
            if (name + 20 <= rom.length) {
                landmarkNames[i] = PokemonRed.titleCase(PokemonRed.text(rom, name, 20)).replace(" Of ", " of ")
                        .replaceAll("Mt\\.(?=\\S)", "Mt. ");
            }
        }
        mapIds = mapConstants(at.crystal);
        places = new String[mapIds.length][];
        pins = new dev.pppp.model.MapPin[mapIds.length][];
        outdoor = new boolean[mapIds.length][];
        environment = new int[mapIds.length][];
        tileset = new int[mapIds.length][];
        warps = new int[mapIds.length][][][];
        for (int g = 1; g < mapIds.length; g++) {
            places[g] = new String[mapIds[g].length];
            pins[g] = new dev.pppp.model.MapPin[mapIds[g].length];
            outdoor[g] = new boolean[mapIds[g].length];
            environment[g] = new int[mapIds[g].length];
            tileset[g] = new int[mapIds[g].length];
            warps[g] = new int[mapIds[g].length][][];
            int headers = rom(at.mapGroups / 0x4000, u16le(rom, at.mapGroups + (g - 1) * 2));
            for (int n = 1; n < mapIds[g].length; n++) {
                int header = headers + (n - 1) * MAP_HEADER_SIZE;
                if (header < 0 || header + MAP_HEADER_SIZE > rom.length) continue;
                int landmark = rom[header + MAP_LOCATION] & 0xFF, env = rom[header + MAP_ENVIRONMENT] & 0xFF;
                outdoor[g][n] = env == TOWN || env == ROUTE;
                environment[g][n] = env;
                tileset[g][n] = rom[header + MAP_TILESET] & 0xFF;
                warps[g][n] = warps(header);
                if (landmark >= landmarkNames.length) continue; // not a landmark: unknown
                places[g][n] = place(mapIds[g][n], env == TOWN || env == ROUTE, landmarkNames[landmark]);
                pins[g][n] = pin(landmark);
            }
        }
    }

    /** The map header's environment (TOWN 1 to DUNGEON 7, constants/map_data_constants.asm); 0 for an unknown map. */
    int environment(int group, int number) {
        return known(group, number) ? environment[group][number] : 0;
    }

    /**
     * Whether the map is a Pokémon Center, told as EnterMapWarp.SetSpawn tells one: by its tileset,
     * TILESET_POKECENTER (Gold 6, Crystal 7) or Crystal's TILESET_POKECOM_CENTER (15).
     */
    boolean pokecenter(int group, int number) {
        if (!known(group, number)) return false;
        int t = tileset[group][number];
        return at.crystal ? t == 7 || t == 15 : t == 6;
    }

    /**
     * The door from the map into its Pokémon Center as {warp number here, warp number inside, center group, center
     * number}, warp numbers from 1 as the game counts them; null when the map has none.
     */
    int[] pokecenterDoor(int group, int number) {
        if (!known(group, number)) return null;
        int[][] w = warps[group][number];
        for (int i = 0; i < w.length; i++) {
            if (pokecenter(w[i][3], w[i][4])) return new int[] {i + 1, w[i][2], w[i][3], w[i][4]};
        }
        return null;
    }

    /** The warp number (from 1) in the map that leads to the destination map, or 0 when none does. */
    int warpTo(int group, int number, int destGroup, int destNumber) {
        if (!known(group, number)) return 0;
        int[][] w = warps[group][number];
        for (int i = 0; i < w.length; i++) if (w[i][3] == destGroup && w[i][4] == destNumber) return i + 1;
        return 0;
    }

    /** The pret map constant at wMapGroup, wMapNumber (as "POKECENTER_2F"), or null. */
    String mapId(int group, int number) {
        return known(group, number) ? mapIds[group][number] : null;
    }

    /** {group, number} of a pret map constant, or null. */
    int[] map(String constant) {
        for (int g = 1; g < mapIds.length; g++) {
            for (int n = 1; n < mapIds[g].length; n++) if (constant.equals(mapIds[g][n])) return new int[] {g, n};
        }
        return null;
    }

    private boolean known(int group, int number) {
        return group >= 1 && group < mapIds.length && number >= 1 && number < mapIds[group].length && warps[group][number] != null;
    }

    /**
     * A map's warps (doors, stairs, cave mouths) as {x, y, destination warp, group, number}. The header points at the map's attributes (bank at 0, address
     * at 3); there the events pointer (address at 9, in the scripts' bank at 6) leads to two filler bytes, the warp
     * count, then 5 bytes per warp_event: y, x, destination warp, map group, map number (macros/scripts/maps.asm).
     */
    private int[][] warps(int header) {
        int attr = rom(rom[header] & 0xFF, u16le(rom, header + 3));
        if (attr < 0 || attr + 11 > rom.length) return new int[0][];
        int events = rom(rom[attr + 6] & 0xFF, u16le(rom, attr + 9));
        if (events < 0 || events + 3 > rom.length) return new int[0][];
        int count = rom[events + 2] & 0xFF;
        if (events + 3 + count * 5 > rom.length) return new int[0][];
        int[][] out = new int[count][];
        for (int i = 0; i < count; i++) {
            int w = events + 3 + i * 5;
            out[i] = new int[] {rom[w + 1] & 0xFF, rom[w] & 0xFF, rom[w + 2] & 0xFF, rom[w + 3] & 0xFF, rom[w + 4] & 0xFF};
        }
        return out;
    }

    /**
     * Where the player stands on the world map. Indoors (a house, a cave, a gate) that is the door they came in by:
     * stepping from a town or route into one, the game keeps the warp used outside for Dig and the Escape Rope
     * (engine/overworld/warp_connection.asm, SaveDigWarp: wDigWarpNumber, from 1, wDigMapGroup, wDigMapNumber).
     * Null when that is unknown (a new game indoors, before ever going out).
     */
    dev.pppp.model.MapSpot spot(Ram ram, int group, int number) {
        int x = ram.u8(at.partyCount + MAP_GROUP + 3), y = ram.u8(at.partyCount + MAP_GROUP + 2);
        String facing = FACING[ram.u8(at.playerDirection) >> 2 & 3];
        if (!outdoor[group][number]) {
            int warp = ram.u8(at.digWarp), g = ram.u8(at.digWarp + 1), n = ram.u8(at.digWarp + 2);
            if (g < 1 || g >= mapIds.length || n < 1 || n >= mapIds[g].length || !outdoor[g][n]) return null;
            if (warp < 1 || warp > warps[g][n].length) return null;
            group = g;
            number = n;
            x = warps[g][n][warp - 1][0];
            y = warps[g][n][warp - 1][1];
            facing = "down"; // just out of the door
        }
        return new dev.pppp.model.MapSpot(mapIds[group][number], x, y, facing);
    }

    /**
     * A landmark's spot on the Pokégear town map: Landmarks holds x, y as the cursor sprite's position, so less the
     * hardware's 8, 16 offset for screen pixels. Johto runs from New Bark Town to KANTO_LANDMARK, Kanto from there to
     * the last but one; the last (the Fast Ship) and 0 (special maps) are on neither map.
     */
    private dev.pppp.model.MapPin pin(int landmark) {
        if (landmark < 1 || landmark >= at.numLandmarks - 1) return null;
        int x = (rom[at.landmarks + landmark * 4] & 0xFF) - 8, y = (rom[at.landmarks + landmark * 4 + 1] & 0xFF) - 16;
        if (x < 0 || x >= 160 || y < 0 || y >= 144) return null;
        return new dev.pppp.model.MapPin(landmark < at.kantoLandmark ? "johto" : "kanto", x, y);
    }

    /**
     * pret's map constants per group (constants/map_constants.asm of pokegold or pokecrystal: Crystal adds maps
     * inside groups, so numbers differ), from the resource files; [group][number], both from 1.
     */
    static String[][] mapConstants(boolean crystal) {
        String file = crystal ? "pokecrystal-maps.txt" : "pokegold-maps.txt";
        try (var in = PokemonGen2.class.getResourceAsStream(file)) {
            List<String[]> groups = new ArrayList<>();
            groups.add(new String[0]);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                groups.add(("- " + line.trim()).split(" ")); // "-": number 0
            }
            return groups.toArray(String[][]::new);
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("Missing " + file, e);
        }
    }

    /** The landmarks of buildings that stand in a town, and that town (pret data/maps/landmarks.asm). */
    private static final Map<String, String> TOWN_OF = Map.of("Sprout Tower", "Violet City", "Radio Tower",
            "Goldenrod City", "Tin Tower", "Ecruteak City", "Burned Tower", "Ecruteak City", "Lighthouse",
            "Olivine City", "Lav Radio Tower", "Lavender Town");
    /** Rooms whose map constant does not read well word by word. */
    private static final Map<String, String> ROOM_NAMES = Map.ofEntries(
            Map.entry("PLAYERS_HOUSE_1F", "Your House 1F"), Map.entry("PLAYERS_HOUSE_2F", "Your House 2F"),
            Map.entry("PLAYERS_NEIGHBORS_HOUSE", "Neighbor's House"),
            Map.entry("BILLS_FAMILYS_HOUSE", "Bill's Family's House"),
            Map.entry("BILLS_OLDER_SISTERS_HOUSE", "Bill's Older Sister's House"),
            Map.entry("EARLS_POKEMON_ACADEMY", "Earl's Pokémon Academy"),
            Map.entry("DAY_OF_WEEK_SIBLINGS_HOUSE", "Siblings' House"),
            Map.entry("FAST_SHIP_CABINS_NNW_NNE_NE", "Cabins"), Map.entry("FAST_SHIP_CABINS_SW_SSW_NW", "Cabins"),
            Map.entry("FAST_SHIP_CABINS_SE_SSE_CAPTAINS_CABIN", "Cabins"),
            Map.entry("POKECOM_CENTER_ADMIN_OFFICE_MOBILE", "Pokécom Center"),
            Map.entry("DRAGON_SHRINE", "Dragon Shrine"), Map.entry("ROUTE_40_BATTLE_TOWER_GATE", "Route 40 Gate"),
            Map.entry("UNDERGROUND_PATH", "")); // "" : the landmark alone
    private static final Map<String, String> WORDS = Map.of("POKECENTER", "Pokémon Center", "POKEMON", "Pokémon",
            "POKE", "Poké", "MART", "Poké Mart", "MR", "Mr.", "DEPT", "Dept.", "CO", "Co.", "OF", "of", "HO OH", "Ho-Oh");
    private static final Set<String> COMPASS = Set.of("NW", "NE", "SW", "SE");

    /**
     * Where a map is, as the scoreboard shows it: the landmark outdoors ("Route 42"); "Landmark · Room" inside
     * ("Violet City · Pokémon Center", "Goldenrod City · Radio Tower 3F": a building's own landmark gives way to
     * its town); the room alone in the link rooms and the Pokémon Center 2F, whose landmark is SPECIAL (null).
     */
    static String place(String map, boolean outdoors, String landmark) {
        if (landmark != null && outdoors) return landmark;
        String town = landmark == null ? null : TOWN_OF.getOrDefault(landmark, landmark);
        String room = room(map, town);
        return town == null ? room : room.isEmpty() ? town : town + " · " + room;
    }

    /**
     * A map constant as a room name, without the words it shares with the start of town ("OLIVINE_TIMS_HOUSE" in
     * Olivine City: "Tim's House"). Floors stay upper case, "*S_HOUSE" (LAB, ROOM, HOME) is possessive, a
     * Pokémon Center's 1F is dropped, every *_SPEECH_HOUSE is a "House", unused _BETA maps lose the suffix.
     */
    static String room(String map, String town) {
        String named = ROOM_NAMES.get(map);
        if (named != null) return named;
        if (map.endsWith("SPEECH_HOUSE")) return "House"; // a house whose people talk about something
        List<String> words = new ArrayList<>(List.of(map.replaceFirst("_BETA$", "").replace("HO_OH", "HO OH")
                .replace("POKECENTER_1F", "POKECENTER").split("_")));
        if (town != null) {
            String[] place = town.toUpperCase(java.util.Locale.ROOT).replace("'", "").replace("MT.", "MOUNT ")
                    .split("[^A-Z0-9]+");
            for (int i = 0; i < place.length && !words.isEmpty(); i++) {
                String w = words.getFirst();
                if (!place[i].equals(w) && !(w.length() >= 3 && place[i].startsWith(w))) break;
                words.removeFirst();
            }
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            boolean possessive = w.length() > 2 && w.endsWith("S") && i + 1 < words.size()
                    && Set.of("HOUSE", "LAB", "ROOM", "HOME").contains(words.get(i + 1));
            if (possessive) w = w.substring(0, w.length() - 1);
            String nice = WORDS.get(w);
            if (nice == null) {
                nice = w.matches(".*\\d.*") || COMPASS.contains(w) ? w
                        : w.charAt(0) + w.substring(1).toLowerCase(java.util.Locale.ROOT);
            }
            out.add(possessive ? nice + "'s" : nice);
        }
        return String.join(" ", out);
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
        return at.unownDex + NUM_UNOWN + 1 - at.playerId; // through wUnlockedUnowns, right after wUnownDex
    }

    @Override
    public Gen2Save read(Ram ram) {
        // As in PokemonRed: a named player and well-formed lists rule out boot, the title screen and loading.
        int name = at.playerId + 2, first = ram.u8(name);
        if (first < 0x80 || first > 0xBF
                || !PokemonRed.terminated(ram.slice(name, NAME_LENGTH))) {
            return null;
        }
        int count = ram.u8(at.partyCount), species = at.partyCount + 1, mons = at.partyCount + 8;
        if (count > 6 || ram.u8(species + count) != 0xFF) return null;
        int playerId = ram.u16(at.playerId);
        List<Gen2Save.Mon> party = new ArrayList<>();
        List<PartyMon> members = new ArrayList<>();
        List<Gen2Save.Slot> slots = new ArrayList<>();
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
            boolean dizzyPunch = false, extremeSpeed = false;
            for (int m = 0; m < NUM_MOVES; m++) {
                int move = ram.u8(mon + MON_MOVES + m);
                dizzyPunch |= move == DIZZY_PUNCH;
                extremeSpeed |= move == EXTREMESPEED;
            }
            slots.add(new Gen2Save.Slot(id, egg, shiny, hp, ram.u16(mon + MON_ID), ram.u16(mon + MON_DVS), dizzyPunch,
                    level, ram.u8(mon + MON_HAPPINESS), ram.u8(mon + MON_POKERUS), extremeSpeed));
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
        int money = u24(ram, at.coins + MONEY), momsMoney = u24(ram, at.coins + MOMS_MONEY);
        if (money > MAX_MONEY || momsMoney > MAX_MONEY) return null;
        int hms = 0, tms = 0;
        for (int i = 0; i < NUM_HMS; i++) if (ram.u8(bag + NUM_TMS + i) > 0) hms++;
        for (int i = 0; i < NUM_TMS; i++) if (ram.u8(bag + i) > 0) tms++;
        Set<Integer> flags = new HashSet<>();
        for (int flag : Gen2Save.WATCHED_EVENTS) if (event(ram, flag)) flags.add(flag);
        BitSet unown = new BitSet(); // forms (1-26) in the order registered, then 0s (engine/pokedex/unown_dex.asm)
        for (int i = 0; i < NUM_UNOWN && ram.u8(at.unownDex + i) != 0; i++) {
            int form = ram.u8(at.unownDex + i);
            if (form <= NUM_UNOWN) unown.set(form);
        }
        Set<Integer> contacts = new HashSet<>(); // contact ids; a deleted number leaves a 0 (phone.asm DelCellNum)
        for (int i = 0; i < CONTACT_LIST_SIZE; i++) if (ram.u8(at.phoneList + i) != 0) contacts.add(ram.u8(at.phoneList + i));
        int phone = contacts.size();
        int group = ram.u8(at.partyCount + MAP_GROUP), number = ram.u8(at.partyCount + MAP_GROUP + 1);
        boolean onMap = group >= 1 && group < mapIds.length && number >= 1 && number < mapIds[group].length;
        BitSet caught = dex(ram, at.dexCaught), seen = dex(ram, at.dexCaught + DEX_BYTES);
        seen.or(caught);
        return new Gen2Save(playerId, ram.u8(at.badges), ram.u8(at.badges + 1), ram.u8(at.hallOfFame),
                event(ram, EVENT_BEAT_ELITE_FOUR) && event(ram, EVENT_RED_IN_MT_SILVER),
                event(ram, EVENT_GOT_A_POKEMON_FROM_ELM), event(ram, EVENT_CLEARED_RADIO_TOWER),
                event(ram, EVENT_GOT_MASTER_BALL_FROM_ELM), caught, seen, count, List.copyOf(party), Set.copyOf(items),
                hms, coins, unown.cardinality(), phone, ram.u16(at.playHours), ram.u8(at.playHours + 2),
                onMap ? places[group][number] : null, List.copyOf(members),
                PokemonRed.text(ram.slice(name, NAME_LENGTH), 0, NAME_LENGTH), at.crystal,
                onMap ? mapIds[group][number] : null, ram.u8(at.curBox), Set.copyOf(contacts), List.copyOf(slots),
                money, momsMoney, tms, ram.u8(at.unownDex + NUM_UNOWN), Set.copyOf(flags),
                onMap ? townPin(ram, group, number) : null,
                onMap ? spot(ram, group, number) : null);
    }

    dev.pppp.model.MapPin townPin(Ram ram, int group, int number) {
        String map = mapIds[group][number];
        if ("POKECENTER_2F".equals(map) || PokecenterDig.LINK_ROOMS.contains(map)) {
            // These rooms are shared by all Centers. wBackupMapGroup/Number name the 1F at the foot of the stairs.
            int g = ram.u8(at.digWarp + 4), n = ram.u8(at.digWarp + 5);
            if (pokecenter(g, n)) return pins[g][n];
        }
        return pins[group][number];
    }

    /** wPlayerDirection holds the facing in bits 2-3 (constants/map_object_constants.asm: OW_DOWN .. OW_RIGHT). */
    private static final String[] FACING = {"down", "up", "left", "right"};

    private static int u24(Ram ram, int at) {
        return ram.u8(at) << 16 | ram.u8(at + 1) << 8 | ram.u8(at + 2);
    }

    /** A party member for the profile: the struct at mon, its nickname at nick. */
    private PartyMon member(Ram ram, int mon, int nick, boolean egg, boolean shiny) {
        int id = ram.u8(mon), hp = ram.u16(mon + MON_HP), item = ram.u8(mon + MON_ITEM);
        String nickname = PokemonRed.text(ram.slice(nick, NAME_LENGTH), 0, NAME_LENGTH);
        List<String> moves = new ArrayList<>(), moveTypeNames = new ArrayList<>();
        for (int i = 0; i < NUM_MOVES; i++) {
            int move = ram.u8(mon + MON_MOVES + i);
            if (move > 0 && move <= NUM_ATTACKS) {
                moves.add(moveNames[move]);
                moveTypeNames.add(java.util.Objects.requireNonNullElse(moveTypes[move], "normal"));
            }
        }
        List<String> typeNames = egg ? List.of() : IntStream.of(types[id]).distinct().mapToObj(PokemonGen2::typeName)
                .filter(java.util.Objects::nonNull).toList();
        return new PartyMon(id, egg ? "Egg" : names[id],
                egg || nickname.isEmpty() || nickname.equalsIgnoreCase(names[id]) ? null : nickname,
                ram.u8(mon + MON_LEVEL), hp, ram.u16(mon + MON_MAXHP), egg ? null : status(ram.u8(mon + MON_STATUS), hp),
                item == 0 ? null : itemNames[item], List.copyOf(moves), shiny, egg, typeNames, List.copyOf(moveTypeNames));
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

    /**
     * Pokédex entries (data/pokemon/dex_entries/*.asm, home GetDexEntryPointer): PokedexDataPointerTable holds an
     * address per number; its bank is one per 64 numbers ($68 + (n - 1) / 64 in Gold/Silver, a 4-byte table in
     * Crystal). An entry: the category ending in "@", height (feet * 100 + inches) and weight (tenths of a pound)
     * as little-endian words, then two pages of text each ending in "@", lines split by <NEXT>.
     */
    @Override
    public Dex dex(int n) {
        if (n < 1 || n > DEX_SIZE) return null;
        int pointer = u16le(rom, at.dexPointers + (n - 1) * 2), group = (n - 1) / 64;
        int bank = at.dexBanks < 0 ? DEX_BANK_GOLD_SILVER + group : rom[at.dexBanks + group] & 0xFF;
        int i = rom(bank, pointer);
        if (i < 0 || i + 200 > rom.length) return null;
        String category = dexText(i);
        i = end(i) + 1;
        int height = u16le(rom, i), weight = u16le(rom, i + 2);
        int page1 = i + 4, page2 = end(page1) + 1;
        String first = dexText(page1), second = dexText(page2);
        String text = first.matches(".*\\p{L}-") ? first.substring(0, first.length() - 1) + second // "sto-" / "rms."
                : (first + " " + second).trim();
        List<String> typeNames = IntStream.of(types[n]).distinct().mapToObj(PokemonGen2::typeName)
                .filter(java.util.Objects::nonNull).toList();
        return new Dex(names[n], PokemonRed.titleCase(category), typeNames, height / 100, height % 100, weight, text);
    }

    private static final int DEX_BANK_GOLD_SILVER = 0x68, DEX_TEXT_MAX = 200;

    private int end(int from) {
        int i = from;
        while (i < from + DEX_TEXT_MAX && rom[i] != 0x50) i++;
        return i;
    }

    /** Dex text up to "@": <NEXT> ($4E) is a space (or joins a hyphenated word); Gen 2's 'r 's 't ($D3-$D5) and é ($EA) on top of PokemonRed#text. */
    private String dexText(int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < end(from); i++) {
            int c = rom[i] & 0xFF;
            if (c == 0x4E) {
                int end = sb.length();
                while (end > 0 && sb.charAt(end - 1) == ' ') end--;
                if (end > 1 && sb.charAt(end - 1) == '-' && Character.isLetter(sb.charAt(end - 2))) {
                    sb.setLength(end - 1); // a word hyphenated across lines ("anger-" / "ed")
                    continue;
                }
            }
            sb.append(switch (c) {
                case 0x4E -> " ";
                case 0xD3 -> "'r";
                case 0xD5 -> "'t";
                case 0xEA -> "é";
                default -> PokemonRed.text(rom, i, 1);
            });
        }
        // The game shouts names and items (LUGIA, BERRIES, POKéMON); read as words in the card.
        String text = sb.toString().replace("POKéMON", "Pokémon").replace("POKé", "Poké").replaceAll(" +", " ").trim();
        return java.util.regex.Pattern.compile("\\b[A-Z][A-Z']+\\b").matcher(text)
                .replaceAll(m -> m.group().charAt(0) + m.group().substring(1).toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * The player walking around (ChrisSpriteGFX, the same address in Gold, Silver and Crystal; gfx/sprites/chris.png):
     * 16x96, plain 2bpp, two tiles a row, six 16x16 frames: down, up, left, then each one's walking frame. Coloured
     * with the daytime red overworld palette (gfx/overworld/npc_sprites.pal), colour 0 transparent.
     */
    @Override
    public byte[] playerSprite() {
        return cached(icons, PLAYER_SPRITE_KEY, () -> {
            int at = rom(0x30, 0x4000);
            int[] argb = {0, 0xFF000000 | Gen2Pic.rgb555(31, 19, 10), 0xFF000000 | Gen2Pic.rgb555(31, 7, 1), 0xFF000000};
            var img = new java.awt.image.BufferedImage(16, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int t = 0; t < 24; t++) {
                for (int row = 0; row < 8; row++) {
                    int lo = rom[at + t * 16 + row * 2] & 0xFF, hi = rom[at + t * 16 + row * 2 + 1] & 0xFF;
                    for (int col = 0; col < 8; col++) {
                        int v = (lo >> 7 - col & 1) | (hi >> 7 - col & 1) << 1;
                        img.setRGB(t % 2 * 8 + col, t / 2 * 8 + row, argb[v]);
                    }
                }
            }
            var out = new java.io.ByteArrayOutputStream();
            try {
                javax.imageio.ImageIO.write(img, "png", out);
            } catch (java.io.IOException e) {
                return null;
            }
            return out.toByteArray();
        });
    }

    private static final int PLAYER_SPRITE_KEY = -1; // in the icon cache, beside the dex numbers

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
