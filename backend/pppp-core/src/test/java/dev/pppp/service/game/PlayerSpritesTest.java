package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Real ROMs and parked games in data/ (not in git; skipped without them): patched to walk as Red, the game loads him
 * on the next map it draws (here the Dig), and the player's map object is Red.
 */
class PlayerSpritesTest {
    private static final Path DATA = Path.of("../../data");

    @ParameterizedTest
    @CsvSource({"pokemon-crystal-version-usa-europe-rev-1, KRIS, GREEN, 96, 0xA0",
            "pokemon-gold-version-usa-europe-sgb-enhanced-gb-compatible, KRIS, , 1, 0",
            "pokemon-silver-version-usa-europe-sgb-enhanced-gb-compatible, LASS, PINK, 40, 0xC0"})
    void runningCartridgeLoadsThePeerForEitherSeat(String cart, String peer, String color, int expected, String palette,
                                                  @TempDir Path temp) throws Exception {
        Path rom = DATA.resolve("roms/" + cart + ".gbc");
        assumeTrue(Files.exists(rom), "no ROM");
        byte[] bytes = Files.readAllBytes(rom);
        var host = new EmulatorHost();
        host.insert(cart, rom, temp.resolve("player.sav"));
        host.powerOn();
        try {
            host.pause();
            var patch = PlayerSprites.cableFriend(bytes, peer, color);
            assertThat(patch).hasSize(8); // sprite and palette of 2 seats in 2 rooms
            host.patchRom(patch);
            for (int offset : patch.keySet()) {
                int value = host.withBus(bus -> {
                    bus.setByte(0x2000, offset / 0x4000);
                    return bus.getByte(0x4000 + offset % 0x4000);
                });
                boolean paletteByte = patch.containsKey(offset - 7);
                assertThat(value).isEqualTo(paletteByte ? Integer.decode(palette) : expected);
            }
        } finally {
            host.shutdown();
        }
    }

    @ParameterizedTest
    @CsvSource({"tomassirio@gmail.com, pokemon-crystal-version-usa-europe-rev-1, 0xD4D6",
            "tomas.sirio@getbux.com, pokemon-gold-version-usa-europe-sgb-enhanced-gb-compatible, 0xD1FD"})
    void thePlayerWalksAsTheChosenTrainer(String user, String cart, String playerStruct) throws Exception {
        Path rom = DATA.resolve("roms/" + cart + ".gbc"), saves = DATA.resolve("saves/" + user);
        assumeTrue(Files.exists(rom) && Files.exists(saves.resolve(cart + ".state")), "no save");
        byte[] bytes = Files.readAllBytes(rom);
        assertThat(PlayerSprites.choices(bytes)).extracting(PlayerSprites.Choice::key).contains("CHRIS", "RED", "LASS");
        assertThat(PlayerSprites.choices(bytes).stream().anyMatch(c -> c.key().equals("KRIS"))).isEqualTo(cart.contains("crystal"));
        assertThat(PlayerSprites.sheet(bytes, "RED")).isNotNull();
        assertThat(PlayerSprites.patch(bytes, "CHRIS")).isSameAs(bytes);
        assertThat(PlayerSprites.patch(bytes, "CHRIS", "BLUE")).as("the default trainer in another colour").isNotSameAs(bytes);
        assertThat(PlayerSprites.sheet(bytes, "RED", "GREEN")).isNotEqualTo(PlayerSprites.sheet(bytes, "RED"));
        // Recolouring in place writes exactly what a fresh boot's patch has at those bytes.
        for (String key : new String[] {"CHRIS", "RED"}) {
            for (String color : new String[] {null, "BLUE", "PINK"}) {
                byte[] boot = PlayerSprites.patch(bytes, key, color);
                var live = PlayerSprites.colorBytes(bytes, key, color);
                assertThat(live).as(key + " " + color).isNotEmpty();
                live.forEach((at, v) -> assertThat(boot[at] & 0xFF).as(key + " " + color + " @" + at).isEqualTo(v));
            }
        }

        var game = PokecenterDig.game(bytes).orElseThrow();
        Path sav = Files.createTempDirectory("sprite").resolve("game.sav");
        Files.copy(saves.resolve(cart + ".sav"), sav);
        EmulatorHost host = new EmulatorHost();
        host.insert(cart, rom, sav, b -> PlayerSprites.patch(PokecenterDig.patch(b), "RED", "GREEN"));
        host.powerOn();
        try {
            host.loadState(Files.readAllBytes(saves.resolve(cart + ".state")));
            host.setSpeed(4);
            String why = host.withBus(bus -> PokecenterDig.check(game, bus));
            for (int i = 0; i < 20 && why != null && why.startsWith("in a menu"); i++) {
                host.press(Button.B, 6, 20);
                why = host.withBus(bus -> PokecenterDig.check(game, bus));
            }
            assumeTrue(why == null, why);
            assertThat(host.<Boolean>withBus(bus -> PokecenterDig.queue(user, game, bus, "TRADE"))).isTrue();
            Thread.sleep(3000); // the dig reloads the map: GetPlayerSprite runs
            int sprite = host.withBus(bus -> bus.getByte(Integer.decode(playerStruct)));
            assertThat(sprite).as("OBJECT_SPRITE of the player").isEqualTo(0x06); // SPRITE_RED
            int palette = host.withBus(bus -> bus.getByte(Integer.decode(playerStruct) + 6)) & 7;
            assertThat(palette).as("OBJECT_PALETTE of the player").isEqualTo(2); // PAL_OW_GREEN
            // A new colour shows at once, without a map load.
            Boolean done = false;
            for (int i = 0; i < 30 && !Boolean.TRUE.equals(done); i++) done = host.withBus(bus -> PlayerSprites.repaint(bus, bytes, "RED", "BLUE"));
            assertThat(done).isTrue();
            assertThat(host.withBus(bus -> bus.getByte(Integer.decode(playerStruct) + 6)) & 7).as("repainted").isEqualTo(1);
            if (System.getenv("SHOTS") != null) {
                host.onFrame(px -> {
                    var img = new java.awt.image.BufferedImage(160, 144, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    img.setRGB(0, 0, 160, 144, px, 0, 160);
                    try { javax.imageio.ImageIO.write(img, "png", new java.io.File(System.getenv("SHOTS") + "/" + user + ".png")); } catch (Exception e) { }
                });
                Thread.sleep(300);
            }
        } finally {
            host.shutdown();
        }
    }
}
