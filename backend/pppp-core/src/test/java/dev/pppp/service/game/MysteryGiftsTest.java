package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Bus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class MysteryGiftsTest {
    /** 64 KiB of memory; the PC list at 0xD000. */
    static final class Mem implements Bus {
        final int[] b = new int[0x10000];
        public int getByte(int a) { return b[a]; }
        public void setByte(int a, int v) { b[a] = v & 0xFF; }
    }

    @Test
    void stacksOrAppendsAndRespectsTheLimits() {
        Mem m = new Mem();
        int pc = 0xD000;
        m.b[pc] = 1; m.b[pc + 1] = 0x20; m.b[pc + 2] = 98; m.b[pc + 3] = 0xFF; // one Potion stack of 98
        assertThat(MysteryGifts.put(m, pc, 0x20, 1)).isTrue();
        assertThat(m.b[pc + 2]).isEqualTo(99);
        assertThat(MysteryGifts.put(m, pc, 0x20, 1)).isTrue(); // the stack is full: a new entry
        assertThat(m.b[pc]).isEqualTo(2);
        assertThat(new int[] {m.b[pc + 3], m.b[pc + 4], m.b[pc + 5]}).containsExactly(0x20, 1, 0xFF);
        m.b[pc + 5] = 0x00; // not a list (no -1 after the last entry): nothing written
        assertThat(MysteryGifts.put(m, pc, 0x30, 1)).isFalse();
        assertThat(m.b[pc]).isEqualTo(2);
    }

    static Stream<Path> roms() throws Exception {
        Path dir = Path.of("../../data/roms");
        return Files.isDirectory(dir) ? Files.list(dir).filter(p -> p.toString().endsWith(".gbc")) : Stream.empty();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no ROMs in data/ (CI): nothing to run
    @MethodSource("roms")
    void everyGiftExistsInEveryGame(Path rom) throws Exception {
        PokemonGen2 game = PokemonGen2.forRom(Files.readAllBytes(rom));
        assumeTrue(game != null, "not Gold, Silver or Crystal");
        for (var g : MysteryGifts.CATALOG) {
            if (!g.pokemon()) {
                assertThat(game.itemId(g.name())).as(g.name() + " in " + rom.getFileName()).isPositive();
                continue;
            }
            for (int seed = 0; seed < 50; seed++) { // the Odd Egg's every baby
                var spec = MysteryGifts.EVENTS.get(g.id()).apply(new java.util.Random(seed));
                assertThat(game.speciesId(spec.species())).as(spec.species()).isPositive();
                for (String move : spec.moves()) assertThat(game.moveId(move)).as(move + " in " + rom.getFileName()).isPositive();
            }
        }
    }

    @Test
    void growthRatesAndOddEggOdds() {
        assertThat(GiftMon.exp(0, 30)).isEqualTo(27000); // medium fast: n^3
        assertThat(GiftMon.exp(5, 30)).isEqualTo(33750); // slow: 5n^3/4
        assertThat(GiftMon.exp(3, 5)).isEqualTo(135); // medium slow
        var r = new java.util.Random(1);
        for (int i = 0; i < 1000; i++) assertThat(GiftMon.oddEgg(r).moves()).contains("Dizzy Punch");
    }

    static Stream<Path> states() throws Exception {
        return PokecenterDigTest.states();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no parked states in data/ (CI): nothing to run
    @MethodSource("states")
    void eventPokemonJoinTheParty(Path state) throws Exception {
        String cart = state.getFileName().toString().replace(".state", "");
        Path rom = Path.of("../../data/roms").resolve(cart + ".gbc");
        byte[] bytes = Files.readAllBytes(rom);
        PokemonGen2 game = PokemonGen2.forRom(bytes);
        var dig = PokecenterDig.game(bytes);
        assumeTrue(game != null && dig.isPresent(), "not Gold, Silver or Crystal");
        Path sav = Files.createTempDirectory("gift").resolve("game.sav");
        if (Files.exists(state.resolveSibling(cart + ".sav"))) Files.copy(state.resolveSibling(cart + ".sav"), sav);
        var host = new dev.pppp.emulator.EmulatorHost();
        host.insert("game", rom, sav);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            host.press(dev.pppp.emulator.Button.WAIT, 10, 0);
            int p = game.partyCount();
            int before = host.withBus(bus -> bus.getByte(p));
            assumeTrue(before >= 1 && before <= 4, state.getParent().getFileName() + ": no room for two in the party");
            String why = "busy";
            for (int i = 0; i < 100 && why != null; i++) {
                why = host.withBus(bus -> PokecenterDig.busy(dig.get(), bus));
                if (why != null) Thread.sleep(20);
            }
            assumeTrue(why == null, why);
            var celebi = MysteryGifts.EVENTS.get("celebi").apply(new java.util.Random(1));
            var egg = MysteryGifts.EVENTS.get("odd-egg").apply(new java.util.Random(1));
            assertThat(host.<Boolean>withBus(bus -> GiftMon.add(bus, game, celebi, new java.util.Random(1)))).isTrue();
            assertThat(host.<Boolean>withBus(bus -> GiftMon.add(bus, game, egg, new java.util.Random(1)))).isTrue();
            int[] got = host.withBus(bus -> {
                int mon = p + 1 + 6 + 1 + before * 48;
                return new int[] {bus.getByte(p), bus.getByte(p + 1 + before), bus.getByte(mon + 31), bus.getByte(mon + 2),
                        bus.getByte(p + 2 + before), bus.getByte(p + 3 + before)};
            });
            assertThat(got[0]).as("party count").isEqualTo(before + 2);
            assertThat(got[1]).as("species list").isEqualTo(game.speciesId("Celebi"));
            assertThat(got[2]).as("level").isEqualTo(30);
            assertThat(got[3]).as("first move").isEqualTo(game.moveId("Heal Bell"));
            assertThat(got[4]).as("the egg in the species list").isEqualTo(0xFD);
            assertThat(got[5]).as("list end").isEqualTo(0xFF);
            host.press(dev.pppp.emulator.Button.WAIT, 60, 0); // the game keeps running with them
            System.out.println("Celebi and an Odd Egg (" + egg.species() + (egg.shiny() ? ", shiny" : "") + ") joined "
                    + state.getParent().getFileName() + "'s " + cart + " party (" + before + " -> " + got[0] + ")");
        } finally {
            host.shutdown();
        }
    }
}
