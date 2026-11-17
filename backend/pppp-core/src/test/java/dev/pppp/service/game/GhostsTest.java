package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GhostsTest {

    /** A real step left in Gold's Pokémon Center 2F: wXCoord 5 -> 4 while hSCX runs 0, 254, ..., 240. */
    @Test
    void worldFollowsTheScrollThroughAStep() {
        int anchor = 0 - 16 * 5; // standing on x 5 with the scroll at 0
        assertThat(Ghosts.world(5, 0, anchor)).isEqualTo(80);
        assertThat(Ghosts.world(5, 254, anchor)).isEqualTo(78);
        assertThat(Ghosts.world(4, 242, anchor)).isEqualTo(66); // wXCoord flips before the scroll is done
        assertThat(Ghosts.world(4, 240, anchor)).isEqualTo(64);
        assertThat(Ghosts.world(6, 16, anchor)).isEqualTo(96); // and a step right
    }

    @Test
    void drawBlendsAndClipsAtTheEdge() {
        int[] screen = new int[160 * 144], sprite = new int[16 * 16];
        sprite[0] = 0xFFFFFFFF; // top left opaque white, the rest see-through
        sprite[15] = 0xFFFFFFFF;
        Ghosts.draw(screen, sprite, 150, 0); // columns 10-15 fall off the right edge
        assertThat(screen[150]).isNotZero().isLessThan(0xFFFFFF);
        assertThat(screen[151]).isZero();
        Ghosts.draw(screen, sprite, -15, 5); // only its last column shows, at x 0
        assertThat(screen[5 * 160]).isNotZero();
    }
}
