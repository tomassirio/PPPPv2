package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Bus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class MysteryGiftsTest {
    /** 64 KiB of memory; the PC list at 0xD000. */
    static final class Mem implements Bus {
        final int[] b = new int[0x10000];
        public int getByte(int a) { return b[a]; }
        public void setByte(int a, int v) { b[a] = v & 0xFF; }
    }

    @Test
    void stacksOrAppendsAndRespectsTheLimits() {
        Mem m = new Mem();
        int pc = 0xD000;
        m.b[pc] = 1; m.b[pc + 1] = 0x20; m.b[pc + 2] = 98; m.b[pc + 3] = 0xFF; // one Potion stack of 98
        assertThat(MysteryGifts.put(m, pc, 0x20, 1)).isTrue();
        assertThat(m.b[pc + 2]).isEqualTo(99);
        assertThat(MysteryGifts.put(m, pc, 0x20, 1)).isTrue(); // the stack is full: a new entry
        assertThat(m.b[pc]).isEqualTo(2);
        assertThat(new int[] {m.b[pc + 3], m.b[pc + 4], m.b[pc + 5]}).containsExactly(0x20, 1, 0xFF);
        m.b[pc + 5] = 0x00; // not a list (no -1 after the last entry): nothing written
        assertThat(MysteryGifts.put(m, pc, 0x30, 1)).isFalse();
        assertThat(m.b[pc]).isEqualTo(2);
    }

    static Stream<Path> roms() throws Exception {
        Path dir = Path.of("../../data/roms");
        return Files.isDirectory(dir) ? Files.list(dir).filter(p -> p.toString().endsWith(".gbc")) : Stream.empty();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no ROMs in data/ (CI): nothing to run
    @MethodSource("roms")
    void everyGiftExistsInEveryGame(Path rom) throws Exception {
        PokemonGen2 game = PokemonGen2.forRom(Files.readAllBytes(rom));
        assumeTrue(game != null, "not Gold, Silver or Crystal");
        for (var g : MysteryGifts.CATALOG) assertThat(game.itemId(g.name())).as(g.name() + " in " + rom.getFileName()).isPositive();
    }
}
