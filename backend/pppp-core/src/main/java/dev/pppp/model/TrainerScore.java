package dev.pppp.model;

/**
 * A player's Trainer Score (0-1000) from the save state right now, its Trainer Title and letter grade, and the
 * seven parts it adds up from (Achievements & Trainer Score design, "Trainer Score formula"). Live: it goes down
 * when the save loses something.
 */
public record TrainerScore(int score, String title, String grade, int story, int pokedex, int collection, int team,
                           int keyItems, int side, int rarities) {
    public static final TrainerScore ZERO = new TrainerScore(0, "Youngster", "F", 0, 0, 0, 0, 0, 0, 0);
}
