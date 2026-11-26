package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Two real parked games in data/ (not in git; skipped without them), dug to the battle receptionist and linked: the
 * second player talks to her 25 s after the first, far past the game's own 8.5 s, and both still reach the Colosseum.
 */
class LinkWaitTest {
    private static final Path DATA = Path.of("../../data");
    private static final String CRYSTAL = "pokemon-crystal-version-usa-europe-rev-1",
            GOLD = "pokemon-gold-version-usa-europe-sgb-enhanced-gb-compatible";

    private record Player(EmulatorHost host, PokecenterDig.Game game) {
        String map() {
            int g = game.at().mapGroup();
            int[] m = host.withBus(bus -> new int[] {bus.getByte(g), bus.getByte(g + 1)});
            return game.maps().mapId(m[0], m[1]);
        }
    }

    private static Player boot(String user, String cart) throws Exception {
        return boot(user, cart, "BATTLE");
    }

    private static Player boot(String user, String cart, String kind) throws Exception {
        Path rom = DATA.resolve("roms/" + cart + ".gbc"), saves = DATA.resolve("saves/" + user);
        assumeTrue(Files.exists(rom) && Files.exists(saves.resolve(cart + ".state")), "no " + cart + " save for " + user);
        Path sav = Files.createTempDirectory("link-wait").resolve("game.sav");
        Files.copy(saves.resolve(cart + ".sav"), sav);
        EmulatorHost host = new EmulatorHost();
        host.insert(cart, rom, sav, PokecenterDig::patch);
        host.afterFrame(PokecenterDig.patientReceptionist(host::isLinked));
        host.powerOn();
        host.loadState(Files.readAllBytes(saves.resolve(cart + ".state")));
        var game = PokecenterDig.game(Files.readAllBytes(rom)).orElseThrow();
        String why = host.withBus(bus -> PokecenterDig.check(game, bus));
        for (int i = 0; i < 60 && why != null && (why.startsWith("in a menu") || why.equals("busy")); i++) {
            if (why.equals("busy")) host.press(Button.WAIT, 2, 0); // mid-frame with another WRAM bank in: a moment later
            else host.press(Button.B, 6, 20);
            why = host.withBus(bus -> PokecenterDig.check(game, bus));
        }
        assumeTrue(why == null, why);
        assertThat(host.<Boolean>withBus(bus -> PokecenterDig.queue(user, game, bus, kind))).isTrue();
        return new Player(host, game);
    }

    @Test
    void theReceptionistWaitsForALateFriend() throws Exception {
        Player a = boot("tomassirio@gmail.com", CRYSTAL), b = boot("tomas.sirio@getbux.com", GOLD);
        try {
            EmulatorHost.link(a.host, b.host);
            Thread.sleep(5000); // the dig
            int late = 50; // presses of 0.5 s: b starts 25 s after a
            for (int i = 0; i < late + 50 && !("COLOSSEUM".equals(a.map()) && "COLOSSEUM".equals(b.map())); i++) {
                Thread other = Thread.ofVirtual().start(() -> a.host.press(Button.A, 6, 24));
                if (i >= late) b.host.press(Button.A, 6, 24);
                other.join();
            }
            assertThat(a.map()).isEqualTo("COLOSSEUM");
            assertThat(b.map()).isEqualTo("COLOSSEUM");
        } finally {
            a.host.shutdown();
            b.host.shutdown();
        }
    }

    /** Two Crystal games (last night's pair was Crystal with Crystal): both reach the room, trade and battle. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"TRADE", "BATTLE"})
    void crystalWithCrystal(String kind) throws Exception {
        Player a = boot("linktest@local", CRYSTAL, kind), b = boot("linktest@local", CRYSTAL, kind);
        String room = kind.equals("TRADE") ? "TRADE_CENTER" : "COLOSSEUM";
        try {
            EmulatorHost.link(a.host, b.host);
            Thread.sleep(5000); // the dig
            for (int i = 0; i < 80 && !(room.equals(a.map()) && room.equals(b.map())); i++) {
                Thread other = Thread.ofVirtual().start(() -> a.host.press(Button.A, 6, 24));
                b.host.press(Button.A, 6, 24);
                other.join();
            }
            System.out.println("Crystal x Crystal " + kind + ": " + a.map() + " / " + b.map());
            assertThat(a.map()).isEqualTo(room);
            assertThat(b.map()).isEqualTo(room);
        } finally {
            a.host.shutdown();
            b.host.shutdown();
        }
    }

    /** A girl in Crystal walks into the trade room as herself: no "We need to change the look here..." into Chris. */
    @Test
    void aGirlKeepsHerLook() throws Exception {
        Player a = boot("linktest@local", CRYSTAL, "TRADE"), b = boot("linktest@local", CRYSTAL, "TRADE");
        int gender = PlayerSprites.CRYSTAL_GENDER, player = Ghosts.CRYSTAL.playerStruct();
        a.host.withBus(bus -> {
            bus.setByte(gender, bus.getByte(gender) | 1);
            return null;
        });
        try {
            EmulatorHost.link(a.host, b.host);
            Thread.sleep(5000); // the dig (its new map loads her as Kris)
            for (int i = 0; i < 80 && !("TRADE_CENTER".equals(a.map()) && "TRADE_CENTER".equals(b.map())); i++) {
                Thread other = Thread.ofVirtual().start(() -> a.host.press(Button.A, 6, 24));
                b.host.press(Button.A, 6, 24);
                other.join();
            }
            assertThat(a.map()).isEqualTo("TRADE_CENTER");
            assertThat(a.host.<Integer>withBus(bus -> bus.getByte(player))).as("SPRITE_KRIS").isEqualTo(0x60);
        } finally {
            a.host.shutdown();
            b.host.shutdown();
        }
    }
}
