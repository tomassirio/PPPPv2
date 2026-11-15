package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Gen2PicTest {
    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) out[i] = (byte) b[i];
        return out;
    }

    /** One of each command from home/decompress.asm, worked out by hand. */
    @Test
    void decompressesEveryCommand() {
        byte[] lz = bytes(0x00, // padding: data may start anywhere
                0x02, 0x41, 0x42, 0x43, // literal x3
                0x21, 0x07, // one byte x2
                0x42, 0x01, 0x02, // alternate two bytes x3
                0x61, // zero x2
                0x82, 0x00, 0x00, // repeat x3 from output start + 0
                0xA0, 0x00, 0x02, // bit-flipped copy x1 of output[2] (0x43 -> 0xC2)
                0xC2, 0x82, // backwards copy x3 from 3 before the end (output[11], [10], [9])
                0xE4, 0x04, 0x09, // LZ_LONG: command 1 (one byte), count 4 + 1
                0x83, 0x80, // repeat x4 from 1 before the end: overlapping, a run of the last byte
                0xFF);
        assertThat(Gen2Pic.decompress(lz, 1)).containsExactly(bytes(
                0x41, 0x42, 0x43, 0x07, 0x07, 0x01, 0x02, 0x01, 0x00, 0x00, 0x41, 0x42, 0x43, 0xC2, 0x42, 0x41, 0x00,
                0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09));
    }

    @Test
    void padsSmallPicsIntoTheSevenBySevenBox() {
        byte[] tiles = new byte[5 * 5 * 16];
        java.util.Arrays.fill(tiles, (byte) 0xFF); // every pixel colour 3
        int[][] px = Gen2Pic.frontpic(tiles, 5);
        assertThat(px).hasNumberOfRows(56);
        assertThat(px[0]).hasSize(56);
        // Columns 1-5, rows 2-6 (PadFrontpic .five).
        assertThat(px[16][8]).isEqualTo(3);
        assertThat(px[55][47]).isEqualTo(3);
        assertThat(px[15][8]).isZero();
        assertThat(px[16][7]).isZero();
        assertThat(px[55][48]).isZero();

        px = Gen2Pic.frontpic(new byte[6 * 6 * 16 + 100], 6); // extra animation tiles after the pic are ignored
        assertThat(px).hasNumberOfRows(56);
    }

    /**
     * Against the real cartridges when they are there (not in CI): PPPP_ROMS, or the repo's data/roms. Every
     * species and the 8 Johto badges draw; Kanto badges have no art.
     */
    @Test
    void drawsEverySpeciesAndTheJohtoBadgesFromRealRoms() throws IOException {
        Path dir = Path.of(System.getenv().getOrDefault("PPPP_ROMS", "../../data/roms"));
        List<Path> roms = Files.isDirectory(dir) ? listGbc(dir) : List.of();
        List<PokemonGen2> profiles = roms.stream().map(Gen2PicTest::profile).filter(p -> p != null).toList();
        assumeTrue(!profiles.isEmpty(), "no Gold/Silver/Crystal ROM in " + dir.toAbsolutePath());
        for (PokemonGen2 p : profiles) {
            for (int id = 1; id <= 251; id++) assertThat(size(p.icon(id))).as("#" + id).isEqualTo(56);
            for (int n = 0; n < 8; n++) assertThat(size(p.badge(n))).as("badge " + n).isEqualTo(16);
            assertThat(p.badge(8)).isNull();
            assertThat(p.icon(252)).isNull();
        }
    }

    private static List<Path> listGbc(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".gbc")).toList();
        }
    }

    private static PokemonGen2 profile(Path rom) {
        try {
            return PokemonGen2.forRom(Files.readAllBytes(rom));
        } catch (IOException e) {
            return null;
        }
    }

    private static int size(byte[] png) throws IOException {
        assertThat(png).isNotNull();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(img.getHeight());
        return img.getWidth();
    }
}
