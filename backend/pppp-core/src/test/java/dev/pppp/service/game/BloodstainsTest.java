package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.pppp.repository.BloodstainRepository;
import dev.pppp.service.PlayerConsoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A lost battle leaves a stain where the player stood, naming the foe; a won one leaves none. Crystal's RAM is a fake
 * (a byte array behind PlayerConsoles.readBytes); names come from the real ROM in data/ (skipped without it).
 */
class BloodstainsTest {
    final int[] ram = new int[0x10000];
    final PlayerConsoles consoles = mock(PlayerConsoles.class);
    final Ghosts ghosts = mock(Ghosts.class);
    Bloodstains stains;

    void setUp() throws Exception {
        Path rom = Path.of("../../data/roms/pokemon-crystal-version-usa-europe-rev-1.gbc");
        assumeTrue(Files.exists(rom), "no Crystal ROM in data/");
        PokecenterDig dig = mock(PokecenterDig.class);
        when(dig.game("a")).thenReturn(new PokecenterDig.Game(null, PokemonGen2.forRom(Files.readAllBytes(rom)), 0, null));
        when(consoles.running()).thenReturn(Set.of("a"));
        when(consoles.readBytes(eq("a"), anyInt(), anyInt())).thenAnswer(i -> {
            int at = i.getArgument(1), n = i.getArgument(2);
            byte[] out = new byte[n];
            for (int k = 0; k < n; k++) out[k] = (byte) ram[at + k];
            return out;
        });
        BloodstainRepository repo = mock(BloodstainRepository.class);
        when(repo.all()).thenReturn(List.of());
        stains = new Bloodstains(consoles, dig, ghosts, repo, mock(Replays.class));
        when(ghosts.lastStood("a")).thenReturn(new Ghosts.Where("ROUTE_34", 10 * 16, 7 * 16));
    }

    void battle(int trainerClass, int id, int species, int level, int result) {
        var at = Bloodstains.CRYSTAL;
        ram[at.battleMode()] = trainerClass == 0 ? 1 : 2;
        ram[at.trainerClass()] = trainerClass;
        ram[at.trainerId()] = id;
        ram[at.linkMode()] = 0;
        ram[at.enemySpecies()] = species;
        ram[at.enemyLevel()] = level;
        byte[] cls = {(byte) 0x8B, (byte) 0x84, (byte) 0x80, (byte) 0x83, (byte) 0x84, (byte) 0x91, 0x50}; // LEADER@
        for (int k = 0; k < cls.length; k++) ram[at.otClassName() + k] = cls[k] & 0xFF;
        stains.watch();
        ram[at.battleMode()] = 0;
        ram[at.battleResult()] = result;
        stains.watch();
    }

    @Test
    void aLossLeavesAStainNamingTheFoeAndAWinNone() throws Exception {
        setUp();
        battle(2, 1, 241, 20, 0); // beat Whitney's Miltank: WIN
        when(ghosts.where("b")).thenReturn(new Ghosts.Where("ROUTE_34", 10 * 16, 7 * 16));
        assertThat(stains.visible("b")).isEmpty();

        battle(2, 1, 241, 20, 1); // LOSE
        var seen = stains.visible("b");
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).foe()).isEqualTo("Leader Whitney's Miltank Lv 20");
        assertThat(seen.get(0).here()).isTrue();
        assertThat(seen.get(0).mine()).isFalse();

        battle(0, 0, 92, 17, 1); // a wild Gastly
        assertThat(stains.visible("b")).extracting(Bloodstains.Shown::foe).contains("a wild Gastly Lv 17");
        when(ghosts.where("b")).thenReturn(new Ghosts.Where("ROUTE_35", 10 * 16, 7 * 16));
        assertThat(stains.visible("b")).isEmpty();
    }
}
