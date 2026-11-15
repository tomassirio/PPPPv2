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
            g.location(4, 3, 3, ECRUTEAK, ecruteak); // ECRUTEAK_POKECENTER_1F, INDOOR
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
            assertThat(save.location()).isEqualTo("Ecruteak City · Pokémon Center");
            assertThat(save.map()).isEqualTo("ECRUTEAK_POKECENTER_1F");
            assertThat(save.trainerName()).isEqualTo("KRIS");
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
        g.location(20, 2, 3, 0, PokemonRedTest.encode("SPECIAL")); // TRADE_CENTER: SPECIAL_MAP, the room alone
        assertThat(g.profile().read(g.ram()).location()).isEqualTo("Trade Center");
        g.map(27, 1); // no such group
        assertThat(g.profile().read(g.ram()).location()).isNull();
        g.hp(0, 92, 91); // HP above max HP: mid-write
        assertThat(g.profile().read(g.ram())).isNull();
    }

    @Test
    void outdoorsIsTheLandmarkAndMapNumbersArePerGame() {
        byte[] ecruteak = PokemonRedTest.encode("ECRUTEAK CITY");
        ecruteak[8] = 0x1F; // the line break of landmark names
        for (Gen2Ram.At at : new Gen2Ram.At[] {Gen2Ram.GOLD, Gen2Ram.CRYSTAL}) {
            var g = new Gen2Ram(at);
            g.location(4, 9, 1, ECRUTEAK, ecruteak); // ECRUTEAK_CITY, TOWN
            assertThat(g.profile().read(g.ram()).location()).isEqualTo("Ecruteak City");
        }
        // Crystal added maps to the dungeons group: Mt. Mortar's first map is 3.49 in Gold, 3.57 in Crystal.
        var gold = new Gen2Ram(Gen2Ram.GOLD);
        gold.map(3, 49);
        assertThat(gold.profile().read(gold.ram()).map()).isEqualTo("MOUNT_MORTAR_1F_OUTSIDE");
        var crystal = new Gen2Ram(Gen2Ram.CRYSTAL);
        crystal.map(3, 57);
        assertThat(crystal.profile().read(crystal.ram()).map()).isEqualTo("MOUNT_MORTAR_1F_OUTSIDE");
    }

    @Test
    void roomNames() {
        assertThat(PokemonGen2.place("ELMS_LAB", false, "New Bark Town")).isEqualTo("New Bark Town · Elm's Lab");
        assertThat(PokemonGen2.place("PLAYERS_HOUSE_2F", false, "New Bark Town")).isEqualTo("New Bark Town · Your House 2F");
        assertThat(PokemonGen2.place("VIOLET_POKECENTER_1F", false, "Violet City")).isEqualTo("Violet City · Pokémon Center");
        assertThat(PokemonGen2.place("VIOLET_MART", false, "Violet City")).isEqualTo("Violet City · Poké Mart");
        assertThat(PokemonGen2.place("VIOLET_GYM", false, "Violet City")).isEqualTo("Violet City · Gym");
        assertThat(PokemonGen2.place("RADIO_TOWER_3F", false, "Radio Tower")).isEqualTo("Goldenrod City · Radio Tower 3F");
        assertThat(PokemonGen2.place("BURNED_TOWER_B1F", false, "Burned Tower")).isEqualTo("Ecruteak City · Burned Tower B1F");
        assertThat(PokemonGen2.place("OLIVINE_LIGHTHOUSE_2F", false, "Lighthouse")).isEqualTo("Olivine City · Lighthouse 2F");
        assertThat(PokemonGen2.place("RUINS_OF_ALPH_HO_OH_CHAMBER", false, "Ruins of Alph")).isEqualTo("Ruins of Alph · Ho-Oh Chamber");
        assertThat(PokemonGen2.place("MOUNT_MORTAR_B1F", false, "Mt. Mortar")).isEqualTo("Mt. Mortar · B1F");
        assertThat(PokemonGen2.place("MR_POKEMONS_HOUSE", false, "Route 30")).isEqualTo("Route 30 · Mr. Pokémon's House");
        assertThat(PokemonGen2.place("GOLDENROD_PP_SPEECH_HOUSE", false, "Goldenrod City")).isEqualTo("Goldenrod City · House");
        assertThat(PokemonGen2.place("ILEX_FOREST", false, "Ilex Forest")).isEqualTo("Ilex Forest");
        assertThat(PokemonGen2.place("ROUTE_32", true, "Route 32")).isEqualTo("Route 32");
        assertThat(PokemonGen2.place("POKECENTER_2F", false, null)).isEqualTo("Pokémon Center 2F");
        assertThat(PokemonGen2.place("COLOSSEUM", false, null)).isEqualTo("Colosseum");
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
