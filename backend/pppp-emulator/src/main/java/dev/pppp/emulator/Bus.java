package dev.pppp.emulator;

/**
 * A console's memory as its CPU sees it (16-bit addresses, bytes 0-255): reads and writes go where the Game Boy's
 * would, so a write to $0000-$7FFF talks to the cartridge's mapper and $A000-$BFFF is save RAM while it is enabled.
 * Only valid on the console's own thread (EmulatorHost.withBus, afterFrame), between frames.
 */
public interface Bus {
    int getByte(int address);

    void setByte(int address, int value);

    /**
     * The WRAM bank at D000-DFFF: SVBK's 1-7 in a Game Boy Color game, 1 in an original Game Boy one (no banks).
     * SVBK reads back as value | ~7, so bank 7 reads FF just like DMG mode does; KEY1 (FF4D) reads FF only in DMG
     * mode. (Taking bank 7 for bank 1 once read Crystal's menu backups as the map and pulled a link cable mid-trade.)
     */
    default int wramBank() {
        if (getByte(0xFF4D) == 0xFF) return 1;
        int bank = getByte(0xFF70) & 7;
        return bank == 0 ? 1 : bank;
    }
}
