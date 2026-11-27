package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** PokecenterDig.shortNurse on the real ROMs in the repo's data/roms (not in git; skipped without them). */
class NurseTest {
    static Stream<Path> roms() throws Exception {
        Path dir = Path.of("../../data/roms");
        if (!Files.isDirectory(dir)) return Stream.of();
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".gbc")).toList().stream();
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("roms")
    void healIsShort(Path file) throws Exception {
        byte[] rom = Files.readAllBytes(file);
        assumeTrue(PokecenterDig.game(rom).isPresent(), "not Gold, Silver or Crystal");
        byte[] out = PokecenterDig.patch(rom);
        String s = str(out);
        assertThat(s).contains(str(PokecenterDig.text("Heal?", 0x57)), str(PokecenterDig.text("Healing...", 0x57)));
        for (Object[] said : new Object[][] {{"Good morning!", 0x4F, "Welcome to our"}, {"Thank you for", 0x4F, "waiting."}}) {
            int at = str(rom).indexOf(str(PokecenterDig.text(said)));
            while (at >= 0 && PokecenterDig.textRef(rom, at) == null) at = str(rom).indexOf(str(PokecenterDig.text(said)), at + 1);
            assertThat(at).as("written by the nurse script in the original").isNotNegative();
            assertThat(PokecenterDig.textRef(out, at)).as("no longer written").isNull();
        }
    }

    private static String str(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }
}
