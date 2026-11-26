package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Bus;
import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AAAA's calls: the texts fit the phone's text box, the battle watcher tells beaten / lost / blackout apart, and on
 * every parked Gold, Silver or Crystal game in the repo's data/ (not in git; skipped without them) a call rings with
 * ??? on the caller ID, puts him in the phone, ??? answers twice with dots and the third time with the commands, and
 * after the reveal the caller ID says AAAA.
 */
class AaaaCallsTest {
    @Test
    void everyLineFitsTheTextBox() {
        List<Object[]> texts = new ArrayList<>();
        AaaaCalls.CALLS.forEach(c -> texts.add(c.text()));
        texts.add(AaaaCalls.DOTS);
        texts.add(AaaaCalls.COMMANDS);
        for (Object[] t : texts) {
            for (Object part : t) if (part instanceof String s) assertThat(s).hasSizeLessThanOrEqualTo(18);
            for (byte b : PokecenterDig.text(t)) assertThat(b & 0xFF).as("a character the font has").isNotEqualTo(0xFF);
        }
        assertThat(AaaaCalls.CALLS).extracting(AaaaCalls.Call::id).containsAll(AaaaCalls.ACHIEVEMENTS.values());
    }

    @Test
    void theStageFollowsTheCallsThatRang() {
        assertThat(AaaaCalls.stage(Map.of("badge", "1"))).isEqualTo(AaaaCalls.Stage.UNKNOWN);
        assertThat(AaaaCalls.stage(Map.of("lost", "pending"))).isEqualTo(AaaaCalls.Stage.UNKNOWN);
        assertThat(AaaaCalls.stage(Map.of("lost", "1"))).isEqualTo(AaaaCalls.Stage.REVEALED);
        assertThat(AaaaCalls.stage(Map.of("lost", "1", "beaten", "2"))).isEqualTo(AaaaCalls.Stage.BEATEN);
        assertThat(AaaaCalls.stage(Map.of("beaten", "2"))).isEqualTo(AaaaCalls.Stage.BEATEN);
    }

    /** Crystal's RAM, enough for the watcher: title, WRAM bank 1, battle mode/class/type, link mode, result, tower. */
    private static final class Ram implements Bus {
        final int[] mem = new int[0x10000];

        Ram() {
            byte[] title = "PM_CRYSTAL\0BYTE".getBytes();
            for (int i = 0; i < title.length; i++) mem[0x134 + i] = title[i];
            mem[0xFF70] = 1;
        }

        public int getByte(int address) {
            return mem[address];
        }

        public void setByte(int address, int value) {
            mem[address] = value & 0xFF;
        }

        List<String> fight(java.util.function.Consumer<Bus> watcher, int trainerClass, int type, int result, boolean link) {
            List<String> seen = new ArrayList<>();
            mem[0xD22D] = trainerClass == 0 ? 1 : 2;
            mem[0xD22F] = trainerClass;
            mem[0xD230] = type;
            mem[0xC2DC] = link ? 1 : 0;
            watcher.accept(this);
            mem[0xD0EE] = result;
            watcher.accept(this);
            mem[0xD22D] = mem[0xD22F] = mem[0xD230] = mem[0xC2DC] = 0; // CleanUpBattleRAM
            watcher.accept(this);
            watcher.accept(this);
            return seen;
        }
    }

    @Test
    void theWatcherTellsHowABattleEnded() {
        List<String> out = new ArrayList<>();
        var watcher = AaaaCalls.battles(out::add);
        Ram ram = new Ram();
        ram.fight(watcher, 0x3F, 0, 0, false);           // beat Red
        ram.fight(watcher, 0x3F, 0, 1, false);           // lost to him
        ram.fight(watcher, 0x05, 0, 1, false);           // lost to a trainer: blackout
        ram.fight(watcher, 0, 0, 1 | 0x80, false);       // a wild one (box full bit set): blackout
        ram.fight(watcher, 0x09, 1, 1, false);           // the rival's first battle (CANLOSE): nothing
        ram.fight(watcher, 0x05, 0, 1, true);            // a link battle: nothing
        ram.fight(watcher, 0x05, 0, 0, false);           // a win: nothing
        ram.mem[0xCFC0] = 1;
        ram.fight(watcher, 0x05, 0, 1, false);           // the Battle Tower: nothing
        assertThat(out).containsExactly("beaten", "lost", "blackout", "blackout");
    }

    static Stream<Path> states() throws Exception {
        return PokecenterDigTest.states();
    }

    @ParameterizedTest(allowZeroInvocations = true) // no parked states in data/ (CI): nothing to run
    @MethodSource("states")
    void ringsInARealGame(Path state) throws Exception {
        String cart = state.getFileName().toString().replace(".state", "");
        Path rom = Path.of("../../data/roms").resolve(cart + ".gbc");
        byte[] original = Files.readAllBytes(rom);
        var game = PokecenterDig.game(original).orElse(null);
        AaaaCalls.Built built = AaaaCalls.build(original);
        assumeTrue(game != null, "not Gold, Silver or Crystal");
        assertThat(built).as("the calls fit " + cart).isNotNull();
        boolean crystal = built.at().crystal();
        int tilemap = crystal ? 0xC4A0 : 0xC3A0, phoneList = crystal ? 0xDC7C : 0xD9C6;
        EmulatorHost host = new EmulatorHost();
        Path sav = Files.createTempDirectory("calls").resolve("game.sav");
        host.insert("game", rom, sav, r -> AaaaCalls.patch(new V1Red().patch(r), AaaaCalls.Stage.UNKNOWN));
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(state));
            assumeTrue(free(host, game), state.getParent().getFileName() + ": not free in the overworld");
            int[] before = phone(host, phoneList);
            assumeTrue(contains(before, 0) || contains(before, 8), state.getParent().getFileName() + ": a full phone");

            call(host, game, built, built.callers().get("badge"), tilemap, bytes(0xE6, 0xE6, 0xE6), bytes(0xE8, 0xE8, 0xE8, 0xE8));
            assertThat(phone(host, phoneList)).as("??? in the phone").contains(8);

            // Calling ??? back: dots, dots, then the commands (flags 2046/2047, so first make sure both are clear).
            int flags = (crystal ? 0xDA72 : 0xD7B7) + 2046 / 8;
            host.withBus(bus -> {
                bus.setByte(flags, bus.getByte(flags) & ~0xC0);
                return null;
            });
            byte[] many = PokecenterDig.text("so many");
            many = java.util.Arrays.copyOfRange(many, 1, many.length);
            int callee = built.callees().get(AaaaCalls.Stage.UNKNOWN);
            for (int i = 0; i < 2; i++) {
                List<byte[]> screens = call(host, game, built, callee, tilemap, bytes(0xE6, 0xE6, 0xE6), bytes(0xE8, 0xE8, 0xE8, 0xE8));
                byte[] m = many;
                assertThat(screens).as("call " + (i + 1) + ": dots only").noneMatch(screen -> indexOf(screen, m) >= 0);
            }
            call(host, game, built, callee, tilemap, bytes(0xE6, 0xE6, 0xE6), many);

            // Revealed: AAAA on the caller ID, laughing.
            host.patchRom(AaaaCalls.contact(built, AaaaCalls.Stage.REVEALED));
            byte[] lmao = PokecenterDig.text("LMAOCHAMP");
            call(host, game, built, built.callers().get("lost"), tilemap, bytes(0x80, 0x80, 0x80, 0x80, 0x9C),
                    java.util.Arrays.copyOfRange(lmao, 1, lmao.length));
            System.out.println("AAAA called " + state.getParent().getFileName() + "'s " + cart);
        } finally {
            host.shutdown();
        }
    }

    /** Rings script and waits for name on the caller ID and text in the box, then presses A until free again. */
    private static List<byte[]> call(EmulatorHost host, PokecenterDig.Game game, AaaaCalls.Built built, int script,
                                     int tilemap, byte[] name, byte[] text) throws Exception {
        assertThat(free(host, game)).as("free before the call").isTrue();
        host.withBus(bus -> AaaaCalls.ring(built, bus, script));
        List<byte[]> screens = new ArrayList<>();
        boolean sawName = false, sawText = false;
        for (int i = 0; i < 300 && !(sawName && sawText); i++) {
            Thread.sleep(50);
            byte[] screen = host.readBytes(tilemap, 360);
            screens.add(screen);
            sawName |= indexOf(screen, name) >= 0;
            sawText |= indexOf(screen, text) >= 0;
        }
        assertThat(sawName).as("caller ID").isTrue();
        assertThat(sawText).as("the call's text").isTrue();
        for (int i = 0; i < 40 && !free(host, game); i++) host.press(Button.A, 6, 30);
        assertThat(free(host, game)).as("hung up").isTrue();
        return screens;
    }

    private static boolean free(EmulatorHost host, PokecenterDig.Game game) throws Exception {
        for (int i = 0; i < 100; i++) {
            String why = host.withBus(bus -> PokecenterDig.busy(game, bus));
            if (why == null) return true;
            if (why.equals("nowhere yet")) return false;
            if (why.equals("busy") || why.equals("on the move")) Thread.sleep(20);
            else host.press(Button.B, 6, 30);
        }
        return false;
    }

    private static int[] phone(EmulatorHost host, int at) {
        return host.withBus(bus -> {
            int[] list = new int[10];
            for (int i = 0; i < 10; i++) list[i] = bus.getByte(at + i);
            return list;
        });
    }

    private static boolean contains(int[] list, int value) {
        for (int v : list) if (v == value) return true;
        return false;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
