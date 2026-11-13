package dev.pppp.emulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkCableTest {

    @TempDir Path dir;
    final EmulatorHost master = new EmulatorHost();
    final EmulatorHost slave = new EmulatorHost();

    @AfterEach
    void off() {
        master.shutdown();
        slave.shutdown();
    }

    @Test
    void linkedConsolesSwapBytesUntilUnplugged() throws Exception {
        master.insert("m", TestRom.write(dir, "master.gb", TestRom.LINK_MASTER), dir.resolve("m.sav"));
        slave.insert("s", TestRom.write(dir, "slave.gb", TestRom.LINK_SLAVE), dir.resolve("s.sav"));
        master.powerOn();
        slave.powerOn();
        master.press(Button.WAIT, 30, 0); // past the boot ROM
        slave.press(Button.WAIT, 30, 0);

        EmulatorHost.link(slave, master); // the clock side need not be the driver
        assertThat(master.isLinked()).isTrue();
        assertThatThrownBy(() -> EmulatorHost.link(master, new EmulatorHost())).isInstanceOf(IllegalStateException.class);

        master.press(Button.A, 6, 6);
        assertThat(master.readByte(0xC000)).isEqualTo(0x99);
        assertThat(slave.readByte(0xC000)).isEqualTo(0x42);
        master.press(Button.A, 6, 6);
        assertThat(master.readByte(0xC001)).isEqualTo(2);
        assertThat(slave.readByte(0xC001)).isEqualTo(2);

        EmulatorHost.unlink(master);
        assertThat(slave.isLinked()).isFalse();
        master.press(Button.A, 6, 6); // nobody on the other end: the line reads all ones
        assertThat(master.readByte(0xC001)).isEqualTo(3);
        assertThat(master.readByte(0xC000)).isEqualTo(0xFF);
        slave.press(Button.WAIT, 6, 0); // the slave runs on its own thread again, still waiting for a clock
        assertThat(slave.readByte(0xC001)).isEqualTo(2);
    }

    @Test
    void withNoCableTheLineReadsAllOnes() throws Exception {
        master.insert("m", TestRom.write(dir, "master.gb", TestRom.LINK_MASTER), dir.resolve("m.sav"));
        master.powerOn();
        master.press(Button.WAIT, 30, 0);
        master.press(Button.A, 6, 6);
        assertThat(master.readByte(0xC001)).isEqualTo(1);
        assertThat(master.readByte(0xC000)).isEqualTo(0xFF);
    }

    @Test
    void poweringOffOneEndPullsTheCable() throws Exception {
        master.insert("m", TestRom.write(dir, "master.gb", TestRom.LINK_MASTER), dir.resolve("m.sav"));
        slave.insert("s", TestRom.write(dir, "slave.gb", TestRom.LINK_SLAVE), dir.resolve("s.sav"));
        master.powerOn();
        slave.powerOn();
        EmulatorHost.link(master, slave);

        slave.powerOff();
        assertThat(master.isLinked()).isFalse();
        master.press(Button.WAIT, 2, 0); // still running
    }
}
