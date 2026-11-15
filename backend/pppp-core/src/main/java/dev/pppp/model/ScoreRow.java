package dev.pppp.model;

import java.util.List;

/**
 * A scoreboard row (GET /scoreboard, SCOREBOARD_CHANGED), ranked by trainerScore, then achievementPoints. points
 * is the older per-step tally (badges, Pokédex, levels; see ProgressTracker). game is the cartridge id;
 * online = console in memory. party (eggs included, in order), location (TrainerCard's: "Route 42",
 * "Violet City · Pokémon Center", "Trade Center"; null when unknown), playTime ("H:MM", null when unknown), the badge
 * bitmasks (wJohtoBadges, wKantoBadges) and trainerName (wPlayerName; null for other games or before a reading) are
 * from their last confirmed Gen 2 reading.
 */
public record ScoreRow(String userId, int points, int badges, int caught, int seen, String game, boolean online,
                       int trainerScore, String title, String grade, int achievementPoints, int achievementsUnlocked,
                       List<Member> party, String location, String playTime, int johtoBadges, int kantoBadges,
                       String trainerName) {
    /** species: national Pokédex number; hpPercent 0-100 (above 0 while any HP is left); status as PartyMon's. */
    public record Member(int species, int level, int hpPercent, String status, boolean shiny, boolean egg) {
        public static Member of(PartyMon m) {
            int pct = m.maxHp() <= 0 ? 0 : Math.min(100, (m.hp() * 100 + m.maxHp() - 1) / m.maxHp());
            return new Member(m.species(), m.level(), pct, m.status(), m.shiny(), m.egg());
        }
    }
}
