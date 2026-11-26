package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.model.TrainerCard;
import dev.pppp.model.TrainerScore;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressTrackerTest {
    static final String ASH = "ash@x.y";
    @TempDir Path dir;

    PokemonRedTest ram = new PokemonRedTest();
    PlayerConsoles consoles = mock(PlayerConsoles.class);
    CartridgeService cartridges = mock(CartridgeService.class);
    ProgressRepository repo = mock(ProgressRepository.class);
    Map<String, Progress> stored = new HashMap<>(); // pibs:progress
    Map<String, Map<String, Long>> achieved = new HashMap<>(); // pibs:achievements
    Map<String, TrainerScore> trainers = new HashMap<>(); // pibs:trainer
    Map<String, Long> ranks = new HashMap<>(); // pibs:scoreboard
    List<ScoreEvent> feed = new ArrayList<>(); // pibs:events
    List<Object> published = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        ram.setUp();
        byte[] rom = new byte[0x42000];
        System.arraycopy("POKEMON RED".getBytes(), 0, rom, 0x134, 11);
        rom[0x14A] = 1;
        PokemonRedTest.name(rom, PokemonRedTest.BULBASAUR, "BULBASAUR", 1);
        PokemonRedTest.name(rom, PokemonRedTest.PIDGEY, "PIDGEY", 16);
        when(cartridges.require("red")).thenReturn(Files.write(dir.resolve("red.gb"), rom));
        when(cartridges.require("tetris")).thenReturn(Files.write(dir.resolve("tetris.gb"), new byte[0x8000]));
        when(consoles.running()).thenReturn(Set.of(ASH));
        when(consoles.awake()).thenReturn(Set.of(ASH));
        when(consoles.game(ASH)).thenReturn("red");
        when(consoles.readBytes(eq(ASH), anyInt(), anyInt())).thenAnswer(i -> ram.ram().bytes());
        when(repo.progress(any())).thenAnswer(i -> stored.getOrDefault(i.<String>getArgument(0), Progress.EMPTY));
        when(repo.ranking()).thenAnswer(i -> ranks.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).map(Map.Entry::getKey).toList());
        when(repo.achievements(any())).thenAnswer(i -> new HashMap<>(achieved.getOrDefault(i.<String>getArgument(0), Map.of())));
        when(repo.card(any())).thenReturn(TrainerCard.EMPTY);
        when(repo.log(any())).thenReturn(dev.pppp.model.TrainerLog.EMPTY);
        when(repo.trainer(any())).thenAnswer(i -> trainers.getOrDefault(i.<String>getArgument(0), TrainerScore.ZERO));
        doAnswer(i -> {
            stored.put(i.getArgument(0), i.getArgument(1));
            ranks.putIfAbsent(i.getArgument(0), 0L);
            feed.addAll(i.getArgument(2));
            return null;
        }).when(repo).award(any(), any(), any());
        doAnswer(i -> {
            String user = i.getArgument(0);
            trainers.put(user, i.getArgument(1));
            achieved.computeIfAbsent(user, u -> new HashMap<>()).putAll(i.getArgument(2));
            ranks.put(user, i.getArgument(3));
            feed.addAll(i.getArgument(4));
            return null;
        }).when(repo).rate(any(), any(), any(), anyLong(), any());
    }

    final dev.pppp.repository.LinkHistoryRepository history = mock(dev.pppp.repository.LinkHistoryRepository.class);

    ProgressTracker tracker() {
        return new ProgressTracker(consoles, cartridges, repo, published::add, mock(dev.pppp.repository.PlayerRepository.class),
                history);
    }

    List<String> scored() {
        return published.stream().map(e -> (LiveEvent) e).filter(e -> e.type().equals("PROGRESS"))
                .map(e -> (ScoreEvent) e.payload()).map(e -> e.type() + " " + e.detail() + " +" + e.points()).toList();
    }

    @Test
    void awardsOnlyWhatIsNewOnceItHeldForTwoSamples() {
        var tracker = tracker();
        byte[] named = ram.mem.clone();
        Arrays.fill(ram.mem, (byte) 0xFF); // boot: no player name, garbage everywhere
        tracker.poll();
        tracker.poll();
        assertThat(scored()).isEmpty();
        assertThat(stored).isEmpty();

        ram.mem = named; // a new game, in play with nothing yet: the baseline is zero
        tracker.poll();
        tracker.poll();
        assertThat(scored()).isEmpty();

        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 5, 20);
        ram.dexOwned(1);
        tracker.poll();
        assertThat(scored()).isEmpty(); // seen once: could be mid-write
        tracker.poll();
        assertThat(scored()).containsExactly("CAUGHT Bulbasaur +10", "SEEN Bulbasaur +2");
        tracker.poll();
        assertThat(scored()).hasSize(2); // unchanged: nothing more

        ram.mem[ram.off(0xD356)] = 0b101; // two badges
        ram.mem[ram.off(0xD30A + 1)] = (byte) 0x80; // Pidgey (#16) seen
        ram.mem[ram.off(0xD16B + 0x21)] = 21; // level 5 -> 21 passes 10 and 20
        tracker.poll();
        tracker.poll();
        assertThat(scored()).containsExactly("CAUGHT Bulbasaur +10", "SEEN Bulbasaur +2", "BADGE Boulder +100",
                "BADGE Thunder +100", "SEEN Pidgey +2", "LEVEL 10 +5", "LEVEL 20 +5");
        assertThat(ProgressTracker.points(stored.get(ASH))).isEqualTo(224);
        assertThat(published.getLast()).isEqualTo(new LiveEvent("SCOREBOARD_CHANGED", // Red has no Trainer Score
                List.of(new ScoreRow(ASH, 224, 2, 1, 2, "red", true, 0, "Youngster", "F", 0, 0, List.of(), null, null, 0, 0, null, ScoreRow.Link.NONE))));
    }

    @Test
    void thePartyBoxIsThePlayersOwnConfirmedReading() {
        var tracker = tracker();
        assertThat(tracker.summary("nobody@x.y")).isNull(); // no game picked
        when(consoles.game("tetris@x.y")).thenReturn("tetris");
        assertThat(tracker.summary("tetris@x.y")).isNull(); // no profile for that cartridge
        assertThat(tracker.icon("tetris@x.y", 1)).isNull();
        assertThat(tracker.summary(ASH).party()).isEmpty(); // not sampled yet: an empty box
        assertThat(tracker.summary(ASH).badges()).hasSize(8).noneMatch(b -> b.earned());

        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 5, 20);
        tracker.poll();
        assertThat(tracker.summary(ASH).party()).isEmpty(); // seen once: could be mid-write
        tracker.poll();
        assertThat(tracker.summary(ASH).party()).extracting(m -> m.name()).containsExactly("Bulbasaur");
        tracker.poll();
        assertThat(published.stream().map(e -> (LiveEvent) e).filter(e -> e.type().equals("GAME_STATE")).map(LiveEvent::payload))
                .containsExactly(Map.of("user", ASH, "state", tracker.summary(ASH))); // once per change, tagged with the player

        Arrays.fill(ram.mem, (byte) 0); // out of play for a moment: the box keeps the last reading
        tracker.poll();
        tracker.poll();
        assertThat(tracker.summary(ASH).party()).hasSize(1);
    }

    @Test
    void aRestartOrALostSaveAwardsNothingTwice() {
        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 12, 20);
        ram.dexOwned(1);
        ram.mem[ram.off(0xD356)] = 1;
        var tracker = tracker();
        tracker.poll();
        tracker.poll();
        assertThat(scored()).containsExactly("BADGE Boulder +100", "CAUGHT Bulbasaur +10", "SEEN Bulbasaur +2", "LEVEL 10 +5");

        tracker = tracker(); // backend restart: only Redis remembers
        tracker.poll();
        tracker.poll();
        assertThat(scored()).hasSize(4);

        ram.setUp(); // the player started a new game on the cartridge and earns the same things again
        tracker.poll();
        tracker.poll();
        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 12, 20);
        ram.dexOwned(1);
        ram.mem[ram.off(0xD356)] = 0b11;
        tracker.poll();
        tracker.poll();
        assertThat(scored()).hasSize(5).last().isEqualTo("BADGE Cascade +100");
        assertThat(stored.get(ASH).badges()).containsExactly("Boulder", "Cascade");
    }

    @Test
    void aCartridgeWithoutAProfileScoresNothing() {
        when(consoles.game(ASH)).thenReturn("tetris");
        ram.mem[ram.off(0xD356)] = 1;
        var tracker = tracker();
        tracker.poll();
        tracker.poll();
        assertThat(scored()).isEmpty();
        assertThat(stored).isEmpty();
    }

    @Test
    void scoresEachKindOfStep() {
        var before = new Progress(Set.of("Boulder"), Set.of("Pidgey"), Set.of("Pidgey"), 19, false);
        var after = new Progress(Set.of("Boulder", "Cascade"), Set.of("Pidgey"), Set.of("Pidgey", "Rattata"), 30, true);
        assertThat(ProgressTracker.score(ASH, before, after, 7)).containsExactly(
                new ScoreEvent(ASH, "BADGE", "Cascade", 100, 7), new ScoreEvent(ASH, "SEEN", "Rattata", 2, 7),
                new ScoreEvent(ASH, "LEVEL", "20", 5, 7), new ScoreEvent(ASH, "LEVEL", "30", 5, 7),
                new ScoreEvent(ASH, "HALL_OF_FAME", "", 500, 7));
        assertThat(ProgressTracker.score(ASH, after, after, 7)).isEmpty();
    }

    /** Crystal's layout with synthetic RAM; the addresses themselves come from pokecrystal11.sym. */
    @Test
    void readsGenerationTwo() {
        var crystal = new Gen2Ram(Gen2Ram.CRYSTAL);
        byte[] chikorita = PokemonRedTest.encode("CHIKORITA");
        System.arraycopy(chikorita, 0, crystal.rom, crystal.at.romNames() + 151 * 10, chikorita.length);
        GameProfile<?> any = GameProfile.forRom(crystal.rom);
        assertThat(any).isInstanceOf(PokemonGen2.class);
        var profile = (PokemonGen2) any;
        assertThat(profile.ramStart()).isEqualTo(0xD47B);

        assertThat(profile.read(new Ram(0xD47B, new byte[profile.ramLength()]))).isNull(); // no player name: not in play
        crystal.mon(0, 152, 14, Gen2Ram.PLAYER_ID);
        crystal.badges(0b10001, 0b1); // Zephyr, Mineral; Boulder
        crystal.caught(152); // not flagged seen
        crystal.hallOfFame(1);
        assertThat(profile.progress(profile.read(crystal.ram()))).isEqualTo(new Progress(
                Set.of("Zephyr", "Mineral", "Boulder"), Set.of("Chikorita"), Set.of("Chikorita"), 14, true));
    }

    // ---- Gen 2: achievements and Trainer Score through the tracker ----

    Gen2Ram gold = new Gen2Ram(Gen2Ram.GOLD);

    ProgressTracker goldTracker() throws Exception {
        when(cartridges.require("gold")).thenReturn(Files.write(dir.resolve("gold.gbc"), gold.rom));
        when(consoles.game(ASH)).thenReturn("gold");
        when(consoles.readBytes(eq(ASH), anyInt(), anyInt())).thenAnswer(i -> gold.ram().bytes());
        return tracker();
    }

    void twice(ProgressTracker tracker) {
        tracker.poll();
        tracker.poll();
    }

    List<String> unlockedEvents() {
        return published.stream().map(e -> (LiveEvent) e).filter(e -> e.type().equals("ACHIEVEMENT_UNLOCKED"))
                .map(e -> (String) ((Map<?, ?>) e.payload()).get("id")).toList();
    }

    @Test
    void unlocksEachAchievementOnceEvenAfterARestartOrANewGame() throws Exception {
        var tracker = goldTracker();
        twice(tracker); // a new game: nothing
        assertThat(unlockedEvents()).isEmpty();
        assertThat(achieved).isEmpty();

        gold.event(26, true); // EVENT_GOT_A_POKEMON_FROM_ELM
        gold.mon(0, 155, 5, Gen2Ram.PLAYER_ID); // Cyndaquil
        gold.caught(155);
        gold.badges(0b1, 0);
        tracker.poll();
        assertThat(unlockedEvents()).isEmpty(); // seen once: could be mid-write
        tracker.poll();
        assertThat(unlockedEvents()).containsExactly("new-bark-beginnings", "zephyr-badge");
        assertThat(published).contains(new LiveEvent("ACHIEVEMENT_UNLOCKED", Map.of("user", ASH,
                "id", "zephyr-badge", "name", "Zephyr Badge", "points", 3, "at", achieved.get(ASH).get("zephyr-badge"))));
        assertThat(feed).filteredOn(e -> e.type().equals("ACHIEVEMENT")).extracting(ScoreEvent::detail, ScoreEvent::points)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("New Bark Beginnings", 1),
                        org.assertj.core.groups.Tuple.tuple("Zephyr Badge", 3));
        twice(tracker);
        assertThat(unlockedEvents()).hasSize(2); // unchanged: nothing more

        tracker = tracker(); // backend restart: only Redis remembers
        twice(tracker);
        assertThat(unlockedEvents()).hasSize(2);

        gold.newGame(); // the player started over and earns the same things again
        twice(tracker);
        gold.event(26, true);
        gold.badges(0b11, 0);
        twice(tracker);
        assertThat(unlockedEvents()).containsExactly("new-bark-beginnings", "zephyr-badge", "hive-badge");
        assertThat(achieved.get(ASH)).containsOnlyKeys("new-bark-beginnings", "zephyr-badge", "hive-badge");
        assertThat(tracker.scoreboard().getFirst()).extracting(ScoreRow::achievementPoints, ScoreRow::achievementsUnlocked)
                .containsExactly(7, 3);
    }

    @Test
    void theTrainerScoreGoesUpWithTheSaveButNeverDown() throws Exception {
        var tracker = goldTracker();
        gold.mon(0, 150, 100, 0x9999); // a traded Lv 100 Mewtwo: team 25, collection 150 * 100 / 6020 = 2
        gold.mon(1, 129, 100, Gen2Ram.PLAYER_ID); // Lv 100 Magikarp: 5 more strength, team still 26
        gold.caught(150, 129);
        gold.coins(9999);
        twice(tracker);
        TrainerScore high = trainers.get(ASH);
        assertThat(high).isEqualTo(new TrainerScore(44, "Youngster", "F", 0, 2, 2, 26, 0, 14, 0));
        // Ranked by score, then achievement points: High Roller 5, Level Up 5, Centurion 25, Growing Up 2 and
        // Raised by a Friend 10 (the traded Mewtwo).
        assertThat(ranks.get(ASH)).isEqualTo(44 * 10_000L + 47);
        assertThat(tracker.scoreboard().getFirst()).extracting(ScoreRow::trainerScore, ScoreRow::title, ScoreRow::grade)
                .containsExactly(44, "Youngster", "F");
        assertThat(tracker.scoreboard().getFirst().party())
                .containsExactly(new ScoreRow.Member(150, 100, 100, null, false, false),
                        new ScoreRow.Member(129, 100, 100, null, false, false));

        gold.partySize(1); // Magikarp went to the box, the coins were spent
        gold.coins(0);
        twice(tracker);
        assertThat(trainers.get(ASH).score()).isEqualTo(44); // earned: team 26 and side 14 stay
        assertThat(tracker.scoreboard().getFirst().party())
                .containsExactly(new ScoreRow.Member(150, 100, 100, null, false, false));
        verify(repo, org.mockito.Mockito.atLeastOnce()).saveCard(eq(ASH), any());
        assertThat(achieved.get(ASH)).containsKeys("raised-by-a-friend", "high-roller", "level-up", "centurion");
    }

    @Test
    void aLinkBattleGoesToTheFeedEvenWhenThePlayerLeavesPlayRightAfter() throws Exception {
        var tracker = goldTracker();
        tracker.sramEvery = 0;
        byte[] sav = new byte[0x8000 + 44];
        sav[0x2008] = 99; // saved once (sCheckValue1)
        int wins = 0x31BA; // sLinkBattleWins, big-endian; losses next
        when(consoles.batterySave(ASH)).thenAnswer(i -> sav.clone());
        when(consoles.isAwake(ASH)).thenReturn(true);
        when(consoles.linkedWith(ASH)).thenReturn("misty@x.y");
        twice(tracker);
        twice(tracker); // the record so far: none
        assertThat(scored()).noneMatch(s -> s.startsWith("LINK"));

        sav[wins + 1] = 1; // won the battle
        when(consoles.linkedWith(ASH)).thenReturn(null); // and unplugged
        tracker.poll(); // read once: could be mid-write
        assertThat(scored()).noneMatch(s -> s.startsWith("LINK"));
        when(consoles.isAwake(ASH)).thenReturn(false); // then left /play: the console paused
        tracker.poll(); // still read again, so it counts
        assertThat(scored()).containsOnlyOnce("LINK_WIN misty@x.y +25");
        assertThat(tracker.scoreboard().getFirst().points()).isEqualTo(25); // a win counts towards the points
        // ... and goes to the link history, against whom, with their team as read.
        org.mockito.Mockito.verify(history).add(org.mockito.ArgumentMatchers.eq(ASH), org.mockito.ArgumentMatchers.argThat(
                e -> e.kind().equals("BATTLE") && "WIN".equals(e.result()) && "misty@x.y".equals(e.partner())));

        sav[wins + 3] = 1; // a loss can only come from a running console
        when(consoles.isAwake(ASH)).thenReturn(true);
        twice(tracker);
        // One feed entry per battle, the winner's: a loss shows only in the record.
        assertThat(scored()).noneMatch(s -> s.startsWith("LINK_LOSS"));
        assertThat(tracker.scoreboard().getFirst().link().losses()).isEqualTo(1);
        assertThat(tracker.scoreboard().getFirst().points()).isEqualTo(25); // a loss costs nothing
        org.mockito.Mockito.verify(history).add(org.mockito.ArgumentMatchers.eq(ASH),
                org.mockito.ArgumentMatchers.argThat(e -> e.kind().equals("BATTLE") && "LOSS".equals(e.result())));
    }

    @Test
    void versusCountsTheWinsBetweenTwoPibs() {
        when(repo.newestOfType("LINK_WIN")).thenReturn(List.of(
                new ScoreEvent(ASH, "LINK_WIN", "misty@x.y", 25, 3),
                new ScoreEvent("misty@x.y", "LINK_WIN", ASH, 25, 2),
                new ScoreEvent(ASH, "LINK_WIN", "brock@x.y", 25, 1), // someone else
                new ScoreEvent(ASH, "LINK_WIN", "misty@x.y", 25, 0)));
        ProgressTracker.Versus v = tracker().versus(ASH, "misty@x.y");
        assertThat(v.aWins()).isEqualTo(2);
        assertThat(v.bWins()).isEqualTo(1);
        assertThat(v.recent()).extracting(ScoreEvent::at).containsExactly(3L, 2L, 0L);
    }

    @Test
    void thePokedexShowsOnlyWhatThePlayerHasSeenOrCaught() throws Exception {
        var tracker = goldTracker();
        gold.mon(0, 155, 5, Gen2Ram.PLAYER_ID);
        gold.caught(155);
        gold.seen(155, 16);
        twice(tracker);
        assertThat(tracker.dex(ASH, 155)).extracting(e -> e.state(), e -> e.name() != null, e -> e.weightPounds() != null)
                .containsExactly("caught", true, true);
        assertThat(tracker.dex(ASH, 16)).extracting(e -> e.state(), e -> e.name() != null, e -> e.text(), e -> e.heightFeet())
                .containsExactly("seen", true, null, null);
        assertThat(tracker.dex(ASH, 150)).extracting(e -> e.state(), e -> e.name(), e -> e.category(), e -> e.types())
                .containsExactly("unseen", null, null, null);
    }

    @Test
    void aTitleChangeGoesToTheFeed() throws Exception {
        var tracker = goldTracker();
        gold.badges(0xFF, 0xFF); // 120
        twice(tracker);
        assertThat(feed).filteredOn(e -> e.type().equals("TITLE")).extracting(ScoreEvent::detail)
                .containsExactly("Bug Catcher (E)");
        assertThat(scored()).contains("TITLE Bug Catcher (E) +0");
    }

    @Test
    void momentAchievementsNeedTheMomentItself() throws Exception {
        var tracker = goldTracker();
        gold.mon(0, 160, 50, Gen2Ram.PLAYER_ID); // Feraligatr alone
        gold.balls(1); // MASTER_BALL
        gold.hours(5);
        twice(tracker);
        gold.hallOfFame(1);
        twice(tracker);
        assertThat(unlockedEvents()).contains("champion-of-johto", "solo-run", "self-control", "speedrunner", "mono-type");

        // Seen only after the fact (a restart fell between): the Hall of Fame counts, the moment does not.
        published.clear();
        achieved.clear();
        tracker = tracker();
        twice(tracker);
        assertThat(unlockedEvents()).contains("champion-of-johto").doesNotContain("solo-run", "self-control", "speedrunner");
    }

    @Test
    void redAtMtSilver() throws Exception {
        var tracker = goldTracker();
        gold.mon(0, 20, 60, Gen2Ram.PLAYER_ID); // Raticate
        gold.event(1890, true); // EVENT_RED_IN_MT_SILVER: set at the start of every game
        twice(tracker);
        gold.hallOfFame(1);
        gold.event(68, true); // EVENT_BEAT_ELITE_FOUR
        gold.event(1890, false); // the Hall of Fame lets him appear
        twice(tracker);
        assertThat(unlockedEvents()).doesNotContain("the-silent-summit");
        gold.event(1890, true); // beaten: he disappears
        twice(tracker);
        assertThat(unlockedEvents()).contains("the-silent-summit", "rattata-rules", "low-level");
        assertThat(trainers.get(ASH).story()).isEqualTo(80);
    }

    /** The Cable Club steps from a reading: a starter, then Mr. Pokémon's Mystery Egg, then the egg given to Elm. */
    @Test
    void theCableClubOpensOnceElmHasTheMysteryEgg() {
        assertThat(ProgressTracker.cableClub(club(false))).isZero();
        assertThat(ProgressTracker.cableClub(club(true))).isEqualTo(1);
        assertThat(ProgressTracker.cableClub(club(true, Gen2Save.EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON))).isEqualTo(2);
        assertThat(ProgressTracker.cableClub(club(true, Gen2Save.EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON,
                Gen2Save.EVENT_GAVE_MYSTERY_EGG_TO_ELM))).isEqualTo(dev.pppp.model.TrainerCard.CABLE_CLUB_OPEN);
        // A card saved before the step existed reads 0: a badge or a link record shows the club is open.
        var old = new dev.pppp.model.TrainerCard(List.of(), null, null, 0, 0, null, null, null, 0);
        var badge = new dev.pppp.model.TrainerCard(List.of(), null, null, 1, 0, null, null, null, 0);
        var linked = new dev.pppp.model.TrainerLog(Set.of(), 0, Set.of("misty@x.y"), 1, false, null, 0, 0);
        assertThat(ProgressTracker.cableClub(old, dev.pppp.model.TrainerLog.EMPTY)).isZero();
        assertThat(ProgressTracker.cableClub(badge, dev.pppp.model.TrainerLog.EMPTY)).isEqualTo(3);
        assertThat(ProgressTracker.cableClub(old, linked)).isEqualTo(3);
    }

    private static Gen2Save club(boolean starter, Integer... flags) {
        return new Gen2Save(1, 0, 0, 0, false, starter, false, false, new java.util.BitSet(), new java.util.BitSet(), 0,
                List.of(), Set.of(), 0, 0, 0, 0, 0, 0, null, List.of(), null, true, null, 0, Set.of(), List.of(), 0, 0, 0, 0,
                Set.of(flags), null, null);
    }
}
