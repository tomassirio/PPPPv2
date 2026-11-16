package dev.pppp.model;

import java.util.List;

/**
 * What the scoreboard and the Pib profile show of a player's last confirmed Gen 2 reading, kept in
 * pibs:trainer:{userId} so it survives restarts and shows while they are offline. location: "Landmark · Room"
 * inside ("Violet City · Pokémon Center"), the landmark outdoors, the room alone in the link rooms, or null;
 * playTime: "H:MM" or null; johtoBadges / kantoBadges: the games' bitmasks; trainerName: wPlayerName or null;
 * pin: where on the Pokégear town map, or null (off the map, or a card saved before pins existed).
 */
public record TrainerCard(List<PartyMon> party, String location, String playTime, int johtoBadges,
                          int kantoBadges, String trainerName, MapPin pin) {
    public static final TrainerCard EMPTY = new TrainerCard(List.of(), null, null, 0, 0, null, null);
}
