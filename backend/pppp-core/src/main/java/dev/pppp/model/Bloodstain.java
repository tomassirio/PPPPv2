package dev.pppp.model;

/**
 * Where a player lost a battle (Bloodstains), as Dark Souls' bloodstains: map is pret's map constant, x, y the tile
 * they stood on when the battle began; foe: who beat them ("Leader Whitney's Miltank Lv 20", "a wild Gastly Lv 17").
 */
public record Bloodstain(String id, String user, String map, int x, int y, String foe, long at) {}
