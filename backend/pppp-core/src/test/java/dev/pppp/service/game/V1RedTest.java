package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class V1RedTest {

    /** A Crystal-titled ROM with Red's record at 0E:799C and its TrainerGroups entry at 0E:5A15, as in the real one. */
    private static byte[] crystal() {
        byte[] rom = new byte[0x40000];
        System.arraycopy("PM_CRYSTAL\0BYTE".getBytes(StandardCharsets.US_ASCII), 0, rom, 0x134, 15);
        byte[] red = {(byte) 0x91, (byte) 0x84, (byte) 0x83, 0x50, 1, 81, 25};
        System.arraycopy(red, 0, rom, 0x3B99C, red.length);
        rom[0x3A15] = (byte) 0x9C; // a look-alike outside bank $0E must stay
        rom[0x3A16] = 0x79;
        rom[0x39A15] = (byte) 0x9C;
        rom[0x39A16] = 0x79;
        return rom;
    }

    @Test
    void repointsRedToAaaaInTheBankTail() {
        byte[] rom = crystal();
        byte[] out = new V1Red().patch(rom);

        assertThat(out[0x39A15] & 0xFF | (out[0x39A16] & 0xFF) << 8).isEqualTo(0x7FC0);
        assertThat(Arrays.copyOfRange(out, 0x3BFC0, 0x3BFC0 + V1Red.RECORD.length)).isEqualTo(V1Red.RECORD);
        assertThat(out[0x3A15] & 0xFF).isEqualTo(0x9C);
        assertThat(rom[0x39A15] & 0xFF).isEqualTo(0x9C); // the input is not modified
    }

    @Test
    void leavesOtherRomsAlone() {
        byte[] rom = crystal();
        rom[0x134] = 'X';
        assertThat(new V1Red().patch(rom)).isSameAs(rom);
    }

    /** The Hall of Fame team in its order, at Red's levels, one nickname each. */
    @Test
    void aaaaFieldsTheHallOfFameTeamAtRedsLevels() {
        int[] species = new int[6], levels = new int[6];
        for (int i = 0; i < 6; i++) {
            levels[i] = V1Red.RECORD[6 + i * 6] & 0xFF;
            species[i] = V1Red.RECORD[7 + i * 6] & 0xFF;
        }
        assertThat(species).containsExactly(144, 148, 6, 145, 97, 75); // Articuno Dragonair Charizard Zapdos Hypno Graveler
        assertThat(Arrays.stream(levels).sorted().toArray()).containsExactly(73, 75, 77, 77, 77, 81); // Red's
        assertThat(V1Red.RECORD[V1Red.RECORD.length - 1] & 0xFF).isEqualTo(0xFF);
        assertThat(V1Red.NICKNAMES).hasSize(6 * 11);
    }
}
