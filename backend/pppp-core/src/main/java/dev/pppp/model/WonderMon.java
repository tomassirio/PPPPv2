package dev.pppp.model;

/**
 * A Pokémon waiting in the Wonder Trade pool (WonderTrade): who put it in and when, what it shows (species = Pokédex
 * number, its name, nickname or null, OT name, level, shiny), and its bytes as the game keeps them, base64: the
 * party_struct (48), OT name and nickname (11 each).
 */
public record WonderMon(String id, String user, int species, String name, String nickname, String ot, int level,
                        boolean shiny, String mon, String otName, String nick, long at) {}
