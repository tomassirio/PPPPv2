package dev.pppp.model;

/**
 * Where a player stands in their game: map (the pret map constant, "NEW_BARK_TOWN"), x and y in steps of 16 pixels
 * from the map's top-left (wXCoord, wYCoord), and the way they face ("down", "up", "left", "right").
 */
public record MapSpot(String map, int x, int y, String facing) {}
