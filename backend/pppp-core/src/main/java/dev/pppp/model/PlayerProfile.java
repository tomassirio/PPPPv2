package dev.pppp.model;

import java.util.List;

/**
 * GET /players/{userId}/profile. achievements: the whole catalogue for this player; progress/goal only where the
 * stored data counts towards it (Pokédex caught, Kanto badges), else null. stats: only what is tracked (there is
 * no trade or link battle counter). recent: their newest activity feed entries (the PROGRESS events), max 10.
 */
public record PlayerProfile(ScoreRow row, List<PartyMon> party, List<Entry> achievements, Stats stats,
                            List<ScoreEvent> recent) {
    public record Entry(String id, String name, String description, int points, boolean unlocked, Integer progress,
                        Integer goal) {}

    public record Stats(int caught, int seen, String playTime) {}
}
