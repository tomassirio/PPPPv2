package dev.pppp.model;

/** A scoreboard row (GET /scoreboard, SCOREBOARD_CHANGED). game is the cartridge id; online = console in memory. */
public record ScoreRow(String userId, int points, int badges, int caught, int seen, String game, boolean online) {}
