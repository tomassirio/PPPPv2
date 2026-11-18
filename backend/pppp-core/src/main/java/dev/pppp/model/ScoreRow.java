package dev.pppp.model;

import java.util.List;

/**
 * A scoreboard row (GET /scoreboard, SCOREBOARD_CHANGED), ranked by points + achievementPoints (uncapped; the
 * capped trainerScore breaks ties). points is the per-step tally (badges, Pokédex, levels, link battles won; see ProgressTracker). game is the cartridge id;
 * online = console in memory. party (eggs included, in order), location (TrainerCard's: "Route 42",
 * "Violet City · Pokémon Center", "Trade Center"; null when unknown), playTime ("H:MM", null when unknown), the badge
 * bitmasks (wJohtoBadges, wKantoBadges) and trainerName (wPlayerName; null for other games or before a reading) are
 * from their last confirmed Gen 2 reading.
 * link: their link-cable record (TrainerLog): battles won/lost (the save's link record), Pokémon received in trades,
 * and how many different Pibs they have linked with.
 * cableClub: how far a Gold/Silver/Crystal player is towards the Cable Club opening (TrainerCard.cableClub: 3 is
 * open), -1 for another game (nothing to wait for).
 */
public record ScoreRow(String userId, int points, int badges, int caught, int seen, String game, boolean online,
                       int trainerScore, String title, String grade, int achievementPoints, int achievementsUnlocked,
                       List<Member> party, String location, String playTime, int johtoBadges, int kantoBadges,
                       String trainerName, Link link, int cableClub) {
    /** Without the Cable Club step (unknown: -1). */
    public ScoreRow(String userId, int points, int badges, int caught, int seen, String game, boolean online,
                    int trainerScore, String title, String grade, int achievementPoints, int achievementsUnlocked,
                    List<Member> party, String location, String playTime, int johtoBadges, int kantoBadges,
                    String trainerName, Link link) {
        this(userId, points, badges, caught, seen, game, online, trainerScore, title, grade, achievementPoints,
                achievementsUnlocked, party, location, playTime, johtoBadges, kantoBadges, trainerName, link, -1);
    }

    public record Link(int wins, int losses, int trades, int partners) {
        public static final Link NONE = new Link(0, 0, 0, 0);

        public static Link of(TrainerLog log) {
            return log == null ? NONE : new Link(log.linkWins(), log.linkLosses(), log.trades(), log.partners().size());
        }
    }

    /** species: national Pokédex number; hpPercent 0-100 (above 0 while any HP is left); status as PartyMon's. */
    public record Member(int species, int level, int hpPercent, String status, boolean shiny, boolean egg) {
        public static Member of(PartyMon m) {
            int pct = m.maxHp() <= 0 ? 0 : Math.min(100, (m.hp() * 100 + m.maxHp() - 1) / m.maxHp());
            return new Member(m.species(), m.level(), pct, m.status(), m.shiny(), m.egg());
        }
    }
}
