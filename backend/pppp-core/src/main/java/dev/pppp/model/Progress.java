package dev.pppp.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the scoreboard counts of one player's game, by name so it reads the same for every game: badges earned,
 * species caught and seen (seen includes caught), the highest party level, and whether they entered the Hall of
 * Fame (false for games whose profile cannot tell).
 */
public record Progress(Set<String> badges, Set<String> caught, Set<String> seen, int level, boolean hallOfFame) {
    public static final Progress EMPTY = new Progress(Set.of(), Set.of(), Set.of(), 0, false);

    /** Everything ever reached in either: progress only grows, so nothing is awarded twice. */
    public Progress merge(Progress o) {
        return new Progress(union(badges, o.badges), union(caught, o.caught), union(seen, o.seen),
                Math.max(level, o.level), hallOfFame || o.hallOfFame);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new LinkedHashSet<>(a);
        all.addAll(b);
        return all;
    }
}
