package dev.pppp.model;

/**
 * Where a player is on the Pokégear town map: region "johto" or "kanto", and x, y in that map's 160x144 screen
 * pixels (the Landmarks table's sprite position less the hardware's 8, 16 offset).
 */
public record MapPin(String region, int x, int y) {}
