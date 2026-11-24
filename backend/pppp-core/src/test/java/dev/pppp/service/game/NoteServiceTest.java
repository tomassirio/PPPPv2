package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.pppp.repository.NoteRepository;
import dev.pppp.service.CartridgeService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoteServiceTest {
    Ghosts ghosts = mock(Ghosts.class);
    NoteRepository repo = mock(NoteRepository.class);
    NoteService notes;

    @BeforeEach
    void setUp() {
        CartridgeService carts = mock(CartridgeService.class);
        when(carts.list()).thenReturn(List.of());
        when(repo.all()).thenReturn(List.of());
        when(repo.raters(anyString())).thenReturn(Set.of());
        when(repo.good(anyString(), anyString())).thenReturn(true);
        notes = new NoteService(repo, ghosts, carts);
    }

    void stand(String user, String map, int tileX, int tileY) {
        when(ghosts.where(user)).thenReturn(new Ghosts.Where(map, tileX * 16, tileY * 16));
    }

    @Test
    void leavesWhereThePlayerStandsAndOthersSeeIt() {
        stand("a", "ROUTE_29", 10, 7);
        var n = notes.leave("a", 1, "tall grass");
        assertThat(n.text()).isEqualTo("Beware of tall grass");
        assertThat(n.x()).isEqualTo(10);

        stand("b", "ROUTE_29", 12, 7); // two steps to the right of it
        var seen = notes.visible("b");
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).x()).isEqualTo(Ghosts.SCREEN_X - 32);
        assertThat(seen.get(0).here()).isFalse();
        stand("b", "ROUTE_29", 10, 7);
        assertThat(notes.visible("b").get(0).here()).isTrue();
        stand("b", "ROUTE_30", 10, 7);
        assertThat(notes.visible("b")).isEmpty();

        notes.rate("b", n.id());
        stand("b", "ROUTE_29", 10, 7);
        assertThat(notes.visible("b").get(0).good()).isEqualTo(1);
        assertThatThrownBy(() -> notes.rate("a", n.id())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneNotePerTileAndTenAtMost() {
        stand("a", "ROUTE_29", 1, 1);
        notes.leave("a", 0, "AAAA");
        notes.leave("a", 2, "Surf");
        stand("a", "ROUTE_29", 1, 1);
        assertThat(notes.visible("a")).extracting(NoteService.Shown::text).containsExactly("Try Surf");
        for (int i = 0; i < 12; i++) {
            stand("a", "ROUTE_31", i, 0);
            notes.leave("a", 0, "AAAA");
        }
        stand("a", "ROUTE_31", 11, 0);
        assertThat(notes.visible("a").size() + 1).isLessThanOrEqualTo(NoteService.MAX_PER_PLAYER + 1);
    }

    @Test
    void refusesUnknownWordsAndMovingPlayers() {
        stand("a", "ROUTE_29", 1, 1);
        assertThatThrownBy(() -> notes.leave("a", 0, "<script>")).isInstanceOf(IllegalArgumentException.class);
        when(ghosts.where("a")).thenReturn(new Ghosts.Where("ROUTE_29", 20, 16)); // mid-step
        assertThatThrownBy(() -> notes.leave("a", 0, "AAAA")).isInstanceOf(IllegalStateException.class);
        when(ghosts.where("a")).thenReturn(null); // in a menu
        assertThatThrownBy(() -> notes.leave("a", 0, "AAAA")).isInstanceOf(IllegalStateException.class);
    }
}
