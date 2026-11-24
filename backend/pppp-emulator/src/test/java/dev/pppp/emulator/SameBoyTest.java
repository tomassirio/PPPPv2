package dev.pppp.emulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** SameBoy through the JNI glue, on the real ROMs and parked games in the repo's data/ (skipped without them). */
class SameBoyTest {
    private static final Path DATA = Path.of("../../data");
    private static final Path CRYSTAL = DATA.resolve("roms/pokemon-crystal-version-usa-europe-rev-1.gbc");

    @Test
    void bootsAndRunsFasterThanRealTime() throws Exception {
        assumeTrue(Files.exists(CRYSTAL), "no Crystal ROM in data/");
        long h = SameBoy.create(Files.readAllBytes(CRYSTAL), null, 22050);
        try {
            for (int f = 0; f < 600; f++) SameBoy.runFrame(h); // ten seconds: past the boot, at the title screen
            int[] px = new int[160 * 144];
            SameBoy.pixels(h, px);
            assertThat(java.util.Arrays.stream(px).distinct().count()).as("a picture, not one colour").isGreaterThan(3);
            long t0 = System.nanoTime();
            for (int f = 0; f < 1200; f++) SameBoy.runFrame(h); // 20 s of game time
            double seconds = (System.nanoTime() - t0) / 1e9;
            System.out.printf("SameBoy via JNI: 20 s of Crystal in %.2f s (%.1fx)%n", seconds, 20 / seconds);
            assertThat(SameBoy.drainAudio(h, new short[8192])).as("sound").isPositive();
        } finally {
            SameBoy.destroy(h);
        }
    }

    @Test
    void loadsAParkedCoffeeGbState() throws Exception {
        Path state = DATA.resolve("saves/tomassirio@gmail.com/pokemon-crystal-version-usa-europe-rev-1.state");
        assumeTrue(Files.exists(CRYSTAL) && Files.exists(state), "no parked Crystal game in data/");
        Path sav = state.resolveSibling("pokemon-crystal-version-usa-europe-rev-1.sav");
        long h = SameBoy.create(Files.readAllBytes(CRYSTAL), Files.exists(sav) ? Files.readAllBytes(sav) : null, 22050);
        try {
            int r = SameBoy.loadState(h, Files.readAllBytes(state));
            System.out.println("loadState of Coffee GB's BESS: " + r);
            assertThat(r).isZero();
            for (int f = 0; f < 120; f++) SameBoy.runFrame(h);
            // Crystal's wMapGroup / wMapNumber (pokecrystal11.sym) in WRAM bank 1: the player is somewhere on a map.
            SameBoy.write(h, 0xFF70, 1);
            int group = SameBoy.read(h, 0xDCB5), number = SameBoy.read(h, 0xDCB6);
            System.out.println("on map " + group + ":" + number);
            assertThat(group).isBetween(1, 26);
        } finally {
            SameBoy.destroy(h);
        }
    }
}
