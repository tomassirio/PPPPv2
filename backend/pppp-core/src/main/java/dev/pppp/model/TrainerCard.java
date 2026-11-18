package dev.pppp.model;

import java.util.List;

/**
 * What the scoreboard and the Pib profile show of a player's last confirmed Gen 2 reading, kept in
 * pibs:trainer:{userId} so it survives restarts and shows while they are offline. location: "Landmark · Room"
 * inside ("Violet City · Pokémon Center"), the landmark outdoors, the room alone in the link rooms, or null;
 * playTime: "H:MM" or null; johtoBadges / kantoBadges: the games' bitmasks; trainerName: wPlayerName or null;
 * pin: where on the Pokégear town map, or null (off the map, or a card saved before pins existed); spot: where on
 * their map they stand, or null. cableClub: the story steps that open the Pokémon Centers' Cable Club (its
 * receptionists check EVENT_GAVE_MYSTERY_EGG_TO_ELM, maps/Pokecenter2F.asm): 0 none, 1 got a Pokémon from Elm,
 * 2 got the Mystery Egg from Mr. Pokémon, 3 gave it to Elm, open. Cards saved before it read 0.
 */
public record TrainerCard(List<PartyMon> party, String location, String playTime, int johtoBadges,
                          int kantoBadges, String trainerName, MapPin pin, MapSpot spot, int cableClub) {
    public static final TrainerCard EMPTY = new TrainerCard(List.of(), null, null, 0, 0, null, null, null, 0);
    public static final int CABLE_CLUB_OPEN = 3;
}
