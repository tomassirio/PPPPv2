package dev.pppp.model;

import java.util.Set;

/**
 * What the achievements need remembered across readings, kept as JSON in pibs:history:{userId}. contacts: every
 * Pokégear contact id (pret constants/phone_constants.asm) ever seen in wPhoneList; hatched: eggs seen hatching in
 * the party; partners: user ids ever at the other end of the link cable; trades: Pokémon received in the Trade
 * Center / Time Capsule; crossVersion: one of them came from a Pib on another version; eliteFour: "clean" or
 * "fainted" while on the Elite Four / Lance / Hall of Fame maps, else null; linkWins / linkLosses: the save's
 * sLinkBattleWins / sLinkBattleLosses at its last read.
 */
public record TrainerLog(Set<Integer> contacts, int hatched, Set<String> partners, int trades, boolean crossVersion,
                         String eliteFour, int linkWins, int linkLosses) {
    public static final TrainerLog EMPTY = new TrainerLog(Set.of(), 0, Set.of(), 0, false, null, 0, 0);
}
