package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FrameEncoderTest {
    @Test
    void aGameBoyScreenComesBackPixelForPixelAsAPalettePng() throws Exception {
        int[] pal = {0xE0F8D0, 0x88C070, 0x346856, 0x081820};
        Random r = new Random(1);
        int[] frame = new int[160 * 144];
        for (int i = 0; i < frame.length; i++) frame[i] = pal[r.nextInt(4)];
        byte[] png = FrameEncoder.png(frame);
        assertThat(png[25]).as("colour type: palette").isEqualTo((byte) 3);
        assertDecodesTo(png, frame);
    }

    @Test
    void moreThan256ColoursFallBackToFullColour() throws Exception {
        int[] frame = new int[160 * 144];
        for (int i = 0; i < frame.length; i++) frame[i] = i * 37 & 0xFFFFFF;
        byte[] png = FrameEncoder.png(frame);
        assertThat(png[25]).as("colour type: RGB").isEqualTo((byte) 2);
        assertDecodesTo(png, frame);
    }

    private static void assertDecodesTo(byte[] png, int[] frame) throws Exception {
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(back.getWidth()).isEqualTo(160);
        assertThat(back.getHeight()).isEqualTo(144);
        for (int i = 0; i < frame.length; i++) {
            assertThat(back.getRGB(i % 160, i / 160) & 0xFFFFFF).as("pixel %d", i).isEqualTo(frame[i]);
        }
    }
}
