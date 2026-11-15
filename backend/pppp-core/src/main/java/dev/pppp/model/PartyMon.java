package dev.pppp.model;

import java.util.List;

/**
 * A party member as the Pib profile shows it (Gen 2, read from RAM, names from the ROM). name: the species name
 * ("Feraligatr"; "Egg" for an egg, whose species is what will hatch); nickname: null when the player kept the
 * species name; status: null, "PSN", "PAR", "SLP", "BRN", "FRZ" or "FNT" (HP 0); item: the held item's name or
 * null; moves: the known moves' names; types: lower case ("water").
 */
public record PartyMon(int species, String name, String nickname, int level, int hp, int maxHp, String status,
                       String item, List<String> moves, boolean shiny, boolean egg, List<String> types) {}
