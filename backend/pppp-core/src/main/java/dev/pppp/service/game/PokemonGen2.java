package dev.pppp.service.game;

import dev.pppp.model.GameSummary;
import dev.pppp.model.Progress;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pokémon Gold, Silver and Crystal (English releases), for the scoreboard only: its state is the {@link Progress}
 * itself, with no party box. Addresses are the symbols of pret/pokegold (pokegold.sym and
 * pokesilver.sym agree) and pret/pokecrystal (pokecrystal.sym and pokecrystal11.sym agree), "symbols" branch:
 * wPlayerName, wJohtoBadges (wKantoBadges follows), wHallOfFameCount, wPartyCount (wPartySpecies and wPartyMons
 * follow), wPokedexCaught (wPokedexSeen follows) and PokemonNames in the ROM.
 *
 * <p>Not yet run against a real cartridge. All of it is WRAM bank 1 read through the bus, so a read while the
 * game has another bank switched in is garbage: {@link #read} and ProgressTracker's repeated sample guard that.
 */
final class PokemonGen2 implements GameProfile<Progress> {
    private record Layout(int playerName, int badges, int hallOfFame, int partyCount, int dexCaught, int romNames) {}

    private static final Layout GOLD_SILVER = new Layout(0xD1A3, 0xD57C, 0xD683, 0xDA22, 0xDBE4, 0x6C * 0x4000 + 0x0B74),
            CRYSTAL = new Layout(0xD47D, 0xD857, 0xD95E, 0xDCD7, 0xDE99, 0x14 * 0x4000 + 0x3384);
    private static final int DEX_SIZE = 251, DEX_BYTES = 32, NAME_LENGTH = 11,
            MON_SIZE = 48, MON_LEVEL = 0x1F; // party_struct, its Level field
    /** Bit order of wJohtoBadges, then wKantoBadges (pokecrystal constants/engine_flags.asm). */
    private static final String[] BADGE_NAMES = {"Zephyr", "Hive", "Plain", "Fog", "Mineral", "Storm", "Glacier", "Rising",
            "Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth"};

    private final Layout at;
    private final String[] names = new String[DEX_SIZE + 1]; // species id = Pokédex number in this generation

    /** By header title and game code (0x134, 0x13F), which also tells the English release from the others. */
    static PokemonGen2 forRom(byte[] rom) {
        if (rom.length < 0x150) return null;
        String header = new String(rom, 0x134, 15, StandardCharsets.US_ASCII);
        Layout layout = header.equals("POKEMON_GLDAAUE") || header.equals("POKEMON_SLVAAXE") ? GOLD_SILVER
                : header.equals("PM_CRYSTAL\0BYTE") ? CRYSTAL : null;
        return layout == null || rom.length < layout.romNames + DEX_SIZE * 10 ? null : new PokemonGen2(rom, layout);
    }

    private PokemonGen2(byte[] rom, Layout at) {
        this.at = at;
        for (int i = 1; i <= DEX_SIZE; i++) {
            String name = PokemonRed.titleCase(PokemonRed.text(rom, at.romNames + (i - 1) * 10, 10));
            names[i] = name.isEmpty() ? "#" + i : name;
        }
    }

    @Override
    public int ramStart() {
        return at.playerName;
    }

    @Override
    public int ramLength() {
        return at.dexCaught + 2 * DEX_BYTES - at.playerName;
    }

    @Override
    public Progress read(Ram ram) {
        // As in PokemonRed: a named player and a well-formed party list rule out boot, the title screen and loading.
        int first = ram.u8(at.playerName);
        if (first < 0x80 || first > 0xBF
                || PokemonRed.text(ram.slice(at.playerName, NAME_LENGTH), 0, NAME_LENGTH).length() == NAME_LENGTH) {
            return null;
        }
        int count = ram.u8(at.partyCount), species = at.partyCount + 1, mons = at.partyCount + 8;
        if (count > 6 || ram.u8(species + count) != 0xFF) return null;
        int level = 0;
        for (int i = 0; i < count; i++) {
            int id = ram.u8(mons + i * MON_SIZE), l = ram.u8(mons + i * MON_SIZE + MON_LEVEL);
            // An egg is listed as EGG, not as its species; a member mid-write does not match either.
            if (id >= 1 && id <= DEX_SIZE && id == ram.u8(species + i) && l <= 100) level = Math.max(level, l);
        }
        int bits = ram.u16le(at.badges);
        Set<String> badges = new LinkedHashSet<>();
        for (int i = 0; i < BADGE_NAMES.length; i++) if ((bits & 1 << i) != 0) badges.add(BADGE_NAMES[i]);
        BitSet caught = BitSet.valueOf(ram.slice(at.dexCaught, DEX_BYTES));
        BitSet seen = BitSet.valueOf(ram.slice(at.dexCaught + DEX_BYTES, DEX_BYTES));
        seen.or(caught);
        return new Progress(badges, names(caught), names(seen), level, ram.u8(at.hallOfFame) > 0);
    }

    private Set<String> names(BitSet dex) {
        Set<String> out = new LinkedHashSet<>();
        dex.stream().filter(i -> i < DEX_SIZE).forEach(i -> out.add(names[i + 1]));
        return out;
    }

    @Override
    public Progress progress(Progress state) {
        return state;
    }

    @Override
    public GameSummary summary(Progress s) {
        if (s == null) s = Progress.EMPTY;
        return new GameSummary("Pokémon Gold, Silver or Crystal", List.of(
                new GameSummary.Counter("Badges", s.badges().size(), BADGE_NAMES.length),
                new GameSummary.Counter("Pokédex", s.caught().size(), DEX_SIZE),
                new GameSummary.Counter("Seen", s.seen().size(), DEX_SIZE)), List.of(), List.of(),
                new GameSummary.Collection(0, List.of(), List.of(), List.of()), null);
    }
}
