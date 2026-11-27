package dev.pppp.service.game;

import dev.pppp.emulator.Bus;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * The new-game intro, PPPPv2's way, at insert:
 * <ul>
 * <li>Oak's speech (pret data/text/common_1.asm _OakText1, 2, 4, 5, and "what did you say your name was?"; 3 stays) is rewritten: each new text goes into
 * the free tail of its bank and the text_far that pointed at the old one ($16, address, bank) points at it.</li>
 * <li>Oak's picture (trainer class POKEMON_PROF, only drawn by the intro) wears TOM's head: oak-face.png (Oak's own
 * 56x56 pic, the same in all three games, with TOM's head hand-drawn over his: bald, wraparound shades, beard; his olive made brown)
 * as one LZ literal in a free Pics bank, its TrainerPicPointers entry moved there.</li>
 * <li>The player's ready-made names are TOM, PIB, PIBETTE, GANGBST; a rival left unnamed is LLOYD.</li>
 * <li>Crystal's "Are you a boy?" (engine/menus/init_gender.asm InitGender) offers a third answer, Apache Helicopter:
 * a wider menu (.MenuHeader coords, new .MenuData in bank $12's tail) and a new question. The game stores the
 * answer as wMenuCursorY - 1, so the helicopter leaves wPlayerGender 2: {@link #helicopter} turns that into a boy
 * (the Cable Club checks) and tells PlayerConsoles to walk them as the Sailor.</li>
 * </ul>
 * Every place is found by its bytes in the ROM, so a build that differs is left as it was. Must run after PlayerPic
 * (which copies Oak's original pic for a player who picked OAK).
 */
public final class Intro {
    private Intro() {}

    private static final int TEXT = 0x00, LINE = 0x4F, PARA = 0x51, CONT = 0x55, DONE = 0x57, PROMPT = 0x58,
            TEXT_FAR = 0x16, BANK = 0x4000, POKEMON_PROF = 0x0A, LZ_LONG_LITERAL = 0xE0, LZ_END = 0xFF, SIDE = 7,
            TILE = 16;
    /** wPlayerGender (pokecrystal11.sym; InitGender's "ld [wPlayerGender], a" is ea 72 d4). */
    private static final int PLAYER_GENDER = 0xD472, APACHE = 2;

    /** Old text (its first words, after the text byte) -> the new one. '\n' line, '\f' para, '~' cont. */
    private record Text(String old, String text, int end) {}

    private static final Text[] OAK = {
            new Text("Hello! Sorry to", "QUE HACES TITAN?\fWelcome to the\nworld of PPPPv2!\fMy name is TOM.\f"
                    + "People call me the\nEGG.", PROMPT),
            new Text("This world is in", "This world is in-\nhabited by Pibs~and creatures we~call #MON.@", 0x50),
            new Text("People and #MON\nlive", "Pibs and #MON\nlive together by\fsupporting each\nother.\f"
                    + "Some pibs play\nwith #MON, some~battle with them.", PROMPT),
            new Text("But we don", "But we don't know\neverything about~#MON yet.\fThere are still\nmany mysteries to~"
                    + "solve (not Riemann~anymore).\fThat's why I study\n#MON every day.", PROMPT),
            new Text("Now, what did you", "Wait, which Pib\nare you again?", PROMPT)};
    private static final Text GENDER = new Text("Are you a boy?",
            "Are you a boy,\na girl, or~something else?", DONE);

    /** InitGender's .MenuHeader (MENU_BACKUP_TILES, menu_coords 6, 4, 12, 9 as y1 x1 y2 x2) and .MenuData. */
    private static final byte[] GENDER_HEADER = bytes(0x40, 0x04, 0x06, 0x09, 0x0C),
            GENDER_COORDS = bytes(0x04, 0x00, 0x0B, 0x13), // the whole width for "Apache Helicopter", rows 4-11
            GENDER_MENU = concat(bytes(0xA1, 0x02), encode("Boy@Girl@")),
            NEW_GENDER_MENU = concat(bytes(0xA1, 0x03), encode("Boy@Girl@Apache Helicopter@"));

    /**
     * The player's ready-made names (engine/menus/intro_menu.asm NamePlayer .Chris/.Kris, Gold/Silver's one list):
     * .MenuData is flags, 5 items ("NEW NAME" first), the names, then the title; its header (MENU_BACKUP_TILES,
     * menu_coords 0, 0, 10, 11) points at it. At most 7 letters (PLAYER_NAME_LENGTH).
     */
    private static final byte[] NEW_NAME = encode("NEW NAME@"), NAMES_HEADER = bytes(0x40, 0x00, 0x00, 0x0B, 0x0A),
            NAMES = encode("TOM@PIB@PIBETTE@GANGBST@");
    private static final int NAME_ITEMS = 5;

    /**
     * The rival's name when the player leaves it blank (NameRival's .DefaultName: SILVER, Silver's GOLD), read by
     * "ld de, .DefaultName" (11 lo hi) right before it: moved to bank 3's free $7AF0, below AaaaCalls' scripts.
     */
    private static final byte[] RIVAL = encode("LLOYD@");
    private static final int RIVAL_AT = 3 * BANK + 0x7AF0 - BANK;

    private record Layout(int pics, int palettes, int picBank, int storedPicBank) {}

    // As PlayerPic: Crystal stores a pic bank as its index among the Pics banks + PICS_FIX ($5B is stored $25),
    // Gold/Silver as is (FixPicBank only moves $13, $14 and $1F). Both banks are empty past $7000.
    private static final Layout CRYSTAL = new Layout(rom(0x4A, 0x4000), rom(0x02, 0x70CE), 0x5B, 0x5B - 0x36),
            GOLD_SILVER = new Layout(rom(0x20, 0x4000), rom(0x02, 0x753D), 0x2C, 0x2C);

    /** Brown, RGB555, for Oak's olive: the beard reads as one. */
    private static final int BEARD = 0x70 >> 3 | (0x48 >> 3) << 5 | (0x28 >> 3) << 10;

    private static final byte[] FACE = face();

    /** The ROM with the intro rewritten, or the same array for another game. */
    public static byte[] patch(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        if (crystal == null) return rom;
        byte[] out = rom.clone();
        Map<Integer, Integer> tails = new HashMap<>(); // bank -> lowest address used from its end
        for (Text t : OAK) retext(out, t, tails);
        if (crystal) {
            retext(out, GENDER, tails);
            int menu = find(out, GENDER_MENU, 0);
            int header = menu < 0 ? -1 : find(out, concat(GENDER_HEADER, bytes(address(menu) & 0xFF, address(menu) >> 8)), 0);
            if (header >= 0 && header / BANK == menu / BANK) {
                int moved = place(out, header / BANK, NEW_GENDER_MENU, tails);
                if (moved >= 0) {
                    System.arraycopy(GENDER_COORDS, 0, out, header + 1, GENDER_COORDS.length);
                    out[header + 5] = (byte) address(moved);
                    out[header + 6] = (byte) (address(moved) >> 8);
                }
            }
        }
        for (int list = find(out, NEW_NAME, 0); list >= 0; list = find(out, NEW_NAME, list + 1)) {
            renames(out, list - 2, tails);
        }
        rival(out);
        Layout at = crystal ? CRYSTAL : GOLD_SILVER;
        int pic = place(out, at.picBank, FACE, tails);
        if (pic >= 0) {
            int pointer = at.pics + (POKEMON_PROF - 1) * 3; // dba_pic: bank, address
            out[pointer] = (byte) at.storedPicBank;
            out[pointer + 1] = (byte) address(pic);
            out[pointer + 2] = (byte) (address(pic) >> 8);
            int dark = at.palettes + POKEMON_PROF * 4 + 2; // TrainerPalettes: his second colour (beard, trousers)
            out[dark] = (byte) BEARD;
            out[dark + 1] = (byte) (BEARD >> 8);
        }
        return out;
    }

    /**
     * A frame hook (a no-op but in Crystal): the moment the gender menu leaves wPlayerGender at the helicopter, makes it
     * a boy and calls picked (once per answer).
     */
    public static Consumer<Bus> helicopter(Runnable picked) {
        return new Consumer<>() {
            Boolean crystal;

            @Override
            public void accept(Bus bus) {
                if (crystal == null) crystal = Boolean.TRUE.equals(PokecenterDig.crystal(bus));
                if (crystal && bus.wramBank() == 1 && bus.getByte(PLAYER_GENDER) == APACHE) {
                    bus.setByte(PLAYER_GENDER, 0);
                    picked.run();
                }
            }
        };
    }

    /** A ready-made names .MenuData at data, rewritten into its bank's tail and its header pointed there. */
    private static void renames(byte[] rom, int data, Map<Integer, Integer> tails) {
        if ((rom[data + 1] & 0xFF) != NAME_ITEMS) return;
        int end = data + 2;
        for (int i = 0; i < NAME_ITEMS; i++) while (rom[end++] != 0x50) {}
        int title = end;
        end++; // the title's displacement
        while (rom[end++] != 0x50) {}
        int header = find(rom, concat(NAMES_HEADER, bytes(address(data) & 0xFF, address(data) >> 8)), 0);
        if (header < 0 || header / BANK != data / BANK) return;
        byte[] menu = concat(bytes(rom[data], NAME_ITEMS), NEW_NAME, NAMES, java.util.Arrays.copyOfRange(rom, title, end));
        int moved = place(rom, data / BANK, menu, tails);
        if (moved < 0) return;
        rom[header + 5] = (byte) address(moved);
        rom[header + 6] = (byte) (address(moved) >> 8);
    }

    /** The rival's default name: LLOYD, wherever "ld de, .DefaultName" + call + ret is right before SILVER or GOLD. */
    private static void rival(byte[] rom) {
        for (byte[] old : new byte[][] {encode("SILVER@"), encode("GOLD@")}) {
            for (int at = find(rom, old, 0); at >= 0; at = find(rom, old, at + 1)) {
                int ld = at - 7;
                if (ld < 0 || at / BANK != RIVAL_AT / BANK || (rom[ld] & 0xFF) != 0x11 || (rom[at - 1] & 0xFF) != 0xC9
                        || ((rom[ld + 1] & 0xFF) | (rom[ld + 2] & 0xFF) << 8) != address(at)) continue;
                for (int i = 0; i < RIVAL.length; i++) if (rom[RIVAL_AT + i] != 0) return;
                System.arraycopy(RIVAL, 0, rom, RIVAL_AT, RIVAL.length);
                rom[ld + 1] = (byte) address(RIVAL_AT);
                rom[ld + 2] = (byte) (address(RIVAL_AT) >> 8);
                return;
            }
        }
    }

    /** Moves t's text to its bank's tail, rewritten; untouched when the old one or its single text_far is not found. */
    private static void retext(byte[] rom, Text t, Map<Integer, Integer> tails) {
        byte[] old = concat(bytes(TEXT), encode(t.old));
        for (int at = find(rom, old, 0); at >= 0; at = find(rom, old, at + 1)) {
            byte[] far = bytes(TEXT_FAR, address(at) & 0xFF, address(at) >> 8, at / BANK);
            int ref = find(rom, far, 0);
            if (ref < 0 || find(rom, far, ref + 1) >= 0) continue; // another text that starts alike, or ambiguous
            int moved = place(rom, at / BANK, concat(bytes(TEXT), encode(t.text), bytes(t.end)), tails);
            if (moved < 0) return;
            rom[ref + 1] = (byte) address(moved);
            rom[ref + 2] = (byte) (address(moved) >> 8);
            return;
        }
    }

    /** Writes data at the end of bank's free (zero) tail, below whatever this patch put there; its offset, or -1. */
    private static int place(byte[] rom, int bank, byte[] data, Map<Integer, Integer> tails) {
        int end = tails.getOrDefault(bank, (bank + 1) * BANK), start = end - data.length;
        if (start < bank * BANK) return -1;
        for (int i = start; i < end; i++) if (rom[i] != 0) return -1;
        System.arraycopy(data, 0, rom, start, data.length);
        tails.put(bank, start);
        return start;
    }

    /** pret's charmap for these texts; '\n' line, '\f' para, '~' cont, '@' the end of a string, '#' POKé. */
    static byte[] encode(String s) {
        byte[] out = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out[i] = (byte) (c >= 'A' && c <= 'Z' ? 0x80 + c - 'A' : c >= 'a' && c <= 'z' ? 0xA0 + c - 'a'
                    : c >= '0' && c <= '9' ? 0xF6 + c - '0' : switch (c) {
                        case ' ' -> 0x7F;
                        case '(' -> 0x9A;
                        case ')' -> 0x9B;
                        case '\'' -> 0xE0;
                        case '-' -> 0xE3;
                        case '?' -> 0xE6;
                        case '!' -> 0xE7;
                        case '.' -> 0xE8;
                        case ',' -> 0xF4;
                        case '#' -> 0x54;
                        case '@' -> 0x50;
                        case '\n' -> LINE;
                        case '\f' -> PARA;
                        case '~' -> CONT;
                        default -> throw new IllegalArgumentException("No tile for " + c);
                    });
        }
        return out;
    }

    /** The new texts, for the test: each line at most 18 tiles wide. */
    static String[] texts() {
        String[] all = new String[OAK.length + 1];
        for (int i = 0; i < OAK.length; i++) all[i] = OAK[i].text;
        all[OAK.length] = GENDER.text;
        return all;
    }

    /** oak-face.png as a 7x7 trainer pic (2bpp tiles in columns), compressed as one long LZ literal. */
    private static byte[] face() {
        BufferedImage img;
        try (InputStream in = Intro.class.getResourceAsStream("oak-face.png")) {
            img = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lzLiteral(tiles(img));
    }

    /** 56x56 pixels to 7x7 tiles in columns; the lightest shade is colour 0, the darkest 3. */
    static byte[] tiles(BufferedImage img) {
        byte[] out = new byte[SIDE * SIDE * TILE];
        for (int y = 0; y < SIDE * 8; y++) {
            for (int x = 0; x < SIDE * 8; x++) {
                int p = img.getRGB(x, y), light = (299 * (p >> 16 & 255) + 587 * (p >> 8 & 255) + 114 * (p & 255)) / 1000;
                int colour = light > 213 ? 0 : light > 131 ? 1 : light > 50 ? 2 : 3;
                int at = ((x / 8) * SIDE + y / 8) * TILE + (y % 8) * 2, bit = 0x80 >> (x % 8);
                if ((colour & 1) != 0) out[at] |= (byte) bit;
                if ((colour & 2) != 0) out[at + 1] |= (byte) bit;
            }
        }
        return out;
    }

    /** One LZ_LONG literal command (%111 000 nn nnnnnnnn, count - 1) and $ff. */
    static byte[] lzLiteral(byte[] data) {
        int n = data.length - 1;
        return concat(bytes(LZ_LONG_LITERAL | n >> 8, n & 0xFF), data, bytes(LZ_END));
    }

    static byte[] facePic() {
        return FACE;
    }

    private static int address(int offset) {
        return BANK + offset % BANK;
    }

    private static int rom(int bank, int address) {
        return bank * BANK + address - BANK;
    }

    private static int find(byte[] rom, byte[] what, int from) {
        outer:
        for (int i = from; i + what.length <= rom.length; i++) {
            for (int j = 0; j < what.length; j++) if (rom[i + j] != what[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        n = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, n, p.length);
            n += p.length;
        }
        return out;
    }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }
}
