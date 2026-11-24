package dev.pppp.realtime;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Encodes a 160x144 0xRRGGBB frame as PNG, written by hand: a palette PNG (a Game Boy Color screen has at most a few
 * dozen colours; one byte a pixel), deflated at BEST_SPEED, or full colour for a frame with more than 256. About 15x
 * cheaper than ImageIO's writer and smaller (~8 KB against ~9.5 KB). At 4x every player's stream is 120 of these a
 * second, and ImageIO's took CPU the emulator needed on the Pi (a 4x console measured 0.9x).
 */
public final class FrameEncoder {
    private static final int W = 160, H = 144;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    private FrameEncoder() {}

    public static byte[] png(int[] rgb) {
        Map<Integer, Integer> index = new HashMap<>();
        byte[] rows = new byte[H * (1 + W)];
        int o = 0, last = -1, lastIndex = 0;
        for (int y = 0; y < H; y++) {
            rows[o++] = 0; // filter: none
            for (int x = 0; x < W; x++) {
                int c = rgb[y * W + x] & 0xFFFFFF;
                if (c != last) {
                    Integer i = index.get(c);
                    if (i == null) {
                        if (index.size() == 256) return trueColour(rgb);
                        i = index.size();
                        index.put(c, i);
                    }
                    last = c;
                    lastIndex = i;
                }
                rows[o++] = (byte) lastIndex;
            }
        }
        byte[] palette = new byte[index.size() * 3];
        index.forEach((c, i) -> {
            palette[i * 3] = (byte) (c >> 16);
            palette[i * 3 + 1] = (byte) (c >> 8);
            palette[i * 3 + 2] = (byte) (int) c;
        });
        return write(3, palette, rows);
    }

    private static byte[] trueColour(int[] rgb) {
        byte[] rows = new byte[H * (1 + W * 3)];
        int o = 0;
        for (int y = 0; y < H; y++) {
            rows[o++] = 0;
            for (int x = 0; x < W; x++) {
                int c = rgb[y * W + x];
                rows[o++] = (byte) (c >> 16);
                rows[o++] = (byte) (c >> 8);
                rows[o++] = (byte) c;
            }
        }
        return write(2, null, rows);
    }

    /** colourType: 2 = RGB, 3 = palette (with palette). */
    private static byte[] write(int colourType, byte[] palette, byte[] rows) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(rows);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.size() + 1024);
            DataOutputStream s = new DataOutputStream(out);
            s.write(SIGNATURE);
            chunk(s, "IHDR", new byte[] {0, 0, 0, (byte) W, 0, 0, 0, (byte) H, 8, (byte) colourType, 0, 0, 0});
            if (palette != null) chunk(s, "PLTE", palette);
            chunk(s, "IDAT", compressed.toByteArray());
            chunk(s, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // a ByteArrayOutputStream does not throw
        }
    }

    private static void chunk(DataOutputStream s, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        s.writeInt(data.length);
        s.write(t);
        s.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        s.writeInt((int) crc.getValue());
    }
}
