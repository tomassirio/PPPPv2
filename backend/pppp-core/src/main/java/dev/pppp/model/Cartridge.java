package dev.pppp.model;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * A ROM on the data volume. The id is the sanitized file name stem, so it is stable and filesystem-safe. title is the
 * header's; name is what players read ("Pokémon Crystal"), the title itself for games not in {@link #NAMES}.
 */
public record Cartridge(String id, String title, String name, long size, boolean cgb) {
    public static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    /** Bytes up to the end of the cartridge header. */
    public static final int HEADER_SIZE = 0x150;

    /** Title and CGB flag from the first {@link #HEADER_SIZE} bytes of a ROM. */
    public static Cartridge fromHeader(String id, byte[] header, long size) {
        boolean cgb = (header[0x143] & 0x80) != 0;
        // Colour-era headers keep 11 title bytes; 0x13F..0x142 is the manufacturer code ("BYTE" in PM_CRYSTAL).
        int end = cgb ? 0x13F : 0x144;
        String title = new String(header, 0x134, end - 0x134, StandardCharsets.US_ASCII).replaceAll("[^\\x20-\\x7e]", "").trim();
        return new Cartridge(id, title, NAMES.getOrDefault(title, title), size, cgb);
    }

    private static final java.util.Map<String, String> NAMES = java.util.Map.of(
            "POKEMON RED", "Pokémon Red", "POKEMON BLUE", "Pokémon Blue", "POKEMON YELLOW", "Pokémon Yellow",
            "POKEMON_GLD", "Pokémon Gold", "POKEMON_SLV", "Pokémon Silver", "PM_CRYSTAL", "Pokémon Crystal");

    /** Header checksum at 0x14D over 0x134..0x14C; the boot ROM refuses carts where it is wrong. */
    public static boolean headerChecksumOk(byte[] rom) {
        if (rom.length < HEADER_SIZE) return false;
        int x = 0;
        for (int i = 0x134; i <= 0x14C; i++) x = x - rom[i] - 1;
        return (x & 0xFF) == (rom[0x14D] & 0xFF);
    }
}
