package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
    Map<String, Integer> points = new HashMap<>(); // pibs:scoreboard
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
        when(consoles.game(ASH)).thenReturn("red");
        when(consoles.readBytes(eq(ASH), anyInt(), anyInt())).thenAnswer(i -> ram.ram().bytes());
        when(repo.progress(any())).thenAnswer(i -> stored.getOrDefault(i.<String>getArgument(0), Progress.EMPTY));
        when(repo.scores()).thenAnswer(i -> points);
        doAnswer(i -> {
            stored.put(i.getArgument(0), i.getArgument(1));
            points.merge(i.getArgument(0), i.<List<ScoreEvent>>getArgument(2).stream().mapToInt(ScoreEvent::points).sum(), Integer::sum);
            return null;
        }).when(repo).award(any(), any(), any());
    }

    ProgressTracker tracker() {
        return new ProgressTracker(consoles, cartridges, repo, published::add);
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

        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 5, 20, 1);
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
        assertThat(points).containsEntry(ASH, 224);
        assertThat(published.getLast()).isEqualTo(new LiveEvent("SCOREBOARD_CHANGED",
                List.of(new ScoreRow(ASH, 224, 2, 1, 2, "red", true))));
    }

    @Test
    void aRestartOrALostSaveAwardsNothingTwice() {
        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 12, 20, 1);
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
        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 12, 20, 1);
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

    /** Crystal's layout with synthetic RAM; the addresses themselves come from pokecrystal.sym. */
    @Test
    void readsGenerationTwo() {
        byte[] rom = new byte[0x14 * 0x4000 + 0x3384 + 2510];
        System.arraycopy("PM_CRYSTAL\0BYTE".getBytes(), 0, rom, 0x134, 15);
        byte[] chikorita = PokemonRedTest.encode("CHIKORITA");
        System.arraycopy(chikorita, 0, rom, 0x14 * 0x4000 + 0x3384 + 151 * 10, chikorita.length);
        GameProfile<?> any = GameProfile.forRom(rom);
        assertThat(any).isInstanceOf(PokemonGen2.class);
        var crystal = (PokemonGen2) any;
        assertThat(crystal.ramStart()).isEqualTo(0xD47D);

        byte[] mem = new byte[crystal.ramLength()];
        assertThat(crystal.read(new Ram(0xD47D, mem))).isNull(); // no player name: not in play
        byte[] name = PokemonRedTest.encode("KRIS");
        System.arraycopy(name, 0, mem, 0, name.length);
        mem[0xDCD7 - 0xD47D] = 1; // wPartyCount, then wPartySpecies
        mem[0xDCD8 - 0xD47D] = (byte) 152;
        mem[0xDCD9 - 0xD47D] = (byte) 0xFF;
        mem[0xDCDF - 0xD47D] = (byte) 152; // wPartyMon1Species
        mem[0xDCFE - 0xD47D] = 14; // wPartyMon1Level
        mem[0xD857 - 0xD47D] = 0b10001; // Zephyr, Mineral
        mem[0xD858 - 0xD47D] = 0b1; // Boulder
        mem[0xDE99 - 0xD47D + 18] = (byte) 0x80; // #152 caught, not flagged seen
        mem[0xD95E - 0xD47D] = 1;
        assertThat(crystal.read(new Ram(0xD47D, mem))).isEqualTo(new Progress(Set.of("Zephyr", "Mineral", "Boulder"),
                Set.of("Chikorita"), Set.of("Chikorita"), 14, true));
    }
}
