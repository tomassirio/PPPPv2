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
    /** 85 achievements after the review: the design's, less six Gen 1-only ones, plus 36. */
    static final int TOTAL_POINTS = 1247;

    @Test
    void pokedexEntriesComeFromTheRom() {
        var g = new Gen2Ram(Gen2Ram.GOLD);
        g.type(1, 0x16, 0x03); // grass, poison
        int table = 0x11 * 0x4000 + 0x0360, entry = 0x68 * 0x4000; // PokedexDataPointerTable; #1 in bank $68 at $4000
        g.rom[table] = 0x00;
        g.rom[table + 1] = 0x40;
        var b = new java.io.ByteArrayOutputStream();
        b.writeBytes(PokemonRedTest.encode("SEED")); // category, "@"
        b.writeBytes(new byte[] {(byte) 204, 0, (byte) 150, 0}); // 2'04", 15.0 lb
        b.writeBytes(java.util.Arrays.copyOf(PokemonRedTest.encode("ANGER"), 5));
        b.writeBytes(new byte[] {(byte) 0xE3, 0x4E}); // "-", <NEXT>
        b.writeBytes(PokemonRedTest.encode("ED")); // page 1 ends
        b.writeBytes(PokemonRedTest.encode("LUGIA"));
        System.arraycopy(b.toByteArray(), 0, g.rom, entry, b.size());
        var dex = g.profile().dex(1);
        assertThat(dex.category()).isEqualTo("Seed");
        assertThat(dex.types()).containsExactly("grass", "poison");
        assertThat(List.of(dex.heightFeet(), dex.heightInches(), dex.weightTenths())).containsExactly(2, 4, 150);
        assertThat(dex.text()).isEqualTo("Angered Lugia");
        assertThat(g.profile().dex(0)).isNull();
        assertThat(g.profile().playerSprite()).isNotNull(); // the walking sheet, as on the sign-in map
        assertThat(g.profile().dex(252)).isNull();
    }

    @Test
    void theReviewsAdditionsReadTheirBytes() {
        for (var at : List.of(Gen2Ram.GOLD, Gen2Ram.CRYSTAL)) {
            boolean crystal = at == Gen2Ram.CRYSTAL;
            var g = new Gen2Ram(at);
            g.put(crystal ? 0xD84E : 0xD573, (byte) 0x0F, (byte) 0x42, (byte) 0x3F); // wMoney 999,999
            g.put(crystal ? 0xD851 : 0xD576, (byte) 0x01, (byte) 0x86, (byte) 0xA0); // wMomsMoney 100,000
            g.set(crystal ? 0xDEF3 : 0xDC3E, 0b1111); // wUnlockedUnowns: all four puzzles
            for (int i = 0; i < 25; i++) g.set(at.tmsHms() + i, 1); // TM01-TM25
            for (int flag : Gen2Save.SIBLING_GIFTS) g.event(flag, true);
            g.event(Gen2Save.EVENT_FOUGHT_SUDOWOODO, true);
            g.mon(0, 147, 50, 0x4321, 0xFF, 0xFF); // someone else's perfect Dratini
            int dratini = at.party() + 8;
            g.set(dratini + 2, 0xF5); // ExtremeSpeed
            g.set(dratini + 0x1B, 255); // happiness
            g.set(dratini + 0x1C, 0x13); // Pokérus strain 1, 3 days left
            var save = g.profile().read(g.ram());
            assertThat(save.money()).isEqualTo(999_999);
            assertThat(Achievements.earned(null, save)).contains("millionaire", "moms-savings", "ruins-explorer",
                    "puzzle-master", "tm-hoarder", "week-of-siblings", "odd-tree", "perfect-specimen", "best-friends",
                    "pokerus", "dragons-blessing", "growing-up", "raised-by-a-friend")
                    .doesNotContain("wake-up", "angler", "mystery-egg", "lone-wolf", "first-evolution");
            g.set(dratini + 0x1C, 0);
            g.set(at.tmsHms() + 24, 0);
            g.set(crystal ? 0xDEF3 : 0xDC3E, 0b0100);
            assertThat(Achievements.earned(null, g.profile().read(g.ram()))).contains("ruins-explorer")
                    .doesNotContain("pokerus", "tm-hoarder", "puzzle-master");
        }
    }

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
                        Gen2Save.MASTER_BALL, Gen2Save.MOON_STONE), 3, 1234, 3, 2, 12, 0, null, List.of());
        for (Gen2Ram.At at : new Gen2Ram.At[] {Gen2Ram.GOLD, Gen2Ram.CRYSTAL}) {
            var g = sample(at);
            var read = g.profile().read(g.ram());
            assertThat(read.members()).hasSize(3); // the party detail has its own test (Gen2PartyTest)
            assertThat(read).as(at.header()).isEqualTo(new Gen2Save(expected.playerId(), expected.johtoBadges(),
                    expected.kantoBadges(), expected.hallOfFame(), expected.redBeaten(), expected.gotStarter(),
                    expected.radioTowerCleared(), expected.gotMasterBall(), expected.caught(), expected.seen(),
                    expected.partyCount(), expected.party(), expected.items(), expected.hms(), expected.coins(),
                    expected.unownForms(), expected.phoneContacts(), expected.playHours(), 0, null, read.members(),
                    "KRIS", at == Gen2Ram.CRYSTAL, null, 0, Set.of(1, 5), read.slots()));
            assertThat(read.slots()).containsExactly(
                    new Gen2Save.Slot(130, false, true, 70, Gen2Ram.PLAYER_ID, 0x2AAA, false, 30, 0, 0, false),
                    new Gen2Save.Slot(175, true, false, 12, Gen2Ram.PLAYER_ID, 0, false, 1, 0, 0, false),
                    new Gen2Save.Slot(155, false, false, 52, 0x4321, 0, false, 21, 0, 0, false));
        }
    }

    @Test
    void summaryHasTheBadgesInEarningOrderAndThePokedex() {
        var g = sample(Gen2Ram.CRYSTAL);
        byte[] gyarados = PokemonRedTest.encode("GYARADOS");
        System.arraycopy(gyarados, 0, g.rom, Gen2Ram.CRYSTAL.romNames() + 129 * 10, gyarados.length);
        g.badges(0b0001_0001, 0b1000_0000); // Zephyr, Mineral; Earth
        var profile = g.profile();
        var summary = profile.summary(profile.read(g.ram()));

        assertThat(summary.badges()).extracting(b -> b.name()).containsExactly("Zephyr", "Hive", "Plain", "Fog",
                "Storm", "Mineral", "Glacier", "Rising", "Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh",
                "Volcano", "Earth");
        assertThat(summary.badges()).extracting(b -> b.earned()).containsExactly(true, false, false, false, false,
                true, false, false, false, false, false, false, false, false, false, true);
        assertThat(summary.badges().get(4).place()).isEqualTo("Cianwood Gym");
        var dex = summary.collection();
        assertThat(dex.size()).isEqualTo(251);
        assertThat(dex.owned()).containsExactly(130, 155);
        assertThat(dex.seen()).containsExactly(16, 130, 155); // caught counts as seen
        assertThat(dex.names()).hasSize(251);
        assertThat(dex.names().get(129)).isEqualTo("Gyarados");
        assertThat(dex.names().get(0)).isNull(); // unseen: not given away

        var empty = profile.summary(null);
        assertThat(empty.collection().size()).isEqualTo(251);
        assertThat(empty.collection().seen()).isEmpty();
        assertThat(empty.badges()).hasSize(16).noneMatch(b -> b.earned());
        assertThat(profile.icon(1)).isNull(); // the synthetic ROM ends before the pictures: null, no exception
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
        var save = new Gen2Save(1, 0xFF, 0xFF, 1, true, true, true, true, all, all, 6, legends, items, 7, 9999, 26, 10, 99, 0, null, List.of());
        // Side tops out at 26 + 15 + 14: the contest, eggs and the Battle Tower cannot be read.
        assertThat(TrainerScoring.score(save)).isEqualTo(
                new TrainerScore(200 + 250 + 150 + 142 + 100 + 55 + 30, "Pokémon Master", "S+", 200, 250, 150, 142, 100,
                        55, 30));
        assertThat(Achievements.earned(null, save)).contains("grand-collector", "three-beasts", "full-house",
                "wingspan", "full-kit", "stone-collector", "unown-scholar", "high-roller", "lucky-star", "back-to-kanto");
    }

    @Test
    void theCatalogueIsTheDesigns() {
        List<Achievement> all = Achievements.catalogue();
        assertThat(all).hasSize(85);
        assertThat(all).extracting(Achievement::id).doesNotHaveDuplicates()
                .doesNotContain("johto-complete", "bug-champion", "no-shopping") // not told by the RAM: removed
                .doesNotContain("250-club", "living-history", "the-time-traveller", "kantos-strongest", "fossil-hunter",
                        "old-friends"); // need a Gen 1 game or an event: removed in the review
        assertThat(all).extracting(Achievement::category).containsOnly("Story", "Pokédex", "Legends", "Training",
                "Items", "Side", "Challenge", "Link");
        assertThat(all).allMatch(a -> a.note() != null && !a.note().isBlank() && a.unlockedAt() == null
                && a.implemented() && !a.hidden() && a.rarity().equals(Achievement.rarity(a.points())));
        assertThat(all).extracting(Achievement::points).containsOnly(1, 2, 3, 5, 10, 25, 50);
        assertThat(all.stream().mapToInt(Achievement::points).sum()).isEqualTo(TOTAL_POINTS);
        assertThat(all).filteredOn(a -> a.rarity().equals("legendary")).extracting(Achievement::id)
                .containsExactlyInAnyOrder("the-silent-summit", "full-house", "low-level", "perfect-specimen", "pokerus",
                        "shining-legend", "lone-wolf", "grand-collector", "millionaire");
    }
}
