package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IntroTest {
    static Stream<Path> roms() throws Exception {
        return MysteryGiftsTest.roms();
    }

    @Test
    void everyLineFitsTheTextBox() {
        for (String text : Intro.texts()) {
            for (String line : text.split("[\n\f~]")) assertThat(line.length()).as(line).isLessThanOrEqualTo(18);
        }
    }

    @ParameterizedTest(allowZeroInvocations = true) // no ROMs in data/ (CI): nothing to run
    @MethodSource("roms")
    void oakIsTom(Path rom) throws Exception {
        byte[] before = Files.readAllBytes(rom);
        Boolean crystal = PokemonGen2.crystal(before);
        assumeTrue(crystal != null, "not Gold, Silver or Crystal");
        byte[] after = Intro.patch(before);
        for (String text : Intro.texts()) {
            boolean gender = text.startsWith("Are you");
            assertThat(indexOf(after, Intro.encode(text)) >= 0).as(text).isEqualTo(!gender || crystal);
        }
        // The old texts are still there but nothing points at them: their text_far now points at the new ones.
        int hello = indexOf(before, Intro.encode("Hello! Sorry to")) - 1, a = 0x4000 + hello % 0x4000;
        assertThat(indexOf(after, new byte[] {0x16, (byte) a, (byte) (a >> 8), (byte) (hello / 0x4000)})).isNegative();
        assertThat(indexOf(after, Intro.encode("NEW NAME@TOM@PIB@PIBETTE@GANGBST@"))).as("names").isPositive();
        int lloyd = indexOf(after, Intro.encode("LLOYD@")), l = 0x4000 + lloyd % 0x4000;
        assertThat(lloyd / 0x4000).isEqualTo(3);
        assertThat(indexOf(after, new byte[] {0x11, (byte) l, (byte) (l >> 8)})).as("ld de, LLOYD").isPositive();
        byte[] face = Intro.facePic();
        byte[] tiles = Arrays.copyOfRange(face, 2, face.length - 1);
        if (crystal) {
            // PlayerPic draws a player who picked OAK with class POKEMON_PROF's pic: now the face.
            byte[] oak = PlayerPic.patch(after, "OAK");
            int first = indexOf(oak, tiles);
            assertThat(indexOf(Arrays.copyOfRange(oak, first + 1, oak.length), tiles)).as("ChrisPic too").isNotNegative();
            assertThat(indexOf(after, Intro.encode("Boy@Girl@Apache Helicopter@"))).isPositive();
        } else {
            // Gold/Silver's card picture is made from the class's pic: the face's.
            assertThat(indexOf(PlayerPic.patch(after, "OAK"), PlayerPic.card(tiles))).isPositive();
        }
        assertThat(indexOf(after, face)).isPositive();
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
