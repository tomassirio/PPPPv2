package dev.pppp.model;

import java.util.List;

/** GET /achievements and /players/{userId}/achievements: the player's Trainer Score and the whole catalogue. */
public record PlayerAchievements(String userId, TrainerScore trainer, int points, int unlocked,
                                 List<Achievement> achievements) {}
