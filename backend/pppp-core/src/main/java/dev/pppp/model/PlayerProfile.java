package dev.pppp.model;

import java.util.List;

/**
 * GET /players/{userId}/profile. achievements: the whole catalogue for this player; progress/goal only where the
 * stored data counts towards it (Pokédex caught, Kanto badges, eggs hatched, link partners), else null. A locked
 * legendary is hidden: name and description null (Achievement#seenBy). stats: trades received over the link cable
 * and the save's link battle record. recent: their newest activity feed entries (the PROGRESS events), max 10.
 */
public record PlayerProfile(ScoreRow row, List<PartyMon> party, List<Entry> achievements, Stats stats,
                            List<ScoreEvent> recent) {
    public record Entry(String id, String category, String name, String description, int points, String rarity,
                        boolean hidden, boolean unlocked, Integer progress, Integer goal) {}

    /** partners: user ids of every Pib they have linked with. */
    public record Stats(int caught, int seen, String playTime, int trades, int linkWins, int linkLosses,
                        List<String> partners) {}
}
