package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Wonder Trade on the real ROMs and parked games in data/ (not in git; skipped without them): the daily rewards
 * calendar, trainers have names, and on a running game a party member leaves and a
 * Kadabra arrives as an Alakazam, and an event-style Eevee still joins.
 */
class WonderTradeTest {
    static Stream<Path> roms() throws Exception {
        return MysteryGiftsTest.roms();
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("roms")
    void rewardsAndTrainerNamesExistInEveryGame(Path rom) throws Exception {
        PokemonGen2 game = PokemonGen2.forRom(Files.readAllBytes(rom));
        assumeTrue(game != null, "not Gold, Silver or Crystal");
        var calendar = DailyRewards.calendar();
        assertThat(calendar.get(0).grants()).as("day 1 only welcomes").isEmpty();
        for (var r : calendar.subList(1, calendar.size())) assertThat(r.grants()).as("day " + r.day()).isNotEmpty();
        assertThat(game.trainerName(1, 1)).as("FALKNER").isEqualTo("Falkner");
        assertThat(game.trainerName(2, 1)).as("WHITNEY").isEqualTo("Whitney");
        for (String item : List.of("Everstone", "Metal Coat", "Dragon Scale", "King's Rock", "Up-Grade")) {
            assertThat(game.itemId(item)).as(item).isPositive();
        }
    }

    static Stream<Path> states() throws Exception {
        return PokecenterDigTest.states();
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("states")
    void aKadabraLeavesOneGameAndArrivesAsAnAlakazam(Path state) throws Exception {
        String cart = state.getFileName().toString().replace(".state", "");
        Path rom = Path.of("../../data/roms").resolve(cart + ".gbc");
        var found = PokecenterDig.game(Files.readAllBytes(rom));
        assumeTrue(found.isPresent(), "not Gold, Silver or Crystal");
        var game = found.get();
        PokemonGen2 g = game.maps();
        Path sav = Files.createTempDirectory("wonder").resolve("game.sav");
        EmulatorHost host = new EmulatorHost();
        host.insert("game", rom, sav);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            String why = host.withBus(bus -> PokecenterDig.busy(game, bus));
            for (int i = 0; i < 200 && why != null && !why.equals("nowhere yet"); i++) {
                if (why.equals("busy")) Thread.sleep(20);
                else host.press(Button.B, 6, 30);
                why = host.withBus(bus -> PokecenterDig.busy(game, bus));
            }
            assumeTrue(why == null, state.getParent().getFileName() + ": " + why);
            int count = host.withBus(bus -> bus.getByte(g.partyCount()));
            assumeTrue(count >= 1, "no party");
            int[] after = host.withBus(bus -> {
                GiftMon.Raw first = GiftMon.read(bus, g, 0);
                byte[] mon = first.mon().clone();
                mon[0] = 64; // a Kadabra, holding nothing, nicknamed as its species
                mon[1] = 0;
                mon[31] = 30;
                GiftMon.stats(g, mon);
                byte[] nick = java.util.Arrays.copyOf(g.rawName(64), 11);
                nick[10] = 0x50;
                GiftMon.Raw kadabra = new GiftMon.Raw(mon, first.ot(), nick);
                GiftMon.remove(bus, g, 0);
                int less = bus.getByte(g.partyCount());
                GiftMon.Raw arrived = GiftMon.arrive(g, kadabra);
                boolean put = GiftMon.put(bus, g, arrived);
                GiftMon.Raw last = GiftMon.read(bus, g, bus.getByte(g.partyCount()) - 1);
                WonderTrade.Mon m = WonderTrade.mon(g, last, 64);
                System.out.println(state.getParent().getFileName() + " " + cart + ": got " + m);
                GiftMon.Spec s = new GiftMon.Spec("Eevee", 20, List.of("Tackle", "Tail Whip", "Sand-Attack", "Quick Attack"), false, false);
                GiftMon.remove(bus, g, bus.getByte(g.partyCount()) - 1);
                boolean added = GiftMon.add(bus, g, s, new Random(1));
                return new int[] {less, put ? 1 : 0, last.mon()[0] & 0xFF, last.mon()[31] & 0xFF,
                        (last.mon()[36] & 0xFF) << 8 | last.mon()[37] & 0xFF, m.nickname() == null ? 1 : 0, added ? 1 : 0,
                        bus.getByte(g.partyCount())};
            });
            assertThat(after[0]).as("one left").isEqualTo(count - 1);
            assertThat(after[1]).as("one joined").isEqualTo(1);
            assertThat(after[2]).as("Alakazam").isEqualTo(65);
            assertThat(after[3]).isEqualTo(30);
            assertThat(after[4]).as("max HP").isBetween(60, 120);
            assertThat(after[5]).as("renamed with its species").isEqualTo(1);
            assertThat(after[6]).as("AAAA's Eevee").isEqualTo(1);
            assertThat(after[7]).isEqualTo(count);
            Thread.sleep(1000);
            assertThat((String) host.withBus(bus -> PokecenterDig.busy(game, bus))).as("still in the game").isNotEqualTo("nowhere yet");
        } finally {
            host.shutdown();
        }
    }
    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("states")
    void aTradeIsSavedSoResetCannotUndoIt(Path state) throws Exception {
        String cart = state.getFileName().toString().replace(".state", "");
        Path rom = Path.of("../../data/roms").resolve(cart + ".gbc");
        var found = PokecenterDig.game(Files.readAllBytes(rom));
        assumeTrue(found.isPresent(), "not Gold, Silver or Crystal");
        var game = found.get();
        PokemonGen2 g = game.maps();
        Path sav = Files.createTempDirectory("tradesave").resolve("game.sav");
        Path original = state.resolveSibling(cart + ".sav");
        if (Files.exists(original)) Files.copy(original, sav);
        EmulatorHost host = new EmulatorHost();
        host.insert("game", rom, sav);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            String why = free(host, game);
            assumeTrue(why == null, state.getParent().getFileName() + ": " + why);
            assumeTrue(Boolean.TRUE.equals(host.withBus(bus -> TradeSave.saved(bus, g))), "never saved");
            int before = host.withBus(bus -> bus.getByte(g.partyCount()));
            // One leaves (a full party), or one arrives (room): either way the save must carry it through a Reset.
            int expected = before == 6 ? 5 : before + 1;
            boolean saved = host.withBus(bus -> {
                if (before == 6) GiftMon.remove(bus, g, before - 1);
                else GiftMon.add(bus, g, new GiftMon.Spec("Eevee", 20, List.of("Tackle", "Tail Whip"), false, false), new Random(1));
                return TradeSave.save(bus, g);
            });
            assertThat(saved).isTrue();
            // Every checksum is what the game itself would compute: the main copy's, and Crystal's one-piece backup.
            int[] sums = host.withBus(bus -> {
                bus.setByte(0x0000, 0x0A);
                try {
                    var at = TradeSave.layout(g);
                    bus.setByte(0x4000, 1);
                    int end = g.crystal() ? 0xAB83 : 0xAD69, sum = 0; // sGameDataEnd
                    for (int a = 0xA009; a < end; a++) sum += bus.getByte(a);
                    int stored = bus.getByte(at.checksum()[1]) | bus.getByte(at.checksum()[1] + 1) << 8;
                    int bsum = -1, bstored = -1;
                    if (g.crystal()) {
                        bus.setByte(0x4000, 0);
                        bsum = 0;
                        for (int a = 0xB209; a < 0xBD83; a++) bsum += bus.getByte(a); // sBackupGameData .. End
                        bstored = bus.getByte(0xBF0D) | bus.getByte(0xBF0E) << 8;
                        bsum &= 0xFFFF;
                    }
                    return new int[] {sum & 0xFFFF, stored, bsum, bstored};
                } finally {
                    bus.setByte(0x0000, 0x00);
                }
            });
            assertThat(sums[1]).as("main checksum").isEqualTo(sums[0]);
            assertThat(sums[3]).as("backup checksum").isEqualTo(sums[2]);

            // Reset, Continue: the party the trade left, not the one from before.
            host.reset();
            Thread.sleep(3000);
            for (int i = 0; i < 40 && free(host, game, false) != null; i++) {
                host.press(i % 4 == 0 ? Button.START : Button.A, 6, 40);
                Thread.sleep(250);
            }
            assertThat(free(host, game)).as("back in the game").isNull();
            int after = host.withBus(bus -> bus.getByte(g.partyCount()));
            System.out.println(state.getParent().getFileName() + " " + cart + ": party " + before + " -> traded, after Reset " + after);
            assertThat(after).as("party after Reset").isEqualTo(expected);
        } finally {
            host.shutdown();
        }
    }

    /** Waits (closing what is open) until the player is free in the overworld; why not, else null. */
    private static String free(EmulatorHost host, PokecenterDig.Game game) throws Exception {
        return free(host, game, true);
    }

    private static String free(EmulatorHost host, PokecenterDig.Game game, boolean close) throws Exception {
        String why = host.withBus(bus -> PokecenterDig.busy(game, bus));
        for (int i = 0; close && i < 200 && why != null && !why.equals("nowhere yet"); i++) {
            if (why.equals("busy")) Thread.sleep(20);
            else host.press(Button.B, 6, 30);
            why = host.withBus(bus -> PokecenterDig.busy(game, bus));
        }
        return why;
    }

}
