package dev.pppp.model;

/**
 * One scored step of a player's progress: an entry of the pibs:events stream and the PROGRESS live event.
 * type is BADGE, CAUGHT, SEEN, LEVEL or HALL_OF_FAME (detail: the badge or species name, or the level reached),
 * ACHIEVEMENT (detail: its name; points: its points) or TITLE (detail: the new Trainer Title and grade,
 * "Gym Leader (C)"; points 0).
 */
public record ScoreEvent(String user, String type, String detail, int points, long at) {}
