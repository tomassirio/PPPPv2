package dev.pppp.service.game;

import static dev.pppp.service.game.Gen2Save.*;

import dev.pppp.model.Achievement;
import dev.pppp.model.TrainerLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * The achievement catalogue (the Achievements & Trainer Score design's, less what the RAM cannot tell, plus the
 * link cable's, less six Gen 1-only ones, plus the review's 36: 85 entries, 8 categories) and how each is told. Most read the save as it is now. "At the Hall of
 * Fame" and "at Red" ones look at the party on the first confirmed reading where the Hall of Fame count leaves 0,
 * or Red leaves Mt. Silver, after a reading where it had not; hatching and trading compare two readings too. So
 * they need the tracker to see that moment, and are missed (never guessed) when a restart falls in between. What
 * builds up over time (eggs hatched, phone numbers, link partners, trades) is the {@link TrainerLog}
 * ({@link #log}); boxes, link battle record and the Battle Tower come from the battery save ({@link Sram}, null
 * until read).
 */
public final class Achievements {
    private Achievements() {}

    /**
     * One confirmed reading: before is the previous one of this console run (or null), log already includes this
     * reading, sram is the last battery save read (or null).
     */
    record Moment(Gen2Save before, Gen2Save now, TrainerLog log, Sram sram) {}

    private record Rule(Achievement achievement, Predicate<Moment> earned) {}

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();

    /** Reads the save as it is now. */
    private static void add(String id, String category, String name, String description, int points, String how,
                            Predicate<Gen2Save> now) {
        when(id, category, name, description, points, how, m -> now.test(m.now()));
    }

    private static void when(String id, String category, String name, String description, int points, String how,
                             Predicate<Moment> earned) {
        RULES.put(id, new Rule(Achievement.of(id, category, name, description, points, how), earned));
    }

    private static Predicate<Moment> atHallOfFame(Predicate<Gen2Save> party) {
        return m -> m.before() != null && m.before().hallOfFame() == 0 && m.now().hallOfFame() > 0 && party.test(m.now());
    }

    private static Predicate<Moment> atRed(Predicate<Gen2Save> party) {
        return m -> m.before() != null && !m.before().redBeaten() && m.now().redBeaten() && party.test(m.now());
    }

    private static Predicate<Gen2Save> badge(int johtoBit) {
        return s -> (s.johtoBadges() & 1 << johtoBit) != 0;
    }

    private static Predicate<Gen2Save> flag(int event) {
        return s -> s.flags().contains(event);
    }

    private static java.util.stream.Stream<Slot> hatchedSlots(Gen2Save s) {
        return s.slots().stream().filter(x -> !x.egg());
    }

    private static Predicate<Gen2Save> ownsAll(int... dex) {
        return s -> Arrays.stream(dex).allMatch(s::owns);
    }

    /** Elite Four and Champion rooms (pret map constants, the same in pokegold and pokecrystal). */
    static final Set<String> ELITE_FOUR = Set.of("WILLS_ROOM", "KOGAS_ROOM", "BRUNOS_ROOM", "KARENS_ROOM",
            "LANCES_ROOM");
    /** Where a link trade happens: the Trade Center, or the Time Capsule with a Red/Blue/Yellow player. */
    static final Set<String> TRADE_ROOMS = Set.of("TRADE_CENTER", "TIME_CAPSULE");
    /**
     * Every Pokégear number a player can register (pret constants/phone_constants.asm): Mom, Bill, Elm and the
     * trainers; not PHONE_OAK (never handed out) nor the const_skip gaps (8-10, 25). Buena (38) is Crystal's.
     */
    static final Set<Integer> PHONE_NUMBERS = Set.copyOf(IntStream.rangeClosed(1, 37)
            .filter(id -> id != 2 && (id < 8 || id > 10) && id != 25).boxed().toList());
    static final int PHONE_ELM = 4, BUENA = 38, BATTLETOWER_WON_CHALLENGE = 3, BATTLETOWER_RECEIVED_REWARD = 4; // battle_tower_constants
    static final int SOCIAL_BUTTERFLY = 5, BREEDER = 10, TRADER = 10, COLOSSEUM_REGULAR = 10, TEN_TIME_CHAMPION = 10,
            MAX_MONEY = 999_999, MOMS_SAVINGS = 100_000, MAX_HAPPINESS = 255, PERFECT_DVS = 0xFFFF, TM_HOARDER = 25,
            ALL_UNOWN_PUZZLES = 0b1111;
    /** The roaming beasts and the two box legendaries. */
    static final int[] LEGENDARIES = {243, 244, 245, 249, 250};
    /**
     * What Gold, Silver and Crystal can give between them: all 251 but the Kanto starters (#1-#9), the fossils
     * (#138-#142), the three birds (#144-#146), Mewtwo, Mew and Celebi, which need a Gen 1 game or an event. 231.
     */
    static final int[] GSC_OBTAINABLE = IntStream.rangeClosed(1, 251).filter(d -> d > 9 && (d < 138 || d > 142)
            && (d < 144 || d > 146) && d != 150 && d != 151 && d != 251).toArray();
    static final int DRATINI = 147, DRAGONITE = 149;

    static {
        // Story. Badge bits: wJohtoBadges in the order of pret constants/engine_flags.asm (ZEPHYRBADGE .. RISINGBADGE).
        add("new-bark-beginnings", "Story", "New Bark Beginnings", "Receive a starter from Prof. Elm", 1,
                "event flag EVENT_GOT_A_POKEMON_FROM_ELM", Gen2Save::gotStarter);
        add("zephyr-badge", "Story", "Zephyr Badge", "Defeat Falkner", 3, "Zephyr Badge bit", badge(0));
        add("hive-badge", "Story", "Hive Badge", "Defeat Bugsy", 3, "Hive Badge bit", badge(1));
        add("plain-badge", "Story", "Plain Badge", "Defeat Whitney", 3, "Plain Badge bit", badge(2));
        add("fog-badge", "Story", "Fog Badge", "Defeat Morty", 3, "Fog Badge bit", badge(3));
        add("storm-badge", "Story", "Storm Badge", "Defeat Chuck", 3, "Storm Badge bit", badge(5));
        add("mineral-badge", "Story", "Mineral Badge", "Defeat Jasmine", 3, "Mineral Badge bit", badge(4));
        add("glacier-badge", "Story", "Glacier Badge", "Defeat Pryce", 5, "Glacier Badge bit", badge(6));
        add("rising-badge", "Story", "Rising Badge", "Defeat Clair", 10, "Rising Badge bit", badge(7));
        add("rocket-bust", "Story", "Rocket Bust", "Clear the Goldenrod Radio Tower", 10,
                "event flag EVENT_CLEARED_RADIO_TOWER", Gen2Save::radioTowerCleared);
        add("champion-of-johto", "Story", "Champion of Johto", "Defeat Lance and enter the Hall of Fame", 25,
                "wHallOfFameCount above 0", s -> s.hallOfFame() > 0);
        add("back-to-kanto", "Story", "Back to Kanto", "Collect all 8 Kanto badges", 25, "all 8 wKantoBadges bits",
                s -> s.kantoBadges() == 0xFF);
        add("the-silent-summit", "Story", "The Silent Summit", "Defeat Red on Mt. Silver", 50,
                "Hall of Fame entered and Red gone from Mt. Silver (EVENT_BEAT_ELITE_FOUR, EVENT_RED_IN_MT_SILVER)",
                Gen2Save::redBeaten);

        // Pokédex.
        add("field-researcher", "Pokédex", "Field Researcher", "Catch 50 species", 5, "50 Pokédex caught flags",
                s -> s.caught().cardinality() >= 50);
        add("dex-holder", "Pokédex", "Dex Holder", "Catch 100 species", 10, "100 Pokédex caught flags",
                s -> s.caught().cardinality() >= 100);
        // Legends: the Pokédex caught flag (also set by a trade or an evolution).
        add("tin-tower-guardian", "Legends", "Tin Tower Guardian", "Catch Ho-Oh", 25, "Pokédex caught flag #250",
                ownsAll(250));
        add("whirl-islands-guardian", "Legends", "Whirl Islands Guardian", "Catch Lugia", 25,
                "Pokédex caught flag #249", ownsAll(249));
        add("thunder-chaser", "Legends", "Thunder Chaser", "Catch Raikou", 10, "Pokédex caught flag #243",
                ownsAll(243));
        add("fire-chaser", "Legends", "Fire Chaser", "Catch Entei", 10, "Pokédex caught flag #244", ownsAll(244));
        add("north-wind", "Legends", "North Wind", "Catch Suicune", 10, "Pokédex caught flag #245", ownsAll(245));
        // Training.
        add("level-up", "Training", "Level Up", "Any Pokémon reaches Lv 50", 5, "a party member at Lv 50+",
                s -> s.party().stream().anyMatch(m -> m.level() >= 50));
        add("centurion", "Training", "Centurion", "Any Pokémon reaches Lv 100", 25, "a party member at Lv 100",
                s -> s.party().stream().anyMatch(m -> m.level() >= 100));
        add("full-house", "Training", "Full House", "Six Pokémon at Lv 100 in the party", 50,
                "six party members at Lv 100", s -> s.party().stream().filter(m -> m.level() >= 100).count() == 6);
        when("solo-run", "Training", "Solo Run", "Become Champion using only your starter (missable)", 25,
                "at the Hall of Fame the party is one own-caught Chikorita-to-Feraligatr (the run before is not seen)",
                atHallOfFame(s -> s.partyCount() == 1 && s.party().size() == 1
                        && s.party().getFirst().ownCatch()
                        && s.party().getFirst().species() >= CHIKORITA && s.party().getFirst().species() <= FERALIGATR));
        when("rattata-rules", "Training", "Rattata Rules", "Defeat Red with a Raticate or Furret in the party", 10,
                "at Red's defeat the party holds a Raticate or Furret",
                atRed(s -> s.party().stream().anyMatch(m -> m.species() == RATICATE || m.species() == FURRET)));

        // Items.
        add("silphs-finest", "Items", "Silph's Finest", "Obtain the Master Ball", 5,
                "EVENT_GOT_MASTER_BALL_FROM_ELM, or a Master Ball in the bag or PC",
                s -> s.gotMasterBall() || s.items().contains(MASTER_BALL));
        when("self-control", "Items", "Self-Control", "Defeat Lance with the Master Ball unused", 10,
                "at the Hall of Fame a Master Ball is in the bag or PC",
                atHallOfFame(s -> s.items().contains(MASTER_BALL)));
        add("wingspan", "Items", "Wingspan", "Hold both the Rainbow Wing and Silver Wing", 10,
                "RAINBOW_WING and SILVER_WING among the key items",
                s -> s.items().contains(RAINBOW_WING) && s.items().contains(SILVER_WING));
        add("full-kit", "Items", "Full Kit", "Own all 7 HMs", 5, "HM01-HM07 in wTMsHMs", s -> s.hms() == 7);
        add("stone-collector", "Items", "Stone Collector",
                "Hold all 6 evolution stones (Fire, Water, Thunder, Leaf, Moon, Sun)", 5, "all six in the bag or PC",
                s -> Arrays.stream(STONES).allMatch(s.items()::contains));

        // Side.
        add("unown-scholar", "Side", "Unown Scholar", "Register all 26 Unown forms", 25, "26 forms in wUnownDex",
                s -> s.unownForms() == 26);
        add("red-gyarados", "Side", "Red Gyarados", "Catch the shiny Gyarados at Lake of Rage (missable if fainted)",
                5, "an own-caught shiny Gyarados in the party (any shiny Gyarados of the player's counts)",
                s -> s.party().stream().anyMatch(m -> m.shiny() && m.ownCatch() && m.species() == GYARADOS));
        add("lucky-star", "Side", "Lucky Star", "Catch any other shiny Pokémon", 25,
                "an own-caught shiny other than Gyarados in the party (a hatched one counts)",
                s -> s.party().stream().anyMatch(m -> m.shiny() && m.ownCatch() && m.species() != GYARADOS));
        when("breeder", "Side", "Breeder", "Hatch 10 eggs", 5,
                "10 party eggs seen hatching: a slot's egg (same species, OT and DVs) becomes a Pokémon",
                m -> m.log().hatched() >= BREEDER);
        when("odd-one", "Side", "Odd One", "Hatch a shiny from the Odd Egg (Crystal)", 25,
                "an egg hatches into a shiny that knows Dizzy Punch, which only the Odd Egg's babies do",
                m -> hatched(m.before(), m.now()).stream().anyMatch(x -> x.shiny() && x.dizzyPunch()));
        when("tower-climber", "Side", "Tower Climber", "Win 7 straight at the Battle Tower (Crystal)", 10,
                "sBattleTowerChallengeState is BATTLETOWER_WON_CHALLENGE or _RECEIVED_REWARD in the battery save",
                m -> m.sram() != null && (m.sram().towerState() == BATTLETOWER_WON_CHALLENGE
                        || m.sram().towerState() == BATTLETOWER_RECEIVED_REWARD));
        when("card-collector", "Side", "Card Collector", "Register every Pokégear phone number available", 10,
                "every contact id but Oak's ever seen in wPhoneList (Buena too in Crystal)",
                m -> m.log().contacts().containsAll(phoneNumbers(m.now().crystal())));
        add("high-roller", "Side", "High Roller", "Hold 9,999 coins at the Game Corner", 5, "wCoins = 9,999",
                s -> s.coins() >= 9999);
        add("johto-friends", "Side", "Johto Friends", "Own all three Johto starters' final forms", 10,
                "Pokédex caught flags #154, #157, #160", ownsAll(154, 157, 160));

        // Challenge.
        when("untouchable", "Challenge", "Untouchable", "Beat the Elite Four with no Pokémon fainting (missable)", 25,
                "the Hall of Fame count goes up and no party member was seen at 0 HP from Will's room on",
                m -> m.before() != null && m.now().hallOfFame() > m.before().hallOfFame()
                        && "clean".equals(m.log().eliteFour()));
        when("speedrunner", "Challenge", "Speedrunner", "Enter the Hall of Fame under 6:00 play time", 25,
                "at the Hall of Fame wGameTimeHours is under 6", atHallOfFame(s -> s.playHours() < 6));
        when("low-level", "Challenge", "Low Level", "Defeat Red with no Pokémon above Lv 60", 50,
                "at Red's defeat no party member is above Lv 60",
                atRed(s -> !s.party().isEmpty() && s.party().stream().allMatch(m -> m.level() <= 60)));
        when("mono-type", "Challenge", "Mono-Type", "Become Champion with a party sharing one type", 25,
                "at the Hall of Fame every party member shares a type (types from the ROM's base data)",
                atHallOfFame(Achievements::monoType));

        // Link: the cable between two Pibs' consoles, and what came over it.
        when("plugged-in", "Link", "Plugged In", "Link up with another Pib", 1, "a link cable session",
                m -> !m.log().partners().isEmpty());
        when("first-trade", "Link", "First Trade", "Receive a Pokémon in a link trade", 3,
                "a new Pokémon (OT and DVs) joins the party in the Trade Center or Time Capsule",
                m -> m.log().trades() > 0);
        add("trade-evolution", "Link", "Trade Evolution",
                "Own a Pokémon that evolves by trading (Alakazam, Machamp, Golem, Gengar, Slowking, Politoed, Steelix,"
                        + " Scizor, Kingdra or Porygon2)", 10,
                "a Pokédex caught flag of a species that only trading gives (pret EVOLVE_TRADE)",
                s -> Arrays.stream(TRADE_EVOLVED).anyMatch(s::owns));
        when("cross-version", "Link", "Cross-Version", "Trade with a Pib playing another version", 5,
                "a trade while linked to a Pib on another of Gold, Silver, Crystal (or a Gen 1 game)",
                m -> m.log().crossVersion());
        when("link-champion", "Link", "Link Champion", "Win a link battle", 10,
                "sLinkBattleWins of the battery save above 0", m -> m.log().linkWins() > 0);
        when("social-butterfly", "Link", "Social Butterfly", "Link up with 5 different Pibs", 10,
                "5 different players at the other end of the link cable",
                m -> m.log().partners().size() >= SOCIAL_BUTTERFLY);

        // Added in the achievement review (2026-11). Story.
        add("mystery-egg", "Story", "Mystery Egg", "Receive the egg from Elm's aide", 2,
                "event flag EVENT_GOT_TOGEPI_EGG_FROM_ELMS_AIDE", flag(EVENT_GOT_TOGEPI_EGG_FROM_ELMS_AIDE));
        add("odd-tree", "Story", "Odd Tree", "Deal with the strange tree on Route 36", 3,
                "event flag EVENT_FOUGHT_SUDOWOODO", flag(EVENT_FOUGHT_SUDOWOODO));
        add("wake-up", "Story", "Wake Up!", "Wake the Snorlax blocking Vermilion's Diglett's Cave", 5,
                "event flag EVENT_FOUGHT_SNORLAX", flag(EVENT_FOUGHT_SNORLAX));
        add("power-restored", "Story", "Power Restored", "Get the Kanto Power Plant running again", 5,
                "event flag EVENT_RESTORED_POWER_TO_KANTO", flag(EVENT_RESTORED_POWER_TO_KANTO));
        add("ten-time-champion", "Story", "Ten-Time Champion", "Enter the Hall of Fame 10 times", 25,
                "wHallOfFameCount at 10 or more", s -> s.hallOfFame() >= TEN_TIME_CHAMPION);

        // Pokédex.
        add("rookie", "Pokédex", "Rookie", "Catch 25 species", 3, "25 Pokédex caught flags",
                s -> s.caught().cardinality() >= 25);
        add("eevee-family", "Pokédex", "Eevee Family", "Own all five Eeveelutions", 10,
                "Pokédex caught flags #134, #135, #136, #196, #197", ownsAll(134, 135, 136, 196, 197));
        add("baby-boom", "Pokédex", "Baby Boom", "Own all 8 baby Pokémon", 10,
                "Pokédex caught flags #172-#175, #236, #238-#240", ownsAll(172, 173, 174, 175, 236, 238, 239, 240));
        add("tyrogue-trio", "Pokédex", "Tyrogue Trio", "Own Hitmonlee, Hitmonchan and Hitmontop", 10,
                "Pokédex caught flags #106, #107, #237", ownsAll(106, 107, 237));
        add("grand-collector", "Pokédex", "Grand Collector",
                "Catch every species Gold, Silver and Crystal can give between them (231)", 50,
                "Pokédex caught flags for all 251 but #1-#9, #138-#142, #144-#146, #150, #151 and #251",
                ownsAll(GSC_OBTAINABLE));

        // Legends.
        add("three-beasts", "Legends", "Three Beasts", "Own Raikou, Entei and Suicune", 25,
                "Pokédex caught flags #243, #244, #245", ownsAll(243, 244, 245));
        add("shining-legend", "Legends", "Shining Legend", "Catch a shiny Raikou, Entei, Suicune, Lugia or Ho-Oh", 50,
                "an own-caught shiny #243-#245, #249 or #250 in the party",
                s -> s.party().stream().anyMatch(m -> m.shiny() && m.ownCatch()
                        && Arrays.stream(LEGENDARIES).anyMatch(d -> d == m.species())));

        // Training.
        add("growing-up", "Training", "Growing Up", "Any Pokémon reaches Lv 30", 2, "a party member at Lv 30+",
                s -> s.party().stream().anyMatch(m -> m.level() >= 30));
        when("first-evolution", "Training", "First Evolution", "Evolve a Pokémon", 2,
                "a party slot changes into its evolution, same OT and DVs", m -> !evolved(m.before(), m.now()).isEmpty());
        add("best-friends", "Training", "Best Friends", "A party Pokémon at maximum friendship", 5,
                "a party member's happiness = 255", s -> hatchedSlots(s).anyMatch(x -> x.happiness() >= MAX_HAPPINESS));
        add("perfect-specimen", "Training", "Perfect Specimen", "Own a Pokémon with perfect DVs (15/15/15/15)", 50,
                "a party member with Attack, Defense, Speed and Special DVs all 15 (HP DV follows)",
                s -> hatchedSlots(s).anyMatch(x -> x.dvs() == PERFECT_DVS));

        // Items.
        add("on-two-wheels", "Items", "On Two Wheels", "Get the Bicycle", 1, "BICYCLE among the key items",
                s -> s.items().contains(BICYCLE));
        add("angler", "Items", "Angler", "Hold the Old, Good and Super Rods", 5,
                "OLD_ROD, GOOD_ROD and SUPER_ROD among the key items",
                s -> s.items().containsAll(Set.of(OLD_ROD, GOOD_ROD, SUPER_ROD)));
        add("tm-hoarder", "Items", "TM Hoarder", "Hold 25 different TMs at once", 5, "25 TM bits set in wTMsHMs",
                s -> s.tms() >= TM_HOARDER);
        add("moms-savings", "Items", "Mom's Savings", "Mom holds ₽100,000 for you", 5,
                "wMomsMoney at 100,000 or more", s -> s.momsMoney() >= MOMS_SAVINGS);
        add("kurts-collection", "Items", "Kurt's Collection",
                "Hold all 7 Apricorn balls (Level, Lure, Moon, Friend, Fast, Heavy, Love)", 25, "all seven in the bag or PC",
                s -> Arrays.stream(APRICORN_BALLS).allMatch(s.items()::contains));
        add("millionaire", "Items", "Millionaire", "Hold ₽999,999", 50, "wMoney = 999,999",
                s -> s.money() >= MAX_MONEY);

        // Side.
        add("phone-pal", "Side", "Phone Pal", "Register a trainer's phone number", 1, "any trainer contact in wPhoneList",
                s -> s.contacts().stream().anyMatch(id -> id > PHONE_ELM));
        when("first-hatch", "Side", "First Hatch", "Hatch an egg", 2,
                "a party egg seen hatching (same check as Breeder)", m -> m.log().hatched() > 0);
        add("ruins-explorer", "Side", "Ruins Explorer", "Solve a puzzle at the Ruins of Alph", 3,
                "any bit of wUnlockedUnowns", s -> (s.unownPuzzles() & ALL_UNOWN_PUZZLES) != 0);
        add("puzzle-master", "Side", "Puzzle Master", "Solve all four Ruins of Alph puzzles", 5,
                "all four wUnlockedUnowns bits", s -> (s.unownPuzzles() & ALL_UNOWN_PUZZLES) == ALL_UNOWN_PUZZLES);
        add("friday-lapras", "Side", "Friday Lapras", "Catch Lapras", 5, "Pokédex caught flag #131", ownsAll(131));
        add("karate-king", "Side", "Karate King", "Beat Kiyo in Mt. Mortar and receive Tyrogue", 5,
                "event flag EVENT_GOT_TYROGUE_FROM_KIYO", flag(EVENT_GOT_TYROGUE_FROM_KIYO));
        add("week-of-siblings", "Side", "Week of Siblings", "Get the gift from each of the 7 weekday siblings, Monica to Sunny",
                25, "the seven EVENT_GOT_<item>_FROM_<sibling> flags",
                s -> Arrays.stream(SIBLING_GIFTS).allMatch(s.flags()::contains));
        add("dragons-blessing", "Side", "Dragon's Blessing", "Own the Dragon's Den Dratini that knows ExtremeSpeed (Crystal)",
                25, "a Dratini, Dragonair or Dragonite in the party that knows ExtremeSpeed",
                s -> hatchedSlots(s).anyMatch(x -> x.extremeSpeed() && x.species() >= DRATINI && x.species() <= DRAGONITE));
        add("pokerus", "Side", "Pokérus", "Have a Pokémon with Pokérus", 50,
                "a party member's Pokérus byte is non-zero (infected or cured)", s -> hatchedSlots(s).anyMatch(x -> x.pokerus() != 0));

        // Challenge.
        when("lean-team", "Challenge", "Lean Team", "Become Champion with no Pokémon above Lv 45", 10,
                "at the Hall of Fame no party member is above Lv 45",
                atHallOfFame(s -> !s.party().isEmpty() && s.party().stream().allMatch(m -> m.level() <= 45)));
        when("lone-wolf", "Challenge", "Lone Wolf", "Defeat Red with only one Pokémon in the party", 50,
                "at Red's defeat the party count is 1", atRed(s -> s.partyCount() == 1 && s.party().size() == 1));

        // Link.
        when("raised-by-a-friend", "Link", "Raised by a Friend", "Raise a Pokémon you got in a trade to Lv 50", 10,
                "a party member with another trainer's OT at Lv 50+ (an in-game trade's counts too)",
                m -> hatchedSlots(m.now()).anyMatch(x -> x.ot() != m.now().playerId() && x.level() >= 50));
        when("trader", "Link", "Trader", "Receive 10 Pokémon in link trades", 10,
                "10 trades seen (same check as First Trade)", m -> m.log().trades() >= TRADER);
        when("colosseum-regular", "Link", "Colosseum Regular", "Win 10 link battles", 25,
                "sLinkBattleWins of the battery save at 10 or more", m -> m.log().linkWins() >= COLOSSEUM_REGULAR);
    }

    private static boolean monoType(Gen2Save s) {
        if (s.party().isEmpty() || s.party().size() != s.partyCount()) return false;
        var first = s.party().getFirst();
        return IntStream.of(first.type1(), first.type2()).filter(t -> t >= 0).anyMatch(t ->
                s.party().stream().allMatch(m -> m.type1() == t || m.type2() == t));
    }

    /** The whole catalogue, locked. */
    public static List<Achievement> catalogue() {
        return RULES.values().stream().map(Rule::achievement).toList();
    }

    /** Points of an achievement id; 0 when unknown. */
    public static int points(String id) {
        Rule r = RULES.get(id);
        return r == null ? 0 : r.achievement().points();
    }

    /** Ids of every achievement this moment earns. */
    static List<String> earned(Moment m) {
        List<String> out = new ArrayList<>();
        RULES.forEach((id, r) -> {
            if (r.earned().test(m)) out.add(id);
        });
        return out;
    }

    /** Link achievements told from the TrainerLog alone (no save needed): unlocked for both Pibs as the cable goes in. */
    static final List<String> FROM_LOG = List.of("plugged-in", "social-butterfly", "first-trade", "cross-version",
            "link-champion", "trader", "colosseum-regular");

    /** The {@link #FROM_LOG} achievements this log earns. */
    static List<String> earnedFromLog(TrainerLog log) {
        Moment m = new Moment(null, null, log, null);
        return FROM_LOG.stream().filter(id -> RULES.get(id).earned().test(m)).toList();
    }

    /** {@link #earned(Moment)} with nothing logged and no battery save read. */
    static List<String> earned(Gen2Save before, Gen2Save now) {
        return earned(new Moment(before, now, TrainerLog.EMPTY, null));
    }

    static Set<Integer> phoneNumbers(boolean crystal) {
        if (!crystal) return PHONE_NUMBERS;
        Set<Integer> all = new HashSet<>(PHONE_NUMBERS);
        all.add(BUENA);
        return all;
    }

    /** The party slots that were an egg in before and are that Pokémon (species, OT, DVs) hatched now. */
    static List<Slot> hatched(Gen2Save before, Gen2Save now) {
        if (before == null) return List.of();
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < Math.min(before.slots().size(), now.slots().size()); i++) {
            Slot b = before.slots().get(i), n = now.slots().get(i);
            if (b.egg() && !n.egg() && b.species() == n.species() && b.ot() == n.ot() && b.dvs() == n.dvs()) out.add(n);
        }
        return out;
    }

    /** The party slots that were a Pokémon in before and are its evolution (same OT and DVs, another species) now. */
    static List<Slot> evolved(Gen2Save before, Gen2Save now) {
        if (before == null) return List.of();
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < Math.min(before.slots().size(), now.slots().size()); i++) {
            Slot b = before.slots().get(i), n = now.slots().get(i);
            if (!b.egg() && !n.egg() && b.species() != n.species() && b.ot() == n.ot() && b.dvs() == n.dvs()) out.add(n);
        }
        return out;
    }

    /**
     * The log after this confirmed reading. partner: who is at the other end of the link cable (null: nobody);
     * otherVersion: they play another version; sram: a battery save read since the last reading, or null.
     * Trades are counted as Pokémon (by OT and DVs, which a trade evolution keeps) that join the party while the
     * player stays in a trade room. The Elite Four run starts clean in Will's room, turns "fainted" when a party
     * member is seen at 0 HP on those maps, lasts through the Hall of Fame and ends anywhere else.
     */
    static TrainerLog log(TrainerLog log, Gen2Save before, Gen2Save now, String partner, boolean otherVersion,
                          Sram sram) {
        Set<Integer> contacts = new HashSet<>(log.contacts());
        contacts.addAll(now.contacts());
        Set<String> partners = new HashSet<>(log.partners());
        if (partner != null) partners.add(partner);
        int received = 0;
        String map = now.map() == null ? "" : now.map(); // Set.of(...).contains(null) throws
        if (before != null && TRADE_ROOMS.contains(map) && before.map() != null && TRADE_ROOMS.contains(before.map())) {
            Set<List<Integer>> had = new HashSet<>();
            before.slots().forEach(x -> had.add(List.of(x.ot(), x.dvs())));
            for (Slot x : now.slots()) if (had.add(List.of(x.ot(), x.dvs()))) received++;
        }
        String e4 = log.eliteFour();
        boolean hallOfFame = before != null && now.hallOfFame() > before.hallOfFame();
        if (ELITE_FOUR.contains(map)) {
            if (e4 == null && "WILLS_ROOM".equals(map)) e4 = "clean";
            if (e4 != null && now.slots().stream().anyMatch(x -> !x.egg() && x.hp() == 0)) e4 = "fainted";
        } else if (!"HALL_OF_FAME".equals(map) && !hallOfFame) {
            e4 = null;
        }
        Set<String> shinies = new HashSet<>(log.shinies());
        now.slots().stream().filter(x -> x.shiny() && !x.egg()).forEach(x -> shinies.add(x.ot() + ":" + x.dvs()));
        return new TrainerLog(Set.copyOf(contacts), log.hatched() + hatched(before, now).size(), Set.copyOf(partners),
                log.trades() + received, log.crossVersion() || received > 0 && otherVersion, e4,
                sram == null ? log.linkWins() : sram.linkWins(), sram == null ? log.linkLosses() : sram.linkLosses(),
                Set.copyOf(shinies));
    }
}
