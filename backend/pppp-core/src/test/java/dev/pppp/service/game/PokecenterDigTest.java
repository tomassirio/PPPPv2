package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Digs players of the real ROMs from their parked states in the repo's data/ (not in git; skipped without them) and
 * checks they come up in a Pokémon Center. A player already in one digs anyway, to the same Center.
 */
class PokecenterDigTest {
    private static final Path DATA = Path.of("../../data");

    static Stream<Path> states() throws Exception {
        if (!Files.isDirectory(DATA.resolve("saves"))) return Stream.of();
        try (var files = Files.walk(DATA.resolve("saves"))) {
            return files.filter(p -> p.toString().endsWith(".state"))
                    .filter(p -> Files.exists(DATA.resolve("roms").resolve(cartridge(p) + ".gbc")))
                    .toList().stream();
        }
    }

    private static String cartridge(Path state) {
        String name = state.getFileName().toString();
        return name.substring(0, name.length() - ".state".length());
    }

    @ParameterizedTest
    @MethodSource("states")
    void digsToTheLastPokemonCenter(Path state) throws Exception {
        Path rom = DATA.resolve("roms").resolve(cartridge(state) + ".gbc");
        var found = PokecenterDig.game(Files.readAllBytes(rom));
        assumeTrue(found.isPresent(), "not Gold, Silver or Crystal");
        var game = found.get();
        int mapGroup = game.at().mapGroup();
        Path sav = Files.createTempDirectory("dig").resolve("game.sav");
        Path original = state.resolveSibling(cartridge(state) + ".sav");
        if (Files.exists(original)) Files.copy(original, sav);
        EmulatorHost host = new EmulatorHost();
        host.insert("game", rom, sav);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            host.setSpeed(4);
            String why = host.withBus(bus -> PokecenterDig.run(game, bus, false, ""));
            for (int i = 0; i < 6 && why != null && why.startsWith("in a menu"); i++) { // a menu left open: close it
                host.press(Button.B, 6, 30);
                why = host.withBus(bus -> PokecenterDig.run(game, bus, false, ""));
            }
            assumeTrue(why == null, why);
            assertThat(host.<Boolean>withBus(bus -> PokecenterDig.queue("", game, bus))).isTrue();
            Thread.sleep(3000);
            int[] now = host.withBus(bus -> new int[] {bus.getByte(mapGroup), bus.getByte(mapGroup + 1)});
            assertThat(game.maps().pokecenter(now[0], now[1])).as("in a Pokémon Center").isTrue();
            assertThat(host.<String>withBus(bus -> PokecenterDig.run(game, bus, false, ""))).as("free again").isNull();
        } finally {
            host.shutdown();
        }
    }
}
