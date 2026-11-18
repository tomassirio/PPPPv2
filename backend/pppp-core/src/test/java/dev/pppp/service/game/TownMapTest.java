package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The Pokégear town maps drawn from a real Crystal ROM (skipped without one): 160x144, Johto and Kanto differ. */
class TownMapTest {
    @Test
    void drawsJohtoAndKanto() throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("PPPP_ROMS", "../../data/roms"));
        byte[] rom = null;
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".gbc")).toList()) {
                    byte[] bytes = Files.readAllBytes(p);
                    if (Gen2MapArt.fits(bytes)) rom = bytes;
                }
            }
        }
        assumeTrue(rom != null, "no Crystal ROM in " + dir.toAbsolutePath());
        var art = new Gen2MapArt(rom);
        byte[] johto = art.townMap(false), kanto = art.townMap(true);
        assertThat(johto).isNotNull();
        assertThat(kanto).isNotNull().isNotEqualTo(johto);
        var img = ImageIO.read(new ByteArrayInputStream(johto));
        assertThat(img.getWidth()).isEqualTo(160);
        assertThat(img.getHeight()).isEqualTo(144);
        String out = System.getenv("TOWNMAP_OUT");
        if (out != null) {
            Files.write(Path.of(out, "johto.png"), johto);
            Files.write(Path.of(out, "kanto.png"), kanto);
        }
    }
}
