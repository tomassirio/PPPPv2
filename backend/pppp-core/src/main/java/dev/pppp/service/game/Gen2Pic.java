package dev.pppp.service.game;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Gold/Silver/Crystal pictures from the ROM: pret's LZ format (home/decompress.asm) and front pics laid out as
 * the game does (engine/gfx/load_pics.asm _GetFrontpic, PadFrontpic; engine/gfx/place_graphic.asm).
 */
final class Gen2Pic {
    private static final int LZ_END = 0xFF, LZ_LONG = 7, TILE = 16, BOX = 7; // 2bpp tile bytes; 7x7 tiles

    private Gen2Pic() {}

    /**
     * Decompresses the data at rom[at] (Decompress in home/decompress.asm). A command byte is %cccnnnnn: c the
     * command, n + 1 the count; c = 7 (LZ_LONG) means %111cccnn nnnnnnnn, a 10-bit count. Commands: 0 literal
     * bytes, 1 one byte repeated, 2 two bytes alternated, 3 zeros, then copies from the output so far: 4 as is,
     * 5 bit-flipped, 6 backwards (7 under LZ_LONG acts as 4). A copy's offset is one byte %1ooooooo (o + 1 back from
     * the current end) or two, big-endian, from the start of the output. $ff ends the data.
     */
    static byte[] decompress(byte[] rom, int at) {
        byte[] out = new byte[0x1000];
        int n = 0, p = at;
        while ((rom[p] & 0xFF) != LZ_END) {
            int b = rom[p] & 0xFF, cmd = b >> 5, len;
            if (cmd == LZ_LONG) {
                cmd = b >> 2 & 7;
                len = ((b & 3) << 8 | rom[p + 1] & 0xFF) + 1;
                p += 2;
            } else {
                len = (b & 0x1F) + 1;
                p++;
            }
            if (n + len > out.length) out = Arrays.copyOf(out, Math.max(out.length * 2, n + len));
            switch (cmd) {
                case 0 -> {
                    System.arraycopy(rom, p, out, n, len);
                    p += len;
                    n += len;
                }
                case 1 -> {
                    Arrays.fill(out, n, n + len, rom[p++]);
                    n += len;
                }
                case 2 -> {
                    for (int i = 0; i < len; i++) out[n++] = rom[p + (i & 1)];
                    p += 2;
                }
                case 3 -> n += len; // already zero
                default -> {
                    int o = rom[p] & 0xFF, from;
                    if ((o & 0x80) != 0) {
                        from = n - (o & 0x7F) - 1;
                        p++;
                    } else {
                        from = o << 8 | rom[p + 1] & 0xFF;
                        p += 2;
                    }
                    for (int i = 0; i < len; i++) {
                        out[n++] = switch (cmd) {
                            case 5 -> (byte) (Integer.reverse(out[from + i] & 0xFF) >>> 24);
                            case 6 -> out[from - i];
                            default -> out[from + i];
                        };
                    }
                }
            }
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * Colour indices [y][x] of a 56x56 front pic from its decompressed tiles: the first size x size tiles, column
     * by column, padded into the 7x7 box as PadFrontpic does (a 6x6 pic sits right of an empty column and under an
     * empty row; a 5x5 pic also has an empty column to its right and two empty rows above).
     */
    static int[][] frontpic(byte[] tiles, int size) {
        if (size < 5 || size > BOX || tiles.length < size * size * TILE) {
            throw new IllegalArgumentException("Bad pic size " + size);
        }
        int[][] px = new int[BOX * 8][BOX * 8];
        int left = size == BOX ? 0 : 1, top = BOX - size;
        for (int t = 0; t < size * size; t++) {
            int x0 = (left + t / size) * 8, y0 = (top + t % size) * 8;
            for (int y = 0; y < 8; y++) {
                int lo = tiles[t * TILE + 2 * y] & 0xFF, hi = tiles[t * TILE + 2 * y + 1] & 0xFF;
                for (int x = 0; x < 8; x++) px[y0 + y][x0 + x] = (lo >> 7 - x & 1) | (hi >> 7 - x & 1) << 1;
            }
        }
        return px;
    }

    /** PNG of colour indices in argb; colour 0 touching the border (the background) is transparent. */
    static byte[] png(int[][] px, int[] argb) {
        int h = px.length, w = px[0].length;
        boolean[][] outside = GenOnePic.outside(px);
        // The game draws smaller pics bottom-aligned in the 7x7-tile box (they stand on the ground); as an icon the
        // Pokémon is centred on its drawn pixels instead.
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (outside[y][x]) continue;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }
        int dx = maxX < 0 ? 0 : (w - (maxX - minX + 1)) / 2 - minX, dy = maxY < 0 ? 0 : (h - (maxY - minY + 1)) / 2 - minY;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!outside[y][x]) img.setRGB(x + dx, y + dy, argb[px[y][x]]);
            }
        }
        return GenOnePic.encode(img);
    }

    /** An RGB555 colour (little-endian, as palettes are stored) as opaque ARGB. */
    static int rgb(byte[] rom, int at) {
        int c = (rom[at] & 0xFF) | (rom[at + 1] & 0xFF) << 8;
        return 0xFF000000 | (c & 31) * 255 / 31 << 16 | (c >> 5 & 31) * 255 / 31 << 8 | (c >> 10 & 31) * 255 / 31;
    }

    /** A Game Boy Color 5-bit colour as 0xRRGGBB. */
    static int rgb555(int r, int g, int b) {
        return (r * 255 / 31) << 16 | (g * 255 / 31) << 8 | b * 255 / 31;
    }
}
