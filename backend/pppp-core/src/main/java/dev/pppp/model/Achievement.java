package dev.pppp.model;

/**
 * One entry of the achievement catalogue (Achievements & Trainer Score design). rarity follows the points: 1-3
 * "common", 5 "uncommon", 10 "rare", 25 "epic", 50 "legendary". note says how it is detected. unlockedAt is the
 * player's unlock time in epoch ms, null while locked. A locked legendary is served hidden: name, description and
 * note null ({@link #seenBy}); implemented is always true (entries the RAM cannot tell are not in the catalogue).
 */
public record Achievement(String id, String category, String name, String description, int points, String rarity,
                          boolean hidden, boolean implemented, String note, Long unlockedAt) {
    public static Achievement of(String id, String category, String name, String description, int points, String note) {
        return new Achievement(id, category, name, description, points, rarity(points), false, true, note, null);
    }

    public static String rarity(int points) {
        return points >= 50 ? "legendary" : points >= 25 ? "epic" : points >= 10 ? "rare" : points >= 5 ? "uncommon"
                : "common";
    }

    /** As a browser may see it for a player who unlocked it at at (null: locked): a locked legendary is masked. */
    public Achievement seenBy(Long at) {
        boolean hide = at == null && "legendary".equals(rarity);
        return new Achievement(id, category, hide ? null : name, hide ? null : description, points, rarity, hide,
                implemented, hide ? null : note, at);
    }
}
