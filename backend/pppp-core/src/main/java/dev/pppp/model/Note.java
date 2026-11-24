package dev.pppp.model;

/**
 * A note left on a tile of Johto (NoteService), as Dark Souls' messages: a phrase with one word filled in.
 * map: pret's map constant ("ROUTE_29"), so Gold, Silver and Crystal share them; x, y: the tile (wXCoord, wYCoord).
 */
public record Note(String id, String user, String map, int x, int y, String text, long at) {}
