package dev.pppp.model;

import java.util.List;

/**
 * One Pokédex number as the caller's own Pokédex shows it (GET /game/dex/{number}). state is "unseen", "seen" or
 * "caught". Unseen: the number only. Seen: name, category ("Seed") and types too. Caught: also height (feet and
 * inches), weight (pounds) and the entry text, as the game's Pokédex reveals them. tier: the species' Power Rating
 * tier (PowerRating#tier) once seen, null while unseen.
 */
public record PokedexEntry(int number, String state, String name, String category, List<String> types,
                           Integer heightFeet, Integer heightInches, Double weightPounds, String text, String tier) {
    public PokedexEntry {
        tier = "unseen".equals(state) ? null : dev.pppp.service.game.PowerRating.tier(number);
    }

    public PokedexEntry(int number, String state, String name, String category, List<String> types,
                        Integer heightFeet, Integer heightInches, Double weightPounds, String text) {
        this(number, state, name, category, types, heightFeet, heightInches, weightPounds, text, null);
    }
}
