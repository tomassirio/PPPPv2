package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pppp.model.PartyMon;
import dev.pppp.model.ScoreRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The party detail, location and play time read from Gen 2 RAM, with names from the ROM. */
class Gen2PartyTest {
    static final int QUAGSIRE = 195, TOGEPI = 175, FERALIGATR = 160, LEFTOVERS = 0x92, SURF = 0x39, EARTHQUAKE = 0x59,
            GROUND = 0x04, ECRUTEAK = 22;

    @Test
    void goldAndCrystalReadTheSameParty() {
        for (Gen2Ram.At at : new Gen2Ram.At[] {Gen2Ram.GOLD, Gen2Ram.CRYSTAL}) {
            var g = new Gen2Ram(at);
            g.speciesName(QUAGSIRE, "QUAGSIRE");
            g.speciesName(FERALIGATR, "FERALIGATR");
            g.type(QUAGSIRE, Gen2Ram.WATER, GROUND);
            g.type(FERALIGATR, Gen2Ram.WATER, Gen2Ram.WATER);
            g.nameList(at.itemNames(), 0xFF, Map.of(LEFTOVERS, "LEFTOVERS"));
            g.nameList(at.moveNames(), 251, Map.of(SURF, "SURF", EARTHQUAKE, "EARTHQUAKE"));
            g.mon(0, QUAGSIRE, 34, Gen2Ram.PLAYER_ID, 0x2A, 0xAA); // shiny
            g.nickname(0, "MUDDY");
            g.hp(0, 30, 91);
            g.status(0, 0x08);
            g.item(0, LEFTOVERS);
            g.moves(0, SURF, EARTHQUAKE);
            g.egg(1, TOGEPI);
            g.nickname(1, "EGG");
            g.mon(2, FERALIGATR, 50, Gen2Ram.PLAYER_ID);
            g.nickname(2, "FERALIGATR");
            g.hp(2, 0, 150);
            g.status(2, 0x40); // paralysed, but fainted wins
            byte[] ecruteak = PokemonRedTest.encode("ECRUTEAK CITY");
            ecruteak[8] = 0x1F; // the line break of landmark names
            g.location(10, 3, ECRUTEAK, ecruteak);
            g.hours(12);
            g.minutes(5);

            var profile = g.profile();
            Gen2Save save = profile.read(g.ram());

            assertThat(save.members()).as(at.header()).containsExactly(
                    new PartyMon(QUAGSIRE, "Quagsire", "MUDDY", 34, 30, 91, "PSN", "Leftovers",
                            List.of("Surf", "Earthquake"), true, false, List.of("water", "ground")),
                    new PartyMon(TOGEPI, "Egg", null, 1, 12, 12, null, null, List.of(), false, true, List.of()),
                    new PartyMon(FERALIGATR, "Feraligatr", null, 50, 0, 150, "FNT", null, List.of(), false, false,
                            List.of("water")));
            assertThat(save.location()).isEqualTo("Ecruteak City");
            assertThat(PokemonGen2.playTime(save)).isEqualTo("12:05");
            assertThat(profile.summary(save).playTime()).isEqualTo("12:05");
            assertThat(save.members().stream().map(ScoreRow.Member::of)).containsExactly(
                    new ScoreRow.Member(QUAGSIRE, 34, 33, "PSN", true, false),
                    new ScoreRow.Member(TOGEPI, 1, 100, null, false, true),
                    new ScoreRow.Member(FERALIGATR, 50, 0, "FNT", false, false));
        }
    }

    @Test
    void unknownPlacesAndHalfWrittenMembersGiveNothing() {
        var g = new Gen2Ram(Gen2Ram.CRYSTAL);
        g.mon(0, QUAGSIRE, 34, Gen2Ram.PLAYER_ID);
        assertThat(g.profile().read(g.ram()).location()).isNull(); // no map yet: group 0
        g.location(10, 3, 0, PokemonRedTest.encode("SPECIAL")); // SPECIAL_MAP
        assertThat(g.profile().read(g.ram()).location()).isNull();
        g.hp(0, 92, 91); // HP above max HP: mid-write
        assertThat(g.profile().read(g.ram())).isNull();
    }

    @Test
    void statusBits() {
        assertThat(PokemonGen2.status(0x03, 10)).isEqualTo("SLP");
        assertThat(PokemonGen2.status(0x10, 10)).isEqualTo("BRN");
        assertThat(PokemonGen2.status(0x20, 10)).isEqualTo("FRZ");
        assertThat(PokemonGen2.status(0x40, 10)).isEqualTo("PAR");
        assertThat(PokemonGen2.status(0, 10)).isNull();
    }
}
