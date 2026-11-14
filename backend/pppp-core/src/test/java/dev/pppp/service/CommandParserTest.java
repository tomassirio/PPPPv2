package dev.pppp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.pppp.emulator.Button;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandParserTest {

    @ParameterizedTest
    @CsvSource({
            "a,          1,   A",
            "A,          1,   A",
            "' start ',  1,   START",
            "a3,         1,   A A A",
            "a,          3,   A A A",
            "left2down2, 1,   LEFT LEFT DOWN DOWN",
            "abwait,     1,   A B WAIT",
            "up5,        1,   UP UP UP UP UP",
    })
    void parses(String command, int repeat, String expected) {
        List<Button> want = java.util.Arrays.stream(expected.split(" ")).map(Button::valueOf).toList();
        assertEquals(want, CommandParser.parse(command, repeat));
    }

    @ParameterizedTest
    @CsvSource({
            "x,          1",
            "'',         1",
            "a b,        1",
            "a0,         1",
            "a6,         1",
            "a99,        1",
            "a,          6",
            "a,          0",
            "a3b3,       1",
            "a2,         2",
            "ab,         2",
    })
    void rejects(String command, int repeat) {
        assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(command, repeat));
    }
}
