package dev.pppp.service.game;

import dev.pppp.emulator.Bus;

/**
 * The save a link trade makes (pret engine/menus/save.asm SaveAfterLinkTrade: SavePokemonData, SaveChecksum,
 * SaveBackupPokemonData, SaveBackupChecksum), so a Wonder Trade sticks: Reset boots from the battery save, and without
 * this the Pokémon sent would come back (while its copy waits in the pool). Only the Pokémon data is saved (party,
 * Pokédex, current box): the player's position and items stay as they last saved them, as after a real trade.
 * <p>
 * wPokemonData .. wPokemonDataEnd is copied over sPokemonData and sBackupPokemonData. The save's checksums are plain
 * 16-bit sums (Checksum: e + byte, d + carry) over regions that include those copies; Gold and Silver sum their backup
 * over several sections, Crystal over one, so instead of summing again each checksum moves by (new bytes' sum - old
 * bytes' sum), which gives what the game would compute in either layout. Written between frames while the player is
 * free in the overworld (WRAM bank 1 in, save RAM closed), opening and closing save RAM as OpenSRAM / CloseSRAM do.
 * Addresses from pret pokecrystal11.sym / pokegold.sym (Silver shares Gold's). A copy whose check values are not
 * SAVE_CHECK_VALUE_1 / _2 (never saved there) is left alone.
 */
final class TradeSave {
    private TradeSave() {}

    /** sram: {bank, address}. pokemon / pokemonEnd: wPokemonData, wPokemonDataEnd (WRAM). */
    record Layout(int pokemon, int pokemonEnd, int[] data, int[] checksum, int[] check1, int[] check2,
                  int[] backupData, int[] backupChecksum, int[] backupCheck1, int[] backupCheck2) {}

    static final Layout CRYSTAL = new Layout(0xDCD7, 0xDFF5,
            new int[] {1, 0xA865}, new int[] {1, 0xAD0D}, new int[] {1, 0xA008}, new int[] {1, 0xAD0F},
            new int[] {0, 0xBA65}, new int[] {0, 0xBF0D}, new int[] {0, 0xB208}, new int[] {0, 0xBF0F}),
            GOLD_SILVER = new Layout(0xDA22, 0xDF01,
            new int[] {1, 0xA88A}, new int[] {1, 0xAD69}, new int[] {1, 0xA008}, new int[] {1, 0xAD6B},
            new int[] {0, 0xB0E8}, new int[] {3, 0xBE6D}, new int[] {3, 0xBE38}, new int[] {3, 0xBE6F});

    private static final int RAMG = 0x0000, RAMB = 0x4000, SRAM_ENABLE = 0x0A, SRAM_DISABLE = 0x00,
            SAVE_CHECK_VALUE_1 = 99, SAVE_CHECK_VALUE_2 = 0x7F;

    static Layout layout(PokemonGen2 game) {
        return game.crystal() ? CRYSTAL : GOLD_SILVER;
    }

    /** Whether the game has been saved (the main copy's check values are there): a Wonder Trade needs that first. */
    static boolean saved(Bus bus, PokemonGen2 game) {
        Layout at = layout(game);
        bus.setByte(RAMG, SRAM_ENABLE);
        try {
            return valid(bus, at.check1(), at.check2());
        } finally {
            bus.setByte(RAMG, SRAM_DISABLE);
        }
    }

    /** Saves the Pokémon data into the main copy and the backup. False (nothing written) when the game was never saved. */
    static boolean save(Bus bus, PokemonGen2 game) {
        Layout at = layout(game);
        byte[] now = new byte[at.pokemonEnd() - at.pokemon()];
        for (int i = 0; i < now.length; i++) now[i] = (byte) bus.getByte(at.pokemon() + i);
        bus.setByte(RAMG, SRAM_ENABLE);
        try {
            if (!valid(bus, at.check1(), at.check2())) return false;
            copy(bus, now, at.data(), at.checksum());
            if (valid(bus, at.backupCheck1(), at.backupCheck2())) copy(bus, now, at.backupData(), at.backupChecksum());
            return true;
        } finally {
            bus.setByte(RAMG, SRAM_DISABLE);
        }
    }

    /** Writes bytes at data and moves the little-endian checksum by their change. */
    private static void copy(Bus bus, byte[] bytes, int[] data, int[] checksum) {
        bus.setByte(RAMB, data[0]);
        int delta = 0;
        for (int i = 0; i < bytes.length; i++) {
            int was = bus.getByte(data[1] + i), b = bytes[i] & 0xFF;
            delta += b - was;
            bus.setByte(data[1] + i, b);
        }
        bus.setByte(RAMB, checksum[0]);
        int sum = (bus.getByte(checksum[1]) | bus.getByte(checksum[1] + 1) << 8) + delta;
        bus.setByte(checksum[1], sum & 0xFF);
        bus.setByte(checksum[1] + 1, sum >> 8 & 0xFF);
    }

    private static boolean valid(Bus bus, int[] check1, int[] check2) {
        bus.setByte(RAMB, check1[0]);
        if (bus.getByte(check1[1]) != SAVE_CHECK_VALUE_1) return false;
        bus.setByte(RAMB, check2[0]);
        return bus.getByte(check2[1]) == SAVE_CHECK_VALUE_2;
    }
}
