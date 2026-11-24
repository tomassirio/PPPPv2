package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CreditsTest {
    static Stream<Path> roms() throws Exception {
        return MysteryGiftsTest.roms();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no ROMs in data/ (CI): nothing to run
    @MethodSource("roms")
    void everyNameIsTomas(Path rom) throws Exception {
        byte[] before = Files.readAllBytes(rom);
        assumeTrue(PokemonGen2.crystal(before) != null, "not Gold, Silver or Crystal");
        byte[] after = Credits.patch(before);
        assertThat(after).as("patched").isNotSameAs(before);
        int name = indexOf(after, Credits.encode("    TOMÁS SIRIO@"));
        assertThat(name).isPositive();
        int a = 0x4000 + name % 0x4000, names = Boolean.TRUE.equals(PokemonGen2.crystal(before)) ? 70 : 50;
        int table = indexOf(after, new byte[] {(byte) a, (byte) (a >> 8), (byte) a, (byte) (a >> 8)});
        for (int i = 0; i < names; i++) {
            assertThat((after[table + 2 * i] & 0xFF) | (after[table + 2 * i + 1] & 0xFF) << 8).as("name " + i).isEqualTo(a);
        }
        int title = (after[table + 2 * names] & 0xFF) | (after[table + 2 * names + 1] & 0xFF) << 8;
        assertThat(title).as("END, the first entry after the names, kept").isNotEqualTo(a);
        assertThat(Credits.patch(after)).as("patching twice changes nothing more").isSameAs(after);
    }

    private static int indexOf(byte[] b, byte[] what) {
        outer:
        for (int i = 0; i + what.length <= b.length; i++) {
            for (int j = 0; j < what.length; j++) if (b[i + j] != what[j]) continue outer;
            return i;
        }
        return -1;
    }
}
