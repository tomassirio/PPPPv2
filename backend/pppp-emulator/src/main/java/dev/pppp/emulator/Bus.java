package dev.pppp.emulator;

/**
 * A console's memory as its CPU sees it (16-bit addresses, bytes 0-255): reads and writes go where the Game Boy's
 * would, so a write to $0000-$7FFF talks to the cartridge's mapper and $A000-$BFFF is save RAM while it is enabled.
 * Only valid on the console's own thread (EmulatorHost.withBus, afterFrame), between frames.
 */
public interface Bus {
    int getByte(int address);

    void setByte(int address, int value);
}
