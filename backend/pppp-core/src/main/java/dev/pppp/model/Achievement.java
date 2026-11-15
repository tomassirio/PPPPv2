package dev.pppp.model;

/**
 * One entry of the achievement catalogue (Achievements & Trainer Score design). implemented is false when the
 * game's RAM cannot tell it yet; note then says why (else how it is detected). unlockedAt is the caller's unlock
 * time in epoch ms, null while locked (and in the bare catalogue).
 */
public record Achievement(String id, String category, String name, String description, int points,
                          boolean implemented, String note, Long unlockedAt) {
    public Achievement unlockedAt(Long at) {
        return new Achievement(id, category, name, description, points, implemented, note, at);
    }
}
