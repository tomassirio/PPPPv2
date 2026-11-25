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
 * Digs players of the real ROMs from their parked states in the repo's data/ (not in git; skipped without them): up
 * in front of the trade receptionist, facing her, with the stairs leading down into a Pokémon Center; then from there
 * over to the battle one.
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

    @ParameterizedTest(allowZeroInvocations = true) // no parked states in data/ (CI): nothing to run
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
        host.insert("game", rom, sav, PokecenterDig::patch);
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            host.setSpeed(4);
            // A parked state may stop mid-frame with another WRAM bank in: run a few frames, as play does.
            host.press(Button.WAIT, 10, 0);
            String why = host.withBus(bus -> PokecenterDig.check(game, bus));
            for (int i = 0; i < 20 && why != null && why.startsWith("in a menu"); i++) { // a menu left open: close it
                host.press(Button.B, 6, 30);
                why = host.withBus(bus -> PokecenterDig.check(game, bus));
            }
            assumeTrue(why == null, why);
            for (String kind : new String[] {"TRADE", "BATTLE"}) {
                // A new game that never entered a Pokémon Center has nowhere to dig to (queue says so).
                assumeTrue(host.<Boolean>withBus(bus -> PokecenterDig.queue("", game, bus, kind)), "never in a Pokémon Center");
                Thread.sleep(3000);
                int[] now = host.withBus(bus -> new int[] {bus.getByte(mapGroup), bus.getByte(mapGroup + 1),
                        bus.getByte(mapGroup + 3), bus.getByte(mapGroup + 2), bus.getByte(game.at().backupWarp() + 1),
                        bus.getByte(game.at().backupWarp() + 2)});
                assertThat(game.maps().mapId(now[0], now[1])).as(state + " " + kind).isEqualTo("POKECENTER_2F");
                assertThat(new int[] {now[2], now[3]}).as(kind).containsExactly(kind.equals("TRADE") ? 5 : 9, 3);
                assertThat(game.maps().pokecenter(now[4], now[5])).as("stairs lead down into a Center").isTrue();
                assertThat(host.<String>withBus(bus -> PokecenterDig.check(game, bus))).as("free again").isNull();
                assertThat(host.<Boolean>withBus(bus -> PokecenterDig.atReceptionist(game, bus, kind))).as("at her").isTrue();
                host.press(Button.A, 6, 30); // what LinkService presses for them: she talks
                assertThat(host.<String>withBus(bus -> PokecenterDig.check(game, bus))).as("talking").isNotNull();
                for (int i = 0; i < 20 && host.withBus(bus -> PokecenterDig.check(game, bus)) != null; i++) {
                    host.press(Button.B, 6, 30); // "No", and out of her dialog
                }
            }
        } finally {
            host.shutdown();
        }
    }

    /**
     * Only Pokémon Centers count as one, in every game: Crystal's TILESET_POKECOM_CENTER is $15, and 15 ($0F, the
     * gyms' and Elite Four's tileset) once made Violet City's gym its "Center", so a player respawning there could not
     * be dug to the Cable Club.
     */
    @ParameterizedTest(allowZeroInvocations = true)
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pokemon-crystal-version-usa-europe-rev-1",
            "pokemon-gold-version-usa-europe-sgb-enhanced-gb-compatible", "pokemon-silver-version-usa-europe-sgb-enhanced-gb-compatible"})
    void onlyPokemonCentersAreCenters(String cart) throws Exception {
        Path rom = DATA.resolve("roms").resolve(cart + ".gbc");
        assumeTrue(Files.exists(rom), "no " + cart);
        var game = PokecenterDig.game(Files.readAllBytes(rom)).orElseThrow();
        var maps = game.maps();
        int centers = 0;
        for (int g = 1; g < 40; g++) {
            for (int n = 1; n < 120; n++) {
                if (!maps.pokecenter(g, n)) continue;
                centers++;
                assertThat(maps.mapId(g, n)).as(g + ":" + n).matches(".*(POKECENTER|POKECOM_CENTER).*");
            }
        }
        assertThat(centers).isGreaterThan(20);
        int[] violet = maps.map("VIOLET_CITY");
        int[] door = maps.pokecenterDoor(violet[0], violet[1]);
        assertThat(maps.mapId(door[2], door[3])).isEqualTo("VIOLET_POKECENTER_1F");
        assertThat(maps.warpTo(door[2], door[3], game.pc2f()[0], game.pc2f()[1])).isPositive();
    }
}
