package dev.pppp.service.game;

import static dev.pppp.service.game.Gen2Save.*;

import dev.pppp.model.TrainerScore;
import java.util.Comparator;

/**
 * The Trainer Score of a save, as the Achievements & Trainer Score design's "Trainer Score formula", with integer
 * maths (each part rounded down). What the RAM cannot tell yet scores 0: the Bug-Catching Contest, eggs hatched,
 * Battle Tower / Trainer House wins (side content, so it tops out at 55 of 100), and boxed Pokémon (team strength
 * and shinies count the party only).
 */
final class TrainerScoring {
    private TrainerScoring() {}

    /** Lowest score of each title, best first (design, "Titles, grades and worked examples"). */
    private static final int[] FLOOR = {920, 800, 600, 450, 300, 200, 100, 0};
    private static final String[] TITLE = {"Pokémon Master", "Mt. Silver Legend", "Champion", "Elite Four",
            "Gym Leader", "Ace Trainer", "Bug Catcher", "Youngster"};
    private static final String[] GRADE = {"S+", "S", "A", "B", "C", "D", "E", "F"};

    static TrainerScore score(Gen2Save s) {
        // Story (200): Johto badges 10 each, Hall of Fame 40, Kanto badges 5 each, Red 40.
        int story = 10 * Integer.bitCount(s.johtoBadges()) + (s.hallOfFame() > 0 ? 40 : 0)
                + 5 * Integer.bitCount(s.kantoBadges()) + (s.redBeaten() ? 40 : 0);
        // Pokédex (250): 0.9 per species caught + 0.1 per species seen (seen includes caught).
        int pokedex = Math.min(250, (9 * s.caught().cardinality() + s.seen().cardinality()) / 10);
        // Collection (150): PR of every species caught / 6020 * 150; Mew and Celebi are a bonus over 6020.
        int pr = s.caught().stream().map(PowerRating::of).sum();
        int collection = Math.min(150, pr * 150 / PowerRating.TOTAL_WITHOUT_MYTHICALS);
        // Team (150): the best six by PR x level / 100, summed, / 600 * 150. The party is all we can read.
        int strength = s.party().stream().map(m -> PowerRating.of(m.species()) * m.level())
                .sorted(Comparator.reverseOrder()).limit(6).mapToInt(Integer::intValue).sum(); // x100
        int team = Math.min(150, strength * 150 / 60_000);
        int keyItems = keyItems(s);
        // Side (100): Unown forms 1 each (26); a full phone list (10 numbers) 15; coins up to 14 at 9,999.
        int side = Math.min(100, s.unownForms() + Math.min(15, s.phoneContacts() * 3 / 2) + s.coins() * 14 / 9999);
        // Shinies and rarities (50): 10 per shiny (max 30), Mew 10, Celebi 10.
        int shinies = (int) s.party().stream().filter(Gen2Save.Mon::shiny).count();
        int rarities = Math.min(30, 10 * shinies) + (s.owns(MEW) ? 10 : 0) + (s.owns(CELEBI) ? 10 : 0);
        int total = story + pokedex + collection + team + keyItems + side + rarities;
        int t = title(total);
        return new TrainerScore(total, TITLE[t], GRADE[t], story, pokedex, collection, team, keyItems, side, rarities);
    }

    /** Key items (100): Master Ball 10 (+15 while still held after the Hall of Fame), 7 HMs x 3, both Wings 10
     * each, Bicycle 4, Squirtbottle/SecretPotion 5, Card Key/Clear Bell 5, 6 stones x 3, Coin Case 2. */
    private static int keyItems(Gen2Save s) {
        var held = s.items();
        boolean masterBall = held.contains(MASTER_BALL);
        int points = (s.gotMasterBall() || masterBall ? 10 : 0) + (masterBall && s.hallOfFame() > 0 ? 15 : 0)
                + 3 * s.hms()
                + (held.contains(RAINBOW_WING) ? 10 : 0) + (held.contains(SILVER_WING) ? 10 : 0)
                + (held.contains(BICYCLE) ? 4 : 0)
                + (held.contains(SQUIRTBOTTLE) || held.contains(SECRETPOTION) ? 5 : 0)
                + (held.contains(CARD_KEY) || held.contains(CLEAR_BELL) ? 5 : 0)
                + (held.contains(COIN_CASE) ? 2 : 0);
        for (int stone : STONES) if (held.contains(stone)) points += 3;
        return points;
    }

    /**
     * Earned points stay earned: each part at the best it has ever been (was: the stored score, ZERO at first), the
     * total their sum, the title from that. Trading, boxing or losing a Pokémon never takes points back.
     */
    static TrainerScore best(TrainerScore was, TrainerScore now) {
        if (was == null) return now;
        int story = Math.max(was.story(), now.story()), pokedex = Math.max(was.pokedex(), now.pokedex()),
                collection = Math.max(was.collection(), now.collection()), team = Math.max(was.team(), now.team()),
                keyItems = Math.max(was.keyItems(), now.keyItems()), side = Math.max(was.side(), now.side()),
                rarities = Math.max(was.rarities(), now.rarities());
        int total = story + pokedex + collection + team + keyItems + side + rarities;
        int t = title(total);
        return new TrainerScore(total, TITLE[t], GRADE[t], story, pokedex, collection, team, keyItems, side, rarities);
    }

    /** Index into the title table for a score. */
    private static int title(int score) {
        int t = 0;
        while (score < FLOOR[t]) t++;
        return t;
    }

    static TrainerScore titled(int score) { // for tests of the boundaries
        int t = title(score);
        return new TrainerScore(score, TITLE[t], GRADE[t], 0, 0, 0, 0, 0, 0, 0);
    }
}
