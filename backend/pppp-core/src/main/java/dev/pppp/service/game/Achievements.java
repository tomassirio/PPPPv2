package dev.pppp.service.game;

import static dev.pppp.service.game.Gen2Save.*;

import dev.pppp.model.Achievement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * The achievement catalogue of the Achievements & Trainer Score design (52 entries, 7 categories) and how each is
 * told from a {@link Gen2Save}. Most read the save as it is now. "At the Hall of Fame" and "at Red" ones look at
 * the party on the first confirmed reading where the Hall of Fame count leaves 0, or Red leaves Mt. Silver, after
 * a reading where it had not: so they need the tracker to see that moment, and are missed (never guessed) when a
 * restart falls in between. Entries the RAM cannot tell yet are listed with the reason and never unlock.
 */
public final class Achievements {
    private Achievements() {}

    private record Rule(Achievement achievement, BiPredicate<Gen2Save, Gen2Save> earned) {}

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();

    /** Reads the save as it is now. */
    private static void add(String id, String category, String name, String description, int points, String how,
                            Predicate<Gen2Save> now) {
        add(id, category, name, description, points, how, (before, s) -> now.test(s));
    }

    /** earned(before, now): before is the previous confirmed reading of this console run, or null. */
    private static void add(String id, String category, String name, String description, int points, String how,
                            BiPredicate<Gen2Save, Gen2Save> earned) {
        RULES.put(id, new Rule(new Achievement(id, category, name, description, points, true, how, null), earned));
    }

    private static void todo(String id, String category, String name, String description, int points, String why) {
        RULES.put(id, new Rule(new Achievement(id, category, name, description, points, false, why, null), null));
    }

    private static BiPredicate<Gen2Save, Gen2Save> atHallOfFame(Predicate<Gen2Save> party) {
        return (before, s) -> before != null && before.hallOfFame() == 0 && s.hallOfFame() > 0 && party.test(s);
    }

    private static BiPredicate<Gen2Save, Gen2Save> atRed(Predicate<Gen2Save> party) {
        return (before, s) -> before != null && !before.redBeaten() && s.redBeaten() && party.test(s);
    }

    private static Predicate<Gen2Save> badge(int johtoBit) {
        return s -> (s.johtoBadges() & 1 << johtoBit) != 0;
    }

    private static Predicate<Gen2Save> ownsAll(int... dex) {
        return s -> Arrays.stream(dex).allMatch(s::owns);
    }

    private static final String NO_BOXES = "needs the PC boxes, which live in cartridge RAM (not read)";

    static {
        // Story. Badge bits: wJohtoBadges in the order of pret constants/engine_flags.asm (ZEPHYRBADGE .. RISINGBADGE).
        add("new-bark-beginnings", "Story", "New Bark Beginnings", "Receive a starter from Prof. Elm", 1,
                "event flag EVENT_GOT_A_POKEMON_FROM_ELM", Gen2Save::gotStarter);
        add("zephyr-badge", "Story", "Zephyr Badge", "Defeat Falkner", 3, "Zephyr Badge bit", badge(0));
        add("hive-badge", "Story", "Hive Badge", "Defeat Bugsy", 3, "Hive Badge bit", badge(1));
        add("plain-badge", "Story", "Plain Badge", "Defeat Whitney", 5, "Plain Badge bit", badge(2));
        add("fog-badge", "Story", "Fog Badge", "Defeat Morty", 5, "Fog Badge bit", badge(3));
        add("storm-badge", "Story", "Storm Badge", "Defeat Chuck", 5, "Storm Badge bit", badge(5));
        add("mineral-badge", "Story", "Mineral Badge", "Defeat Jasmine", 5, "Mineral Badge bit", badge(4));
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
        todo("johto-complete", "Pokédex", "Johto Complete", "Catch all species in the Johto Dex (New Pokédex)", 25,
                "the New Pokédex lists all 251 species (pret data/pokemon/dex_order_new.asm), so it needs a definition"
                        + " of the Johto set first");
        add("250-club", "Pokédex", "250 Club", "Catch every species except Celebi", 50,
                "Pokédex caught flags #1-#250", s -> s.caught().get(1, 251).cardinality() == 250);
        todo("living-history", "Pokédex", "Living History", "Own all 251 at once (in boxes)", 50, NO_BOXES);

        // Legends: the Pokédex caught flag (also set by a trade or an evolution).
        add("tin-tower-guardian", "Legends", "Tin Tower Guardian", "Catch Ho-Oh", 25, "Pokédex caught flag #250",
                ownsAll(250));
        add("whirl-islands-guardian", "Legends", "Whirl Islands Guardian", "Catch Lugia", 25,
                "Pokédex caught flag #249", ownsAll(249));
        add("thunder-chaser", "Legends", "Thunder Chaser", "Catch Raikou", 10, "Pokédex caught flag #243",
                ownsAll(243));
        add("fire-chaser", "Legends", "Fire Chaser", "Catch Entei", 10, "Pokédex caught flag #244", ownsAll(244));
        add("north-wind", "Legends", "North Wind", "Catch Suicune", 10, "Pokédex caught flag #245", ownsAll(245));
        add("kantos-strongest", "Legends", "Kanto's Strongest", "Own Mewtwo (trade-only in GSC)", 25,
                "Pokédex caught flag #150", ownsAll(MEWTWO));
        add("the-time-traveller", "Legends", "The Time Traveller", "Own Celebi (GS Ball event)", 50,
                "Pokédex caught flag #251", ownsAll(CELEBI));

        // Training.
        add("level-up", "Training", "Level Up", "Any Pokémon reaches Lv 50", 5, "a party member at Lv 50+",
                s -> s.party().stream().anyMatch(m -> m.level() >= 50));
        add("centurion", "Training", "Centurion", "Any Pokémon reaches Lv 100", 25, "a party member at Lv 100",
                s -> s.party().stream().anyMatch(m -> m.level() >= 100));
        add("full-house", "Training", "Full House", "Six Pokémon at Lv 100 in the party", 50,
                "six party members at Lv 100", s -> s.party().stream().filter(m -> m.level() >= 100).count() == 6);
        add("solo-run", "Training", "Solo Run", "Become Champion using only your starter (missable)", 25,
                "at the Hall of Fame the party is one own-caught Chikorita-to-Feraligatr (the run before is not seen)",
                atHallOfFame(s -> s.partyCount() == 1 && s.party().size() == 1
                        && s.party().getFirst().ownCatch()
                        && s.party().getFirst().species() >= CHIKORITA && s.party().getFirst().species() <= FERALIGATR));
        add("rattata-rules", "Training", "Rattata Rules", "Defeat Red with a Raticate or Furret in the party", 10,
                "at Red's defeat the party holds a Raticate or Furret",
                atRed(s -> s.party().stream().anyMatch(m -> m.species() == RATICATE || m.species() == FURRET)));

        // Items.
        add("silphs-finest", "Items", "Silph's Finest", "Obtain the Master Ball", 5,
                "EVENT_GOT_MASTER_BALL_FROM_ELM, or a Master Ball in the bag or PC",
                s -> s.gotMasterBall() || s.items().contains(MASTER_BALL));
        add("self-control", "Items", "Self-Control", "Defeat Lance with the Master Ball unused", 10,
                "at the Hall of Fame a Master Ball is in the bag or PC",
                atHallOfFame(s -> s.items().contains(MASTER_BALL)));
        add("wingspan", "Items", "Wingspan", "Hold both the Rainbow Wing and Silver Wing", 10,
                "RAINBOW_WING and SILVER_WING among the key items",
                s -> s.items().contains(RAINBOW_WING) && s.items().contains(SILVER_WING));
        add("full-kit", "Items", "Full Kit", "Own all 7 HMs", 10, "HM01-HM07 in wTMsHMs", s -> s.hms() == 7);
        add("stone-collector", "Items", "Stone Collector",
                "Hold all 6 evolution stones (Fire, Water, Thunder, Leaf, Moon, Sun)", 5, "all six in the bag or PC",
                s -> Arrays.stream(STONES).allMatch(s.items()::contains));

        // Side.
        todo("bug-champion", "Side", "Bug Champion", "Win the Bug-Catching Contest", 5,
                "no flag records a win; the result exists only during the judging script");
        add("unown-scholar", "Side", "Unown Scholar", "Register all 26 Unown forms", 25, "26 forms in wUnownDex",
                s -> s.unownForms() == 26);
        add("red-gyarados", "Side", "Red Gyarados", "Catch the shiny Gyarados at Lake of Rage (missable if fainted)",
                10, "an own-caught shiny Gyarados in the party (any shiny Gyarados of the player's counts)",
                s -> s.party().stream().anyMatch(m -> m.shiny() && m.ownCatch() && m.species() == GYARADOS));
        add("lucky-star", "Side", "Lucky Star", "Catch any other shiny Pokémon", 25,
                "an own-caught shiny other than Gyarados in the party (a hatched one counts)",
                s -> s.party().stream().anyMatch(m -> m.shiny() && m.ownCatch() && m.species() != GYARADOS));
        todo("breeder", "Side", "Breeder", "Hatch 10 eggs", 5, "the game keeps no count of eggs hatched");
        todo("odd-one", "Side", "Odd One", "Hatch a shiny from the Odd Egg (Crystal)", 25,
                "a hatched shiny baby cannot be told apart from a bred one");
        todo("tower-climber", "Side", "Tower Climber", "Win 7 straight at the Battle Tower (Crystal)", 10,
                "the Battle Tower record lives in cartridge RAM (not read)");
        todo("card-collector", "Side", "Card Collector", "Register every Pokégear phone number available", 10,
                "the Pokégear holds 10 numbers at once (pret CONTACT_LIST_SIZE), so it needs a history of numbers");
        add("high-roller", "Side", "High Roller", "Hold 9,999 coins at the Game Corner", 5, "wCoins = 9,999",
                s -> s.coins() >= 9999);
        add("fossil-hunter", "Side", "Fossil Hunter", "Own Aerodactyl, Kabutops and Omastar", 10,
                "Pokédex caught flags #142, #141, #139", ownsAll(142, 141, 139));
        add("old-friends", "Side", "Old Friends", "Own all three Kanto starters' final forms", 10,
                "Pokédex caught flags #3, #6, #9", ownsAll(3, 6, 9));
        add("johto-friends", "Side", "Johto Friends", "Own all three Johto starters' final forms", 10,
                "Pokédex caught flags #154, #157, #160", ownsAll(154, 157, 160));

        // Challenge.
        todo("untouchable", "Challenge", "Untouchable", "Beat the Elite Four with no Pokémon fainting (missable)", 25,
                "depends on battle state across five battles, which is not tracked");
        todo("no-shopping", "Challenge", "No Shopping", "Become Champion without buying from a Poké Mart (missable)",
                25, "the game keeps no record of purchases");
        add("speedrunner", "Challenge", "Speedrunner", "Enter the Hall of Fame under 6:00 play time", 25,
                "at the Hall of Fame wGameTimeHours is under 6", atHallOfFame(s -> s.playHours() < 6));
        add("low-level", "Challenge", "Low Level", "Defeat Red with no Pokémon above Lv 60", 50,
                "at Red's defeat no party member is above Lv 60",
                atRed(s -> !s.party().isEmpty() && s.party().stream().allMatch(m -> m.level() <= 60)));
        add("mono-type", "Challenge", "Mono-Type", "Become Champion with a party sharing one type", 25,
                "at the Hall of Fame every party member shares a type (types from the ROM's base data)",
                atHallOfFame(Achievements::monoType));
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

    /** Ids of every implemented achievement this reading earns. before: the previous confirmed one, or null. */
    static List<String> earned(Gen2Save before, Gen2Save now) {
        List<String> out = new ArrayList<>();
        RULES.forEach((id, r) -> {
            if (r.earned() != null && r.earned().test(before, now)) out.add(id);
        });
        return out;
    }
}
