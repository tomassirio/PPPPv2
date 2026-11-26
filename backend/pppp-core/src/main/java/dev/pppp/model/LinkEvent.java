package dev.pppp.model;

import java.util.List;

/**
 * One entry of a player's link history (GET /api/v1/players/{id}/link-history): a link battle (kind BATTLE, result
 * WIN or LOSS, both teams as last read), a link trade (TRADE: what left, what arrived) or a Wonder Trade (WONDER: sent,
 * received, or both). partner: the other Pib (null while a Wonder Trade waits in the pool).
 */
public record LinkEvent(String kind, long at, String partner, String result, List<Mon> mine, List<Mon> theirs, Mon gave,
                        Mon got) {
    /** species = Pokédex number. */
    public record Mon(int species, int level, boolean shiny) {}
}
