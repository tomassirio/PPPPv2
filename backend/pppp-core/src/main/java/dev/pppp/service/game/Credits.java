package dev.pppp.service.game;

/**
 * The end credits name one person: every name in them (pret data/credits_strings.asm: the CreditsStringsPointers
 * entries before .End, 70 in Crystal, 50 in Gold and Silver; titles such as GAME SCENARIO stay) points at a single
 * "TOMÁS SIRIO", written over the first name's bytes (SATOSHI TAJIRI's, 18 long). The font has no Á, so the Ä tile
 * (charmap $c0, only used by European mail) is redrawn as one: A with an acute accent, in every copy of the font.
 * The table is found from the first two names' addresses, so it works wherever the build put it.
 */
public final class Credits {
    private Credits() {}

    private static final String FIRST = "   SATOSHI TAJIRI@", SECOND = "   JUNICHI MASUDA@";
    /** Centred like the others (20 columns); Á is the Ä tile. */
    private static final String NAME = "    TOMÁS SIRIO@";
    private static final int CRYSTAL_NAMES = 70, GOLD_NAMES = 50, A_DIAERESIS = 0xC0, FONT_START = 0x80;
    /** 1bpp rows of A, Ä and the new Á (gfx/font/font.png). */
    private static final byte[] A = bytes(0x10, 0x28, 0x28, 0x44, 0x7C, 0x82, 0x82, 0x00),
            UMLAUT = bytes(0x28, 0x10, 0x28, 0x44, 0x7C, 0x82, 0x82, 0x00),
            ACUTE = bytes(0x08, 0x10, 0x28, 0x44, 0x7C, 0x82, 0x82, 0x00);

    /** The ROM with the credits renamed, or the same array when it is not Gold, Silver or Crystal as pret has them. */
    public static byte[] patch(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        if (crystal == null) return rom;
        int first = find(rom, encode(FIRST), 0), second = first < 0 ? -1 : find(rom, encode(SECOND), 0);
        if (first < 0 || second < 0 || first / 0x4000 != second / 0x4000) return rom;
        int a = address(first), b = address(second);
        int table = find(rom, bytes(a & 0xFF, a >> 8, b & 0xFF, b >> 8), 0);
        if (table < 0) return rom;
        byte[] out = rom.clone();
        byte[] name = encode(NAME);
        System.arraycopy(name, 0, out, first, name.length);
        int names = crystal ? CRYSTAL_NAMES : GOLD_NAMES;
        for (int i = 0; i < names; i++) {
            out[table + i * 2] = (byte) a;
            out[table + i * 2 + 1] = (byte) (a >> 8);
        }
        int tile = (A_DIAERESIS - FONT_START) * 8;
        for (int at = find(out, UMLAUT, 0); at >= 0; at = find(out, UMLAUT, at + 1)) {
            if (at >= tile && same(out, at - tile, A)) System.arraycopy(ACUTE, 0, out, at, ACUTE.length);
        }
        return out;
    }

    /** pret's charmap for the credits: capitals, space, Á (as Ä) and the terminator. */
    static byte[] encode(String s) {
        byte[] out = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out[i] = (byte) (c >= 'A' && c <= 'Z' ? 0x80 + c - 'A' : c == ' ' ? 0x7F : c == 'Á' ? A_DIAERESIS : c == '@' ? 0x50 : 0xE6);
        }
        return out;
    }

    private static int address(int offset) {
        return offset < 0x4000 ? offset : 0x4000 + offset % 0x4000;
    }

    private static boolean same(byte[] rom, int at, byte[] what) {
        for (int i = 0; i < what.length; i++) if (rom[at + i] != what[i]) return false;
        return true;
    }

    private static int find(byte[] rom, byte[] what, int from) {
        for (int i = from; i + what.length <= rom.length; i++) if (same(rom, i, what)) return i;
        return -1;
    }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }
}
