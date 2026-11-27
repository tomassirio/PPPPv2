package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.pppp.config.PpppProperties;
import dev.pppp.service.PlayerConsoles;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplaysTest {
    @TempDir
    Path data;

    private static int[] picture(int colour) {
        int[] p = new int[160 * 144];
        java.util.Arrays.fill(p, colour);
        p[0] = 0x123456;
        return p;
    }

    @Test
    void keepsTheLastFiveSecondsAndWritesAGif() throws Exception {
        Replays replays = new Replays(mock(PlayerConsoles.class), new PpppProperties(List.of(), data));
        for (int i = 0; i < 600; i++) replays.frame("a", picture(i)); // 20 s of changing pictures
        for (int i = 0; i < 300; i++) replays.frame("a", picture(-1)); // 10 s waiting on one picture: counts 1 s
        String id = UUID.randomUUID().toString();
        replays.save("a", id);
        Path gif = data.resolve("replays").resolve(id + ".gif");
        for (int i = 0; i < 100 && !Files.exists(gif); i++) Thread.sleep(50);
        assertThat(replays.find(id)).isEqualTo(gif);
        assertThat(replays.millis(id)).isBetween(4900, 5100);
        assertThat(new Replays(mock(PlayerConsoles.class), new PpppProperties(List.of(), data)).millis(id))
                .isEqualTo(replays.millis(id)); // read back from the file after a restart

        var reader = ImageIO.getImageReadersByFormatName("gif").next();
        reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(Files.readAllBytes(gif))));
        int frames = reader.getNumImages(true);
        // 4 s of changing pictures at 15 fps, then the held one (1 s): 5 s in all
        assertThat(frames).isBetween(60, 62);
        assertThat(reader.read(0).getRGB(5, 5) & 0xFFFFFF).isEqualTo(480); // the first kept: 16 s in
        assertThat(reader.read(frames - 1).getRGB(5, 5) & 0xFFFFFF).isEqualTo(0xFFFFFF);
        assertThat(Files.size(gif)).isLessThan(100_000);

        replays.delete(id);
        assertThat(replays.find(id)).isNull();
        assertThat(replays.find("../../etc/passwd")).isNull();
        replays.retain(Set.of());
    }
}
