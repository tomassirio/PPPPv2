package dev.pppp.model;

import java.util.List;

/**
 * What the scoreboard and the Pib profile show of a player's last confirmed Gen 2 reading, kept in
 * pibs:trainer:{userId} so it survives restarts and shows while they are offline. location: the landmark
 * ("Ecruteak City") or null; playTime: "H:MM" or null; johtoBadges / kantoBadges: the games' bitmasks.
 */
public record TrainerCard(List<PartyMon> party, String location, String playTime, int johtoBadges,
                          int kantoBadges) {
    public static final TrainerCard EMPTY = new TrainerCard(List.of(), null, null, 0, 0);
}
