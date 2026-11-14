package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PokemonRedTest {
    static final int BULBASAUR = 0x99, IVYSAUR = 0x09, PIDGEY = 0x24; // internal species ids

    PokemonRed red;
    byte[] mem;

    @BeforeEach
    void setUp() {
        byte[] rom = new byte[0x42000];
        System.arraycopy("POKEMON RED".getBytes(), 0, rom, 0x134, 11);
        rom[0x14A] = 1;
        name(rom, BULBASAUR, "BULBASAUR", 1);
        name(rom, IVYSAUR, "IVYSAUR", 2);
        name(rom, PIDGEY, "PIDGEY", 16);
        assertThat(PokemonRed.matches(rom)).isTrue();
        red = new PokemonRed(rom);

        mem = new byte[red.ramLength()];
        put(0xD158, encode("ASH"));
        put(0xD163, new byte[] {0, (byte) 0xFF});
    }

    @Test
    void notInPlayWithoutAPlayerName() {
        mem = new byte[red.ramLength()];
        assertThat(red.read(ram())).isNull();
    }

    @Test
    void readsPartyBadgesAndPokedex() {
        assertThat(red.progress(red.read(ram()))).isEqualTo(dev.pppp.model.Progress.EMPTY);
        addMon(0, IVYSAUR, "IVYSAUR", 6, 20);
        dexOwned(1);
        dexOwned(2);
        put(0xD356, new byte[] {0b1}); // Boulder badge
        var progress = red.progress(red.read(ram()));
        assertThat(progress.badges()).containsExactly("Boulder");
        assertThat(progress.caught()).containsExactly("Bulbasaur", "Ivysaur");
        assertThat(progress.level()).isEqualTo(6);

        put(0xD16B + 1, new byte[] {0, 0}); // fainted
        put(0xDA41, new byte[] {3, 0, 7}); // 3 h, (not maxed), 7 min
        var summary = red.summary(red.read(ram()));
        assertThat(summary.party().getFirst().status()).isEqualTo("FNT");
        assertThat(summary.party().getFirst().icon()).isEqualTo(2); // Ivysaur's Pokédex number
        assertThat(summary.badges()).extracting(b -> b.earned()).containsExactly(true, false, false, false, false, false, false, false);
        assertThat(summary.badges().getFirst().place()).isEqualTo("Pewter Gym");
        assertThat(summary.collection().owned()).containsExactly(1, 2);
        assertThat(summary.playTime()).isEqualTo("3h 07m");
        assertThat(summary.collection().seen()).containsExactly(1, 2); // owned counts as seen
        assertThat(summary.collection().names().get(1)).isEqualTo("Ivysaur");
        assertThat(summary.collection().names().get(15)).isNull(); // unseen: not given away
    }

    @Test
    void playTimeShowsDaysOnceADayHasPassed() {
        assertThat(PokemonRed.playTime(4, 26)).isEqualTo("4h 26m");
        assertThat(PokemonRed.playTime(53, 7)).isEqualTo("2d 5h 07m");
        assertThat(PokemonRed.playTime(255, 59)).isEqualTo("10d 15h 59m");
        assertThat(PokemonRed.playTime(1, 99)).isNull();
    }

    @Test
    void starterPokedexPreviewIsNotACatch() {
        mem[off(0xD2F7)] = 0b0100_1011; // the game flags 1, 2, 4 and 7 while showing a starter's entry
        assertThat(red.progress(red.read(ram())).caught()).isEmpty();
    }

    @Test
    void halfAddedPokemonIsSkippedNotOutOfPlay() {
        addMon(0, BULBASAUR, "BULBASAUR", 5, 20);
        mem[off(0xD16B)] = 0; // species list says Bulbasaur, its data is not written yet (nickname prompt)
        var naming = red.read(ram());
        assertThat(naming).isNotNull();
        assertThat(naming.party()).isEmpty();
        mem[off(0xD16B)] = (byte) BULBASAUR;
        assertThat(red.read(ram()).party()).hasSize(1);
    }

    @Test
    void partyShowsNicknamesOrSpecies() {
        addMon(0, BULBASAUR, "BULBASAUR", 5, 20);
        addMon(1, PIDGEY, "BIRDY", 9, 30);
        assertThat(red.summary(red.read(ram())).party()).extracting(m -> m.name()).containsExactly("Bulbasaur", "BIRDY");
    }

    // ---- helpers ----

    static void name(byte[] rom, int id, String name, int dex) {
        byte[] n = encode(name);
        System.arraycopy(n, 0, rom, 0x1C21E + (id - 1) * 10, Math.min(n.length, 10));
        rom[0x41024 + id - 1] = (byte) dex;
    }

    static byte[] encode(String s) {
        byte[] b = new byte[s.length() + 1];
        for (int i = 0; i < s.length(); i++) b[i] = (byte) (0x80 + s.charAt(i) - 'A');
        b[s.length()] = 0x50;
        return b;
    }

    void addMon(int slot, int species, String nick, int level, int hp) {
        int count = Math.max(mem[off(0xD163)], slot + 1);
        mem[off(0xD163)] = (byte) count;
        mem[off(0xD164 + slot)] = (byte) species;
        mem[off(0xD164 + count)] = (byte) 0xFF;
        int base = 0xD16B + slot * 44;
        mem[off(base)] = (byte) species;
        put(base + 1, new byte[] {0, (byte) hp});
        mem[off(base + 0x21)] = (byte) level;
        put(base + 0x22, new byte[] {0, (byte) hp});
        put(0xD2B5 + slot * 11, encode(nick));
    }

    void dexOwned(int dex) {
        mem[off(0xD2F7 + (dex - 1) / 8)] |= (byte) (1 << ((dex - 1) % 8));
    }

    void put(int address, byte[] bytes) {
        System.arraycopy(bytes, 0, mem, off(address), bytes.length);
    }

    int off(int address) {
        return address - red.ramStart();
    }

    Ram ram() {
        return new Ram(red.ramStart(), mem.clone());
    }

    @Test
    void playerSpriteComesFromTheSpriteSheetTable() throws Exception {
        byte[] rom = new byte[0x42000];
        System.arraycopy("POKEMON RED".getBytes(), 0, rom, 0x134, 11);
        rom[0x14A] = 1;
        assertThat(new PokemonRed(rom).playerSprite()).isNull(); // empty table: no sprite, no crash
        rom[0x17B27] = (byte) 0x80; // dw $4180, db $C0, db bank 5 -> ROM offset 0x14180
        rom[0x17B28] = 0x41;
        rom[0x17B29] = (byte) 0xC0;
        rom[0x17B2A] = 5;
        rom[0x14180] = (byte) 0x80; // first frame, top-left pixel: colour 1 (low plane bit only)
        rom[0x14180 + 3 * 16 + 15] = 0x01; // tile 3 (bottom-right), last row, last pixel: colour 2

        var img = ImageIO.read(new ByteArrayInputStream(new PokemonRed(rom).playerSprite()));
        assertThat(img.getWidth()).isEqualTo(16);
        assertThat(img.getHeight()).isEqualTo(96);
        assertThat(img.getRGB(0, 0)).isEqualTo(0xFFF8D0A8);
        assertThat(img.getRGB(15, 15)).isEqualTo(0xFFE04848);
        assertThat(img.getRGB(1, 0) >>> 24).isZero(); // colour 0 is transparent
    }
}
