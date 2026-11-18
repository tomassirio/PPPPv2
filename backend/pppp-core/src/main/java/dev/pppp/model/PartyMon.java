package dev.pppp.model;

import java.util.List;

/**
 * A party member as the Pib profile shows it (Gen 2, read from RAM, names from the ROM). name: the species name
 * ("Feraligatr"; "Egg" for an egg, whose species is what will hatch); nickname: null when the player kept the
 * species name; status: null, "PSN", "PAR", "SLP", "BRN", "FRZ" or "FNT" (HP 0); item: the held item's name or
 * null; moves: the known moves' names; types: lower case ("water"); moveTypes: each move's type, lower case, in
 * the order of moves (null in data stored before it existed); tier: the species' Power Rating tier (PowerRating#tier),
 * always from the species, also for data stored before it existed.
 */
public record PartyMon(int species, String name, String nickname, int level, int hp, int maxHp, String status,
                       String item, List<String> moves, boolean shiny, boolean egg, List<String> types,
                       List<String> moveTypes, String tier) {
    public PartyMon {
        tier = egg ? null : dev.pppp.service.game.PowerRating.tier(species);
    }

    /** Without the tier (it follows from the species). */
    public PartyMon(int species, String name, String nickname, int level, int hp, int maxHp, String status,
                    String item, List<String> moves, boolean shiny, boolean egg, List<String> types,
                    List<String> moveTypes) {
        this(species, name, nickname, level, hp, maxHp, status, item, moves, shiny, egg, types, moveTypes, null);
    }

    /** Without the moves' types (tests, older callers). */
    public PartyMon(int species, String name, String nickname, int level, int hp, int maxHp, String status,
                    String item, List<String> moves, boolean shiny, boolean egg, List<String> types) {
        this(species, name, nickname, level, hp, maxHp, status, item, moves, shiny, egg, types, null, null);
    }
}
