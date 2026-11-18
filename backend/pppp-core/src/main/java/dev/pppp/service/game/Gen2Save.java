package dev.pppp.service.game;

import dev.pppp.model.MapPin;
import dev.pppp.model.MapSpot;
import dev.pppp.model.PartyMon;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * What {@link PokemonGen2} reads of a Gold/Silver/Crystal save in WRAM bank 1, for {@link Achievements} and
 * {@link TrainerScoring}. Boxes live in cartridge RAM ({@link Sram}, read far less often): "owned" below means the
 * party, or the Pokédex's caught flags (which stay set after a release or trade).
 *
 * @param johtoBadges   wJohtoBadges bits; kantoBadges likewise
 * @param hallOfFame    wHallOfFameCount
 * @param redBeaten     Red is gone from Mt. Silver since the last Hall of Fame entry (see PokemonGen2)
 * @param caught        Pokédex caught flags by dex number (bit n = #n, 1..251); seen includes caught
 * @param partyCount    wPartyCount, eggs included; party holds the hatched members only
 * @param items         every item id held in the bag (items, balls, key items) or the PC
 * @param hms           how many of HM01-HM07 are held
 * @param unownForms    Unown forms registered in the Unown Dex
 * @param phoneContacts numbers registered in the Pokégear
 * @param playHours     in-game clock hours; playMinutes its minutes
 * @param location      where the player is ("Route 42", "Violet City · Pokémon Center"), null when unknown
 * @param members       the whole party for the profile, eggs included, in order
 * @param trainerName   wPlayerName
 * @param crystal       Pokémon Crystal (else Gold or Silver)
 * @param map           the pret map constant of wMapGroup/wMapNumber ("ELMS_LAB"), null when unknown
 * @param curBox        wCurBox: the PC box (0-13) whose contents are sBox; its own sBoxN is stale
 * @param contacts      the contact ids in wPhoneList (pret constants/phone_constants.asm)
 * @param slots         the whole party, eggs included, in order, for the moments between two readings
 * @param money         wMoney; momsMoney wMomsMoney (both 3 bytes, big-endian)
 * @param tms           how many different TMs are held
 * @param unownPuzzles  wUnlockedUnowns: bit 0-3 = the Kabuto, Omanyte, Aerodactyl and Ho-Oh puzzles solved
 * @param flags         which of {@link #WATCHED_EVENTS} are set
 * @param pin           where the player is on the Pokégear town map, null off the map (link rooms, the ship)
 * @param spot          where the player stands on their map, null when the map is unknown
 */
public record Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten,
                       boolean gotStarter, boolean radioTowerCleared, boolean gotMasterBall, BitSet caught,
                       BitSet seen, int partyCount, List<Mon> party, Set<Integer> items, int hms, int coins,
                       int unownForms, int phoneContacts, int playHours, int playMinutes, String location,
                       List<PartyMon> members, String trainerName, boolean crystal, String map, int curBox,
                       Set<Integer> contacts, List<Slot> slots, int money, int momsMoney, int tms,
                       int unownPuzzles, Set<Integer> flags, MapPin pin, MapSpot spot) {

    /** Without the spot on the map (tests that do not need it). */
    public Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten,
                    boolean gotStarter, boolean radioTowerCleared, boolean gotMasterBall, BitSet caught, BitSet seen,
                    int partyCount, List<Mon> party, Set<Integer> items, int hms, int coins, int unownForms,
                    int phoneContacts, int playHours, int playMinutes, String location, List<PartyMon> members,
                    String trainerName, boolean crystal, String map, int curBox, Set<Integer> contacts,
                    List<Slot> slots, int money, int momsMoney, int tms, int unownPuzzles, Set<Integer> flags,
                    MapPin pin) {
        this(playerId, johtoBadges, kantoBadges, hallOfFame, redBeaten, gotStarter, radioTowerCleared, gotMasterBall,
                caught, seen, partyCount, party, items, hms, coins, unownForms, phoneContacts, playHours, playMinutes,
                location, members, trainerName, crystal, map, curBox, contacts, slots, money, momsMoney, tms,
                unownPuzzles, flags, pin, null);
    }

    /** Without the town-map pin (tests that do not need it). */
    public Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten,
                    boolean gotStarter, boolean radioTowerCleared, boolean gotMasterBall, BitSet caught, BitSet seen,
                    int partyCount, List<Mon> party, Set<Integer> items, int hms, int coins, int unownForms,
                    int phoneContacts, int playHours, int playMinutes, String location, List<PartyMon> members,
                    String trainerName, boolean crystal, String map, int curBox, Set<Integer> contacts,
                    List<Slot> slots, int money, int momsMoney, int tms, int unownPuzzles, Set<Integer> flags) {
        this(playerId, johtoBadges, kantoBadges, hallOfFame, redBeaten, gotStarter, radioTowerCleared, gotMasterBall,
                caught, seen, partyCount, party, items, hms, coins, unownForms, phoneContacts, playHours, playMinutes,
                location, members, trainerName, crystal, map, curBox, contacts, slots, money, momsMoney, tms,
                unownPuzzles, flags, null, null);
    }

    /** Without the fields the later achievements added (older tests). */
    public Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten,
                    boolean gotStarter, boolean radioTowerCleared, boolean gotMasterBall, BitSet caught, BitSet seen,
                    int partyCount, List<Mon> party, Set<Integer> items, int hms, int coins, int unownForms,
                    int phoneContacts, int playHours, int playMinutes, String location, List<PartyMon> members,
                    String trainerName, boolean crystal, String map, int curBox, Set<Integer> contacts,
                    List<Slot> slots) {
        this(playerId, johtoBadges, kantoBadges, hallOfFame, redBeaten, gotStarter, radioTowerCleared, gotMasterBall,
                caught, seen, partyCount, party, items, hms, coins, unownForms, phoneContacts, playHours, playMinutes,
                location, members, trainerName, crystal, map, curBox, contacts, slots, 0, 0, 0, 0, Set.of());
    }

    /** Without what only the newer achievements read (tests of the older ones). */
    public Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten, boolean gotStarter,
             boolean radioTowerCleared, boolean gotMasterBall, BitSet caught, BitSet seen, int partyCount,
             List<Mon> party, Set<Integer> items, int hms, int coins, int unownForms, int phoneContacts, int playHours,
             int playMinutes, String location, List<PartyMon> members) {
        this(playerId, johtoBadges, kantoBadges, hallOfFame, redBeaten, gotStarter, radioTowerCleared, gotMasterBall,
                caught, seen, partyCount, party, items, hms, coins, unownForms, phoneContacts, playHours, playMinutes,
                location, members, null, false, null, 0, Set.of(), List.of());
    }

    /**
     * A hatched party member. ownCatch: its original trainer id is the player's. type1/type2 are pret type ids
     * from the ROM's base data (equal for a single type; -1 when the ROM could not be read).
     */
    public record Mon(int species, int level, boolean shiny, boolean ownCatch, int type1, int type2) {}

    /**
     * A party slot, eggs included: species from its struct (what an egg will hatch into), HP, original trainer id,
     * the two DV bytes as one number (atk/def << 8 | spd/spc), and whether it knows Dizzy Punch, which only the
     * Odd Egg's babies do in Gen 2 (pret data/events/odd_eggs.asm). ot and dvs tell one Pokémon from another.
     * level, happiness and pokerus are the party_struct bytes; extremeSpeed: it knows ExtremeSpeed, which in Gen 2
     * only Crystal's Dragon's Den Dratini does.
     */
    public record Slot(int species, boolean egg, boolean shiny, int hp, int ot, int dvs, boolean dizzyPunch, int level,
                       int happiness, int pokerus, boolean extremeSpeed) {
        public Slot(int species, boolean egg, boolean shiny, int hp, int ot, int dvs, boolean dizzyPunch) {
            this(species, egg, shiny, hp, ot, dvs, dizzyPunch, 1, 0, 0, false);
        }
    }

    /**
     * What the cartridge RAM (the battery save) holds that WRAM does not, read by {@link PokemonGen2#sram}. boxed:
     * species (dex numbers) in the PC boxes, eggs not counted; linkWins / linkLosses: sLinkBattleWins /
     * sLinkBattleLosses; towerState: Crystal's sBattleTowerChallengeState (0 in Gold/Silver).
     */
    public record Sram(BitSet boxed, int linkWins, int linkLosses, int towerState) {}

    /** Item ids, pret constants/item_constants.asm (same in pokegold and pokecrystal; checked against ItemNames in the ROMs). */
    static final int MASTER_BALL = 0x01, BICYCLE = 0x07, MOON_STONE = 0x08, FIRE_STONE = 0x16, THUNDERSTONE = 0x17,
            WATER_STONE = 0x18, LEAF_STONE = 0x22, COIN_CASE = 0x36, SECRETPOTION = 0x43,
            CLEAR_BELL = 0x46, // Crystal only; unused in Gold/Silver, so never held there
            SILVER_WING = 0x47, CARD_KEY = 0x7F, SUN_STONE = 0xA9, SQUIRTBOTTLE = 0xAF, RAINBOW_WING = 0xB2,
            OLD_ROD = 0x3A, GOOD_ROD = 0x3B, SUPER_ROD = 0x3D, HEAVY_BALL = 0x9D, LEVEL_BALL = 0x9F, LURE_BALL = 0xA0,
            FAST_BALL = 0xA1, FRIEND_BALL = 0xA4, MOON_BALL = 0xA5, LOVE_BALL = 0xA6;
    /** Kurt's seven Apricorn balls. */
    static final int[] APRICORN_BALLS = {LEVEL_BALL, LURE_BALL, MOON_BALL, FRIEND_BALL, FAST_BALL, HEAVY_BALL, LOVE_BALL};

    /**
     * Event flags the achievements read (constants/event_flags.asm, the same numbers in pokegold and pokecrystal):
     * the egg from Elm's aide, the Route 36 tree and Vermilion's Snorlax fought, Kanto's power back, Kiyo's
     * Tyrogue, the seven weekday siblings' gifts (Monica to Sunny), and the Mystery Egg (got from Mr. Pokémon, given
     * to Elm: the Cable Club opens).
     */
    static final int EVENT_GOT_TOGEPI_EGG_FROM_ELMS_AIDE = 45, EVENT_FOUGHT_SUDOWOODO = 42,
            EVENT_FOUGHT_SNORLAX = 1872, EVENT_RESTORED_POWER_TO_KANTO = 205, EVENT_GOT_TYROGUE_FROM_KIYO = 97,
            EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON = 30, EVENT_GAVE_MYSTERY_EGG_TO_ELM = 31;
    static final int[] SIBLING_GIFTS = {111, 107, 101, 103, 99, 109, 105}; // Monica, Wesley, Tuscany, Arthur, Frieda, Santos, Sunny
    static final Set<Integer> WATCHED_EVENTS = java.util.stream.IntStream.concat(
            java.util.stream.IntStream.of(EVENT_GOT_TOGEPI_EGG_FROM_ELMS_AIDE, EVENT_FOUGHT_SUDOWOODO,
                    EVENT_FOUGHT_SNORLAX, EVENT_RESTORED_POWER_TO_KANTO, EVENT_GOT_TYROGUE_FROM_KIYO,
                    EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON, EVENT_GAVE_MYSTERY_EGG_TO_ELM),
            java.util.Arrays.stream(SIBLING_GIFTS)).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
    static final int[] STONES = {FIRE_STONE, WATER_STONE, THUNDERSTONE, LEAF_STONE, MOON_STONE, SUN_STONE};

    /** Pokédex numbers (pret constants/pokemon_constants.asm; Gen 2 species ids are dex numbers). */
    static final int RATICATE = 20, FURRET = 162, GYARADOS = 130, MEWTWO = 150, MEW = 151, CELEBI = 251,
            CHIKORITA = 152, FERALIGATR = 160;
    /** Evolve only by trading in Gen 2 (pret data/pokemon/evos_attacks.asm EVOLVE_TRADE): Alakazam, Machamp, Golem,
     * Gengar, Slowking, Politoed, Steelix, Scizor, Kingdra, Porygon2. */
    static final int[] TRADE_EVOLVED = {65, 68, 76, 94, 199, 186, 208, 212, 230, 233};

    public boolean owns(int dex) {
        return caught.get(dex);
    }
}
