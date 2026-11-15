package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pppp.model.Achievement;
import dev.pppp.model.TrainerLog;
import dev.pppp.service.game.Gen2Save.Mon;
import dev.pppp.service.game.Gen2Save.Slot;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Achievements told from two readings, the log and the battery save; and the rules of the save as it is. */
class Gen2MomentsTest {
    static final int ME = 0x1234;

    /** A Crystal save on map with this Hall of Fame count, party slots and phone contacts. */
    static Gen2Save save(String map, int hof, List<Slot> slots, Set<Integer> contacts) {
        List<Mon> party = slots.stream().filter(s -> !s.egg())
                .map(s -> new Mon(s.species(), 50, s.shiny(), s.ot() == ME, 0, 0)).toList();
        return new Gen2Save(ME, 0, 0, hof, false, true, false, false, new BitSet(), new BitSet(), slots.size(), party,
                Set.of(), 0, 0, 0, contacts.size(), 1, 0, null, List.of(), "KRIS", true, map, 0, contacts, slots);
    }

    static Gen2Save save(String map, List<Slot> slots) {
        return save(map, 0, slots, Set.of());
    }

    static Slot mon(int species, int hp) {
        return new Slot(species, false, false, hp, ME, species * 7, false);
    }

    static TrainerLog log(TrainerLog log, Gen2Save before, Gen2Save now) {
        return Achievements.log(log, before, now, null, false, null);
    }

    static List<String> earned(Gen2Save before, Gen2Save now, TrainerLog log, Gen2Save.Sram sram) {
        return Achievements.earned(new Achievements.Moment(before, now, log, sram));
    }

    @Test
    void hatchingCountsEggsThatBecomeTheirPokemon() {
        Slot egg = new Slot(172, true, true, 0, ME, 0x2AAA, true), pichu = new Slot(172, false, true, 20, ME, 0x2AAA, true);
        var before = save("ROUTE_34", List.of(mon(155, 30), egg));
        var after = save("ROUTE_34", List.of(mon(155, 30), pichu));
        assertThat(Achievements.hatched(before, after)).containsExactly(pichu);
        assertThat(Achievements.hatched(null, after)).isEmpty();
        // Another Pokémon in the egg's slot (a PC swap) is no hatch.
        assertThat(Achievements.hatched(before, save("ROUTE_34", List.of(mon(155, 30), mon(172, 20))))).isEmpty();

        TrainerLog log = log(new TrainerLog(Set.of(), 9, Set.of(), 0, false, null, 0, 0), before, after);
        assertThat(log.hatched()).isEqualTo(10);
        assertThat(earned(before, after, log, null)).contains("breeder", "odd-one");
        // A shiny without Dizzy Punch is bred, not the Odd Egg.
        var bred = save("ROUTE_34", List.of(mon(155, 30), new Slot(172, false, true, 20, ME, 0x2AAA, false)));
        assertThat(earned(before, bred, log, null)).doesNotContain("odd-one");
    }

    @Test
    void cardCollectorNeedsEveryNumberEverSeen() {
        assertThat(Achievements.phoneNumbers(false)).hasSize(32).doesNotContain(2, 8, 9, 10, 25, 38);
        assertThat(Achievements.phoneNumbers(true)).hasSize(33).contains(38);
        TrainerLog log = TrainerLog.EMPTY;
        List<Integer> all = Achievements.phoneNumbers(true).stream().sorted().toList();
        for (int i = 0; i < all.size(); i += 10) { // the Pokégear holds 10 at once
            var now = save("ROUTE_30", 0, List.of(mon(155, 30)), Set.copyOf(all.subList(i, Math.min(all.size(), i + 10))));
            log = log(log, null, now);
            assertThat(earned(null, now, log, null).contains("card-collector")).isEqualTo(i + 10 >= all.size());
        }
    }

    @Test
    void untouchableNeedsACleanRunFromWillsRoomToTheHallOfFame() {
        List<Slot> team = List.of(mon(157, 100), mon(130, 80));
        String[] run = {"WILLS_ROOM", "KOGAS_ROOM", "BRUNOS_ROOM", "KARENS_ROOM", "LANCES_ROOM", "HALL_OF_FAME"};
        TrainerLog log = TrainerLog.EMPTY;
        Gen2Save before = save("INDIGO_PLATEAU_POKECENTER_1F", team);
        for (String map : run) {
            Gen2Save now = save(map, team);
            log = log(log, before, now);
            before = now;
        }
        assertThat(log.eliteFour()).isEqualTo("clean");
        Gen2Save famous = save("HALL_OF_FAME", 1, team, Set.of());
        log = log(log, before, famous);
        assertThat(earned(before, famous, log, null)).contains("untouchable");
        assertThat(log(log, famous, save("PLAYERS_HOUSE_2F", 1, team, Set.of())).eliteFour()).isNull();

        // A faint in Bruno's room spoils the run, even after a Revive.
        log = TrainerLog.EMPTY;
        before = save("INDIGO_PLATEAU_POKECENTER_1F", team);
        for (String map : run) {
            Gen2Save now = save(map, map.equals("BRUNOS_ROOM") ? List.of(mon(157, 0), mon(130, 80)) : team);
            log = log(log, before, now);
            before = now;
        }
        famous = save("HALL_OF_FAME", 1, team, Set.of());
        log = log(log, before, famous);
        assertThat(log.eliteFour()).isEqualTo("fainted");
        assertThat(earned(before, famous, log, null)).doesNotContain("untouchable");

        // Not seen from Will's room (a restart in between): not told, not guessed.
        assertThat(log(TrainerLog.EMPTY, save("KOGAS_ROOM", team), save("KARENS_ROOM", team)).eliteFour()).isNull();
    }

    @Test
    void tradesAreNewPokemonInTheTradeCenter() {
        Slot given = mon(155, 30), got = new Slot(64, false, false, 40, 0x9999, 0x1111, false);
        var before = save("TRADE_CENTER", List.of(mon(157, 100), given));
        var after = save("TRADE_CENTER", List.of(mon(157, 100), got));
        TrainerLog log = Achievements.log(TrainerLog.EMPTY, before, after, "misty@x.y", true, null);
        assertThat(log.trades()).isEqualTo(1);
        assertThat(log.crossVersion()).isTrue();
        assertThat(log.partners()).containsExactly("misty@x.y");
        // Kadabra evolving into Alakazam right after is the same Pokémon (OT, DVs): no second trade.
        var evolved = save("TRADE_CENTER", List.of(mon(157, 100), new Slot(65, false, false, 40, 0x9999, 0x1111, false)));
        assertThat(Achievements.log(log, after, evolved, "misty@x.y", true, null).trades()).isEqualTo(1);
        assertThat(earned(before, after, log, null)).contains("plugged-in", "first-trade", "cross-version")
                .doesNotContain("social-butterfly");
        // Outside the trade rooms a new Pokémon (a catch, a gift) is no trade; the same version is not cross-version.
        assertThat(log(TrainerLog.EMPTY, save("ROUTE_30", List.of(given)), save("ROUTE_30", List.of(given, got))).trades())
                .isZero();
        assertThat(Achievements.log(TrainerLog.EMPTY, before, after, "brock@x.y", false, null).crossVersion()).isFalse();

        TrainerLog five = new TrainerLog(Set.of(), 0, Set.of("a", "b", "c", "d", "e"), 0, false, null, 3, 1);
        assertThat(earned(null, after, five, null)).contains("social-butterfly", "link-champion");
    }

    @Test
    void theBatterySaveHoldsTheBoxesTheLinkRecordAndTheBattleTower() {
        for (boolean crystal : new boolean[] {false, true}) {
            byte[] sav = new byte[0x8000 + 44]; // + Coffee GB's clock footer
            java.util.Arrays.fill(sav, (byte) 0xFF); // what a fresh cartridge reads
            assertThat(PokemonGen2.sram(crystal, sav, 0)).isNull(); // never saved
            sav[0x2008] = 99; // sCheckValue1
            int sBox = crystal ? 0x2D10 : 0x2D6C, wins = crystal ? 0x3260 : 0x31BA;
            box(sav, sBox, 1, 2, 3);
            box(sav, 0x4000, 4); // sBox1: the current box's stale copy (wCurBox 0), skipped
            box(sav, 0x4000 + 0x450, 5, 0xFD); // sBox2, with an egg
            box(sav, 0x6000 + 6 * 0x450, 251); // sBox14
            sav[wins] = 0x01; // 256 + 2 wins, big-endian; 3 losses
            sav[wins + 1] = 0x02;
            sav[wins + 2] = 0;
            sav[wins + 3] = 3;
            if (crystal) sav[0x3E45] = 4; // BATTLETOWER_RECEIVED_REWARD
            var sram = PokemonGen2.sram(crystal, sav, 0);
            assertThat(sram.boxed().stream().boxed().toList()).containsExactly(1, 2, 3, 5, 251);
            assertThat(sram.linkWins()).isEqualTo(258);
            assertThat(sram.linkLosses()).isEqualTo(3);
            assertThat(sram.towerState()).isEqualTo(crystal ? 4 : 0);
            assertThat(PokemonGen2.sram(crystal, sav, 1).boxed().get(4)).isTrue(); // in box 2 now: box 1 counts
        }
        BitSet boxed = new BitSet();
        boxed.set(1, 251); // all but Celebi
        var party = save("ROUTE_30", List.of(mon(251, 100)));
        var full = new Gen2Save.Sram(boxed, 1, 0, 4);
        TrainerLog log = Achievements.log(TrainerLog.EMPTY, null, party, null, false, full);
        assertThat(log.linkWins()).isEqualTo(1);
        assertThat(earned(null, party, log, full)).contains("living-history", "tower-climber", "link-champion");
        assertThat(earned(null, save("ROUTE_30", List.of(mon(250, 100))), log, full)).doesNotContain("living-history");
        assertThat(earned(null, party, log, new Gen2Save.Sram(boxed, 0, 0, 2))).doesNotContain("tower-climber");
    }

    /** A well-formed box at offset holding these species (0xFD: an egg). */
    static void box(byte[] sav, int at, int... species) {
        sav[at] = (byte) species.length;
        for (int i = 0; i < species.length; i++) {
            sav[at + 1 + i] = (byte) species[i];
            sav[at + 22 + i * 0x20] = (byte) (species[i] == 0xFD ? 175 : species[i]);
        }
        sav[at + 1 + species.length] = (byte) 0xFF;
    }

    @Test
    void eachBadgeRuleReadsItsOwnBit() {
        String[] ids = {"zephyr-badge", "hive-badge", "plain-badge", "fog-badge", "mineral-badge", "storm-badge",
                "glacier-badge", "rising-badge"}; // pret engine_flags.asm order: Mineral (4) before Storm (5)
        for (int bit = 0; bit < 8; bit++) {
            var s = new Gen2Save(1, 1 << bit, 0, 0, false, false, false, false, new BitSet(), new BitSet(), 0,
                    List.of(), Set.of(), 0, 0, 0, 0, 0, 0, null, List.of());
            assertThat(Achievements.earned(null, s)).containsExactly(ids[bit]);
        }
    }

    @Test
    void hallOfFameMomentsLookAtThePartyThen() {
        Mon starter = new Mon(157, 70, false, true, 0x14, 0x14);
        var before = new Gen2Save(1, 0xFF, 0, 0, false, true, false, true, new BitSet(), new BitSet(), 1,
                List.of(starter), Set.of(Gen2Save.MASTER_BALL), 0, 0, 0, 0, 5, 0, null, List.of());
        var after = new Gen2Save(1, 0xFF, 0, 1, false, true, false, true, new BitSet(), new BitSet(), 1,
                List.of(starter), Set.of(Gen2Save.MASTER_BALL), 0, 0, 0, 0, 5, 30, null, List.of());
        assertThat(Achievements.earned(before, after)).contains("champion-of-johto", "solo-run", "self-control",
                "speedrunner", "mono-type", "silphs-finest", "level-up");
        assertThat(Achievements.earned(null, after)).doesNotContain("solo-run", "self-control", "speedrunner",
                "mono-type"); // the moment was not seen
        assertThat(Achievements.earned(after, after)).doesNotContain("solo-run");
    }

    @Test
    void rarityFollowsPointsAndLockedLegendariesAreHidden() {
        assertThat(IntStream.of(1, 3, 5, 10, 25, 50).mapToObj(Achievement::rarity))
                .containsExactly("common", "common", "uncommon", "rare", "epic", "legendary");
        Achievement summit = Achievements.catalogue().stream().filter(a -> a.id().equals("the-silent-summit"))
                .findFirst().orElseThrow();
        assertThat(summit.rarity()).isEqualTo("legendary");
        Achievement locked = summit.seenBy(null);
        assertThat(locked.hidden()).isTrue();
        assertThat(locked.name()).isNull();
        assertThat(locked.description()).isNull();
        assertThat(locked.note()).isNull();
        assertThat(locked.points()).isEqualTo(50);
        Achievement unlocked = summit.seenBy(123L);
        assertThat(unlocked.hidden()).isFalse();
        assertThat(unlocked.name()).isEqualTo("The Silent Summit");
        Achievement epic = Achievements.catalogue().stream().filter(a -> a.points() == 25).findFirst().orElseThrow();
        assertThat(epic.seenBy(null).name()).isNotNull();
    }
}
