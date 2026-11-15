package dev.pppp.model;

/**
 * A scoreboard row (GET /scoreboard, SCOREBOARD_CHANGED), ranked by trainerScore, then achievementPoints. points
 * is the older per-step tally (badges, Pokédex, levels; see ProgressTracker). game is the cartridge id;
 * online = console in memory.
 */
public record ScoreRow(String userId, int points, int badges, int caught, int seen, String game, boolean online,
                       int trainerScore, String title, String grade, int achievementPoints, int achievementsUnlocked) {}
