package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pppp.model.Achievement;
import dev.pppp.model.TrainerScore;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Gen2AchievementsTest {
    static BitSet dex(int... n) {
        BitSet b = new BitSet();
        for (int i : n) b.set(i);
        return b;
    }

    /** The same save in each layout. */
    static Gen2Ram sample(Gen2Ram.At at) {
        var g = new Gen2Ram(at);
        g.type(130, Gen2Ram.WATER, Gen2Ram.FLYING);
        g.mon(0, 130, 30, Gen2Ram.PLAYER_ID, 0x2A, 0xAA); // the red Gyarados: attack 2, the rest 10
        g.egg(1, 175);
        g.mon(2, 155, 21, 0x4321); // a traded Cyndaquil
        g.caught(130, 155);
        g.seen(16);
        g.badges(0b1001, 0b10);
        g.hallOfFame(2);
        g.event(26, true);
        g.event(124, true);
        g.event(68, true);
        g.event(1890, true);
        g.items(Gen2Save.FIRE_STONE, Gen2Save.SUN_STONE);
        g.keyItems(Gen2Save.BICYCLE, Gen2Save.SQUIRTBOTTLE);
        g.balls(Gen2Save.MASTER_BALL);
        g.pc(Gen2Save.MOON_STONE);
        g.hms(3);
        g.coins(1234);
        g.unown(1, 2, 3, 2);
        g.phone(1, 0, 5);
        g.hours(12);
        return g;
    }

    @Test
    void goldAndCrystalReadTheSameSave() {
        var expected = new Gen2Save(Gen2Ram.PLAYER_ID, 0b1001, 0b10, 2, true, true, false, true, dex(130, 155),
                dex(16, 130, 155), 3, List.of(
                        new Gen2Save.Mon(130, 30, true, true, Gen2Ram.WATER, Gen2Ram.FLYING),
                        new Gen2Save.Mon(155, 21, false, false, Gen2Ram.NORMAL, Gen2Ram.NORMAL)),
                Set.of(Gen2Save.FIRE_STONE, Gen2Save.SUN_STONE, Gen2Save.BICYCLE, Gen2Save.SQUIRTBOTTLE,
                        Gen2Save.MASTER_BALL, Gen2Save.MOON_STONE), 3, 1234, 3, 2, 12);
        for (Gen2Ram.At at : new Gen2Ram.At[] {Gen2Ram.GOLD, Gen2Ram.CRYSTAL}) {
            var g = sample(at);
            assertThat(g.profile().read(g.ram())).as(at.header()).isEqualTo(expected);
        }
    }

    @Test
    void readingsThatAreNotASaveCountForNothing() {
        var g = sample(Gen2Ram.CRYSTAL);
        var profile = g.profile();
        assertThat(profile.read(g.ram())).isNotNull();

        g.set(Gen2Ram.CRYSTAL.numKeyItems() + 3, 0); // key items list without its -1
        assertThat(profile.read(g.ram())).isNull();
        g = sample(Gen2Ram.CRYSTAL);
        g.coins(10_000);
        assertThat(profile.read(g.ram())).isNull();
        g = sample(Gen2Ram.CRYSTAL);
        g.set(Gen2Ram.CRYSTAL.party() + 8, 131); // the first member's struct disagrees with the species list
        assertThat(profile.read(g.ram())).isNull();
        g = sample(Gen2Ram.CRYSTAL);
        g.set(Gen2Ram.CRYSTAL.party() + 8 + 0x1F, 101);
        assertThat(profile.read(g.ram())).isNull();
    }

    @Test
    void shininessFollowsCheckShininess() {
        assertThat(PokemonGen2.shiny(0x2A, 0xAA)).isTrue();
        assertThat(PokemonGen2.shiny(0xFA, 0xAA)).isTrue(); // attack 15
        assertThat(PokemonGen2.shiny(0x1A, 0xAA)).isFalse(); // attack 1
        assertThat(PokemonGen2.shiny(0x2B, 0xAA)).isFalse();
        assertThat(PokemonGen2.shiny(0x2A, 0xA9)).isFalse();
    }

    @Test
    void titlesAndGradesChangeAtTheDesignsBoundaries() {
        int[][] edges = {{0, 99}, {100, 199}, {200, 299}, {300, 449}, {450, 599}, {600, 799}, {800, 919}, {920, 1000}};
        String[] titles = {"Youngster F", "Bug Catcher E", "Ace Trainer D", "Gym Leader C", "Elite Four B",
                "Champion A", "Mt. Silver Legend S", "Pokémon Master S+"};
        for (int i = 0; i < edges.length; i++) {
            for (int score : edges[i]) {
                TrainerScore t = TrainerScoring.titled(score);
                assertThat(t.title() + " " + t.grade()).as("score " + score).isEqualTo(titles[i]);
            }
        }
    }

    @Test
    void powerRatingsAreTheDesignsTable() {
        assertThat(IntStream.rangeClosed(1, 251).map(PowerRating::of).sum()).isEqualTo(6220);
        assertThat(IntStream.rangeClosed(1, 251).filter(d -> d != 151 && d != 251).map(PowerRating::of).sum())
                .isEqualTo(PowerRating.TOTAL_WITHOUT_MYTHICALS);
        assertThat(IntStream.rangeClosed(1, 251).map(PowerRating::of)).allMatch(pr -> pr > 0);
        assertThat(PowerRating.of(150)).isEqualTo(100);
        assertThat(PowerRating.of(157)).isEqualTo(45); // Typhlosion: a Lv 50 one is worth 22 of team strength
        assertThat(PowerRating.of(129)).isEqualTo(5);
    }

    @Test
    void aFullFileScoresWhatTheRamCanTell() {
        BitSet all = new BitSet();
        all.set(1, 252);
        List<Gen2Save.Mon> legends = IntStream.of(150, 249, 250, 151, 251, 144)
                .mapToObj(d -> new Gen2Save.Mon(d, 100, d == 144, true, 0, 0)).toList();
        Set<Integer> items = Set.of(Gen2Save.MASTER_BALL, Gen2Save.RAINBOW_WING, Gen2Save.SILVER_WING,
                Gen2Save.BICYCLE, Gen2Save.SQUIRTBOTTLE, Gen2Save.CARD_KEY, Gen2Save.FIRE_STONE, Gen2Save.WATER_STONE,
                Gen2Save.THUNDERSTONE, Gen2Save.LEAF_STONE, Gen2Save.MOON_STONE, Gen2Save.SUN_STONE, Gen2Save.COIN_CASE);
        var save = new Gen2Save(1, 0xFF, 0xFF, 1, true, true, true, true, all, all, 6, legends, items, 7, 9999, 26, 10, 99);
        // Side tops out at 26 + 15 + 14: the contest, eggs and the Battle Tower cannot be read.
        assertThat(TrainerScoring.score(save)).isEqualTo(
                new TrainerScore(200 + 250 + 150 + 142 + 100 + 55 + 30, "Pokémon Master", "S+", 200, 250, 150, 142, 100,
                        55, 30));
        assertThat(Achievements.earned(null, save)).contains("250-club", "the-time-traveller", "full-house",
                "wingspan", "full-kit", "stone-collector", "unown-scholar", "high-roller", "lucky-star", "back-to-kanto");
    }

    @Test
    void theCatalogueIsTheDesigns() {
        List<Achievement> all = Achievements.catalogue();
        assertThat(all).hasSize(52);
        assertThat(all).extracting(Achievement::id).doesNotHaveDuplicates();
        assertThat(all).extracting(Achievement::category).containsOnly("Story", "Pokédex", "Legends", "Training",
                "Items", "Side", "Challenge");
        assertThat(all).allMatch(a -> a.note() != null && !a.note().isBlank() && a.unlockedAt() == null);
        assertThat(all.stream().mapToInt(Achievement::points).sum()).isEqualTo(902); // "about 900"
        assertThat(all).filteredOn(a -> !a.implemented()).extracting(Achievement::id).containsExactlyInAnyOrder(
                "johto-complete", "living-history", "bug-champion", "breeder", "odd-one", "tower-climber",
                "card-collector", "untouchable", "no-shopping");
    }
}
