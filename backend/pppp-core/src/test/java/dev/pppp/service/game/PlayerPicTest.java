package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PlayerPicTest {
    static Stream<Path> roms() throws Exception {
        return MysteryGiftsTest.roms();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no ROMs in data/ (CI): nothing to run
    @MethodSource("roms")
    void everyTrainerHasAFrontPicture(Path rom) throws Exception {
        byte[] bytes = Files.readAllBytes(rom);
        Boolean crystal = PokemonGen2.crystal(bytes);
        assumeTrue(crystal != null, "not Gold, Silver or Crystal");
        for (var c : PlayerSprites.choices(bytes)) {
            byte[] patched = PlayerPic.patch(bytes, c.key());
            if (PlayerPic.classOf(c.key()) == null) {
                assertThat(patched).as(c.key()).isSameAs(bytes);
                continue;
            }
            assertThat(patched).as(c.key() + " patched").isNotSameAs(bytes);
        }
        // The link opponent: CAL points at the other player's class while linked, and back after.
        var linked = PlayerPic.linkOpponent(bytes, "FALKNER", "CHRIS");
        var back = PlayerPic.linkOpponent(bytes, null, "CHRIS");
        assertThat(linked).isNotEqualTo(back).hasSameSizeAs(back);
        String dir = System.getenv("SHOTS");
        if (dir != null) {
            for (String key : new String[] {"FALKNER", "LASS", "RED", "OFFICER"}) {
                byte[] p = PlayerPic.patch(bytes, key);
                int at = crystal ? 0x22 * 0x4000 + 0x48A9 - 0x4000 : 0x09 * 0x4000 + 0x547F - 0x4000;
                int len = crystal ? 784 : 560, w = crystal ? 7 : 5;
                byte[] tiles = java.util.Arrays.copyOfRange(p, at, at + len);
                var img = new java.awt.image.BufferedImage(w * 8, 56, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int t = 0; t < w * 7; t++) {
                    int tx = crystal ? t / 7 : t % 5, ty = crystal ? t % 7 : t / 5; // columns (Crystal) or rows (card)
                    for (int r = 0; r < 8; r++) {
                        int lo = tiles[t * 16 + r * 2] & 0xFF, hi = tiles[t * 16 + r * 2 + 1] & 0xFF;
                        for (int x = 0; x < 8; x++) {
                            int v = (lo >> 7 - x & 1) | (hi >> 7 - x & 1) << 1;
                            img.setRGB(tx * 8 + x, ty * 8 + r, new int[] {0xFFFFFF, 0xAAAAAA, 0x555555, 0}[v]);
                        }
                    }
                }
                javax.imageio.ImageIO.write(img, "png", Path.of(dir, rom.getFileName() + "-" + key + ".png").toFile());
            }
        }
    }
}
