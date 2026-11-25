package dev.pppp.service.game;

import java.util.HashMap;
import java.util.Map;

/**
 * The player's picture from the front matches the trainer they picked (PlayerSprites): their trainer class's pic
 * (gfx/trainers, LZ-compressed, 7x7 tiles in columns) and palette (TrainerPalettes, 2 colours per class).
 * <ul>
 * <li>Crystal: the trainer card, the intro and the Hall of Fame draw ChrisPic / KrisPic (engine/gfx/player_gfx.asm,
 * uncompressed 7x7 in columns): both are overwritten with the class's pic, and PlayerPalette (TrainerPalettes entry 0,
 * which wTrainerClass CHRIS reads) with its colours. Kris keeps entry 1, which is Falkner's (the games share it).</li>
 * <li>Gold/Silver: the intro and the Hall of Fame draw trainer class CAL: its TrainerPicPointers and TrainerPalettes
 * entries point at the class's; the trainer card has its own 5x7 picture (ChrisPicAndTrainerCardGFX, tiles in rows),
 * the class pic's middle five columns, and reads entry 0 ("xor a ; CHRIS", _CGB_TrainerCard) for its colours.</li>
 * <li>Link battles draw the opponent as CAL in every game: {@link #linkOpponent} points CAL at the other player's
 * class for as long as the cable is in.</li>
 * </ul>
 * TrainerPicPointers entries (dba_pic: bank as FixPicBank stores it, address) start at FALKNER, class 1; TrainerPalettes
 * at class 0. Class ids from constants/trainer_constants.asm (the same in pokegold and pokecrystal); addresses from
 * pret's pokecrystal11.sym, pokegold.sym, pokesilver.sym.
 */
public final class PlayerPic {
    private PlayerPic() {}

    private record Layout(boolean crystal, int pics, int palettes, int chrisPic, int krisPic, int cardPic) {}

    private static final Layout CRYSTAL = new Layout(true, rom(0x4A, 0x4000), rom(0x02, 0x70CE), rom(0x22, 0x48A9),
            rom(0x22, 0x4BB9), -1),
            GOLD_SILVER = new Layout(false, rom(0x20, 0x4000), rom(0x02, 0x753D), -1, -1, rom(0x09, 0x547F));

    private static final int CAL = 0x0C, TILE = 16, SIDE = 7, PIC = SIDE * SIDE * TILE, CARD_COLUMNS = 5, POINTER = 3,
            PALETTE = 4;

    /** The trainer class drawn for each PlayerSprites choice that has one (trainer_constants.asm). */
    private static final Map<String, Integer> CLASSES = Map.ofEntries(
            Map.entry("RED", 0x3F), Map.entry("BLUE", 0x40), Map.entry("RIVAL", 0x09), Map.entry("OAK", 0x0A),
            Map.entry("FALKNER", 0x01), Map.entry("WHITNEY", 0x02), Map.entry("BUGSY", 0x03), Map.entry("MORTY", 0x04),
            Map.entry("PRYCE", 0x05), Map.entry("JASMINE", 0x06), Map.entry("CHUCK", 0x07), Map.entry("CLAIR", 0x08),
            Map.entry("WILL", 0x0B), Map.entry("BRUNO", 0x0D), Map.entry("KAREN", 0x0E), Map.entry("KOGA", 0x0F),
            Map.entry("LANCE", 0x10), Map.entry("BROCK", 0x11), Map.entry("MISTY", 0x12), Map.entry("SURGE", 0x13),
            Map.entry("SCIENTIST", 0x14), Map.entry("ERIKA", 0x15), Map.entry("YOUNGSTER", 0x16),
            Map.entry("LASS", 0x19), Map.entry("JANINE", 0x1A), Map.entry("COOLTRAINER_M", 0x1B),
            Map.entry("COOLTRAINER_F", 0x1C), Map.entry("BEAUTY", 0x1D), Map.entry("ROCKET", 0x1F),
            Map.entry("GENTLEMAN", 0x20), Map.entry("TEACHER", 0x22), Map.entry("SABRINA", 0x23),
            Map.entry("BUG_CATCHER", 0x24), Map.entry("FISHER", 0x25), Map.entry("SAILOR", 0x28),
            Map.entry("SUPER_NERD", 0x29), Map.entry("ROCKER", 0x2B), Map.entry("BIKER", 0x2D),
            Map.entry("BLAINE", 0x2E), Map.entry("BLACK_BELT", 0x32), Map.entry("SAGE", 0x38),
            Map.entry("POKEFAN_M", 0x3B), Map.entry("KIMONO_GIRL", 0x3C), Map.entry("TWIN", 0x3D),
            Map.entry("POKEFAN_F", 0x3E), Map.entry("OFFICER", 0x41), Map.entry("ROCKET_GIRL", 0x42));

    /** The trainer class for a PlayerSprites key, or null (Chris, Kris, Elm, Bill, Kurt, the elderly, Nurse Joy: none). */
    static Integer classOf(String key) {
        return key == null ? null : CLASSES.get(key);
    }

    /** The ROM with the player's front picture as key's trainer class; untouched when it has none or for another game. */
    public static byte[] patch(byte[] rom, String key) {
        Layout at = layout(rom);
        Integer cls = classOf(key);
        byte[] pic = at == null || cls == null ? null : pic(rom, at, cls);
        if (pic == null) return rom;
        byte[] out = rom.clone();
        if (at.crystal) {
            System.arraycopy(pic, 0, out, at.chrisPic, PIC);
            System.arraycopy(pic, 0, out, at.krisPic, PIC);
        } else {
            System.arraycopy(rom, pointer(at, cls), out, pointer(at, CAL), POINTER);
            System.arraycopy(rom, at.palettes + cls * PALETTE, out, at.palettes + CAL * PALETTE, PALETTE);
            byte[] card = card(pic);
            System.arraycopy(card, 0, out, at.cardPic, card.length);
        }
        System.arraycopy(rom, at.palettes + cls * PALETTE, out, at.palettes, PALETTE); // PlayerPalette
        return out;
    }

    /**
     * The bytes that make a running console (cartridge rom, the cartridge's own bytes) draw its link opponent as their
     * trainer peerKey picked; null peerKey (the cable out): as it was, which in Gold/Silver is the player's own key.
     */
    public static Map<Integer, Integer> linkOpponent(byte[] rom, String peerKey, String ownKey) {
        Layout at = layout(rom);
        if (at == null) return Map.of();
        Integer cls = classOf(peerKey);
        if (peerKey == null && !at.crystal) cls = classOf(ownKey); // Gold/Silver: CAL is the player again
        int source = cls == null ? CAL : cls;
        var out = new HashMap<Integer, Integer>();
        for (int i = 0; i < POINTER; i++) out.put(pointer(at, CAL) + i, rom[pointer(at, source) + i] & 0xFF);
        for (int i = 0; i < PALETTE; i++) out.put(at.palettes + CAL * PALETTE + i, rom[at.palettes + source * PALETTE + i] & 0xFF);
        return out;
    }

    /** The class's pic, decompressed (7x7 tiles in columns), or null when it is not that size. */
    static byte[] pic(byte[] rom, Layout at, int cls) {
        int p = pointer(at, cls);
        int bank = rom[p] & 0xFF, address = (rom[p + 1] & 0xFF) | (rom[p + 2] & 0xFF) << 8;
        if (address < 0x4000 || address >= 0x8000) return null;
        byte[] tiles = Gen2Pic.decompress(rom, rom(picBank(at, bank), address));
        return tiles == null || tiles.length < PIC ? null : java.util.Arrays.copyOf(tiles, PIC);
    }

    /** Gold/Silver's card picture: the pic's middle five columns, its tiles in rows (5 wide, 7 high). */
    static byte[] card(byte[] pic) {
        byte[] out = new byte[CARD_COLUMNS * SIDE * TILE];
        for (int col = 0; col < CARD_COLUMNS; col++) {
            for (int row = 0; row < SIDE; row++) {
                System.arraycopy(pic, ((col + 1) * SIDE + row) * TILE, out, (row * CARD_COLUMNS + col) * TILE, TILE);
            }
        }
        return out;
    }

    private static int pointer(Layout at, int cls) {
        return at.pics + (cls - 1) * POINTER;
    }

    /** As PokemonGen2.picBank: FixPicBank's table (Gold/Silver), Crystal's PicsBanks (stored + PICS_FIX $36). */
    private static int picBank(Layout at, int stored) {
        if (at.crystal) return stored + 0x36;
        return switch (stored) {
            case 0x13 -> 0x1F;
            case 0x14 -> 0x20;
            case 0x1F -> 0x2E;
            default -> stored;
        };
    }

    private static Layout layout(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        return crystal == null ? null : crystal ? CRYSTAL : GOLD_SILVER;
    }

    private static int rom(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }
}
