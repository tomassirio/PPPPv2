package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AAAA's Mail lands in a real game's PC mailbox: every parked Gold, Silver or Crystal game in the repo's data/ (not in
 * git; skipped without them) gets three more Mails, in the mailbox and its backup, and in the battery save on disk.
 */
class AaaaMailTest {
    @Test
    void aMailIsTwoLinesAroundNextThenAuthorIdSpeciesAndItem() {
        byte[] m = AaaaMail.mail("Thank you for", "playing PPPP v1!");
        assertThat(m).hasSize(0x2F);
        assertThat(m[0] & 0xFF).isEqualTo(0x93); // T
        assertThat(m[13] & 0xFF).as("line 1 padded with spaces, so line 2 still prints").isEqualTo(0x7F);
        assertThat(m[16] & 0xFF).isEqualTo(0x4E); // <NEXT>
        assertThat(m[17] & 0xFF).isEqualTo(0xAF); // p
        assertThat(m[32] & 0xFF).isEqualTo(0xE7); // !
        assertThat(new int[] {m[33] & 0xFF, m[36] & 0xFF, m[37] & 0xFF}).containsExactly(0x80, 0x80, 0x50); // AAAA@
        assertThat(m[0x2D] & 0xFF).isEqualTo(144); // Articuno
        assertThat(m[0x2E] & 0xFF).isEqualTo(0xB7); // Portrait Mail
        for (String[] t : AaaaMail.TEXT) {
            assertThat(t[0]).hasSizeLessThanOrEqualTo(16);
            assertThat(t[1]).hasSizeLessThanOrEqualTo(16);
        }
    }

    static Stream<Path> states() throws Exception {
        return PokecenterDigTest.states();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no parked states in data/ (CI): nothing to run
    @MethodSource("states")
    void landsInTheMailboxOfARealGame(Path state) throws Exception {
        String cart = state.getFileName().toString().replace(".state", "");
        Path rom = Path.of("../../data/roms").resolve(cart + ".gbc");
        var found = PokecenterDig.game(Files.readAllBytes(rom));
        assumeTrue(found.isPresent(), "not Gold, Silver or Crystal");
        var game = found.get();
        Path sav = Files.createTempDirectory("mail").resolve("game.sav");
        Path original = state.resolveSibling(cart + ".sav");
        if (Files.exists(original)) Files.copy(original, sav);
        EmulatorHost host = new EmulatorHost();
        host.insert("game", rom, sav);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            String why = host.withBus(bus -> PokecenterDig.busy(game, bus));
            for (int i = 0; i < 200 && why != null && !why.equals("nowhere yet"); i++) { // title screen or intro: skipped below
                if (why.equals("busy")) Thread.sleep(20); // Crystal swaps WRAM banks many times a second: a moment later
                else host.press(Button.B, 6, 30); // a menu or dialog left open: close it
                why = host.withBus(bus -> PokecenterDig.busy(game, bus));
            }
            assumeTrue(why == null, state.getParent().getFileName() + ": " + why);
            int before = host.withBus(bus -> {
                bus.setByte(0x0000, 0x0A);
                bus.setByte(0x4000, 0);
                int n = bus.getByte(AaaaMail.MAILBOX_COUNT);
                bus.setByte(0x0000, 0x00);
                return n;
            });
            Boolean written = host.withBus(AaaaMail::write);
            assumeTrue(Boolean.TRUE.equals(written), state.getParent().getFileName() + ": never saved, or a full mailbox");
            assertThat(before).isBetween(0, 7);

            int[] after = host.withBus(bus -> {
                bus.setByte(0x0000, 0x0A);
                bus.setByte(0x4000, 0);
                int[] r = {bus.getByte(AaaaMail.MAILBOX_COUNT), bus.getByte(AaaaMail.BACKUP),
                        bus.getByte(AaaaMail.MAILBOXES + before * 0x2F), bus.getByte(AaaaMail.MAILBOXES + (before + AaaaMail.TEXT.length - 1) * 0x2F + 0x2E)};
                bus.setByte(0x0000, 0x00);
                return r;
            });
            System.out.println("AAAA's Mail delivered in " + state.getParent().getFileName() + "'s " + cart + " (mailbox " + before + " -> " + after[0] + ")");
            assertThat(after[0]).as("mailbox count").isEqualTo(before + AaaaMail.TEXT.length);
            assertThat(after[1]).as("backup count").isEqualTo(before + AaaaMail.TEXT.length);
            assertThat(after[2]).as("first Mail's first letter").isEqualTo(0x93); // T
            assertThat(after[3]).as("last Mail's item").isEqualTo(0xB7);

            Thread.sleep(2000); // the game keeps running with it
            host.flushBattery();
            byte[] saved = Files.readAllBytes(sav);
            assertThat(saved[AaaaMail.MAILBOX_COUNT - 0xA000] & 0xFF).as("in the battery save").isEqualTo(before + AaaaMail.TEXT.length);
            String still = host.withBus(bus -> PokecenterDig.busy(game, bus));
            // Still in the game (a phone call or an event may have started meanwhile), not reset to the title screen.
            assertThat(still).as("still in the game").isNotEqualTo("nowhere yet");
        } finally {
            host.shutdown();
        }
    }
}
