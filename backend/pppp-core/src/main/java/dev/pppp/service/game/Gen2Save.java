package dev.pppp.service.game;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * What {@link PokemonGen2} reads of a Gold/Silver/Crystal save in WRAM bank 1, for {@link Achievements} and
 * {@link TrainerScoring}. Boxes live in cartridge RAM and are not read: "owned" below means the party, or the
 * Pokédex's caught flags (which stay set after a release or trade).
 *
 * @param johtoBadges   wJohtoBadges bits; kantoBadges likewise
 * @param hallOfFame    wHallOfFameCount
 * @param redBeaten     Red is gone from Mt. Silver since the last Hall of Fame entry (see PokemonGen2)
 * @param caught        Pokédex caught flags by dex number (bit n = #n, 1..251); seen includes caught
 * @param partyCount    wPartyCount, eggs included; party holds the hatched members only
 * @param items         every item id held in the bag (items, balls, key items) or the PC
 * @param hms           how many of HM01-HM07 are held
 * @param unownForms    Unown forms registered in the Unown Dex
 * @param phoneContacts numbers registered in the Pokégear
 * @param playHours     in-game clock hours
 */
public record Gen2Save(int playerId, int johtoBadges, int kantoBadges, int hallOfFame, boolean redBeaten,
                       boolean gotStarter, boolean radioTowerCleared, boolean gotMasterBall, BitSet caught,
                       BitSet seen, int partyCount, List<Mon> party, Set<Integer> items, int hms, int coins,
                       int unownForms, int phoneContacts, int playHours) {

    /**
     * A hatched party member. ownCatch: its original trainer id is the player's. type1/type2 are pret type ids
     * from the ROM's base data (equal for a single type; -1 when the ROM could not be read).
     */
    public record Mon(int species, int level, boolean shiny, boolean ownCatch, int type1, int type2) {}

    /** Item ids, pret constants/item_constants.asm (same in pokegold and pokecrystal; checked against ItemNames in the ROMs). */
    static final int MASTER_BALL = 0x01, BICYCLE = 0x07, MOON_STONE = 0x08, FIRE_STONE = 0x16, THUNDERSTONE = 0x17,
            WATER_STONE = 0x18, LEAF_STONE = 0x22, COIN_CASE = 0x36, SECRETPOTION = 0x43,
            CLEAR_BELL = 0x46, // Crystal only; unused in Gold/Silver, so never held there
            SILVER_WING = 0x47, CARD_KEY = 0x7F, SUN_STONE = 0xA9, SQUIRTBOTTLE = 0xAF, RAINBOW_WING = 0xB2;
    static final int[] STONES = {FIRE_STONE, WATER_STONE, THUNDERSTONE, LEAF_STONE, MOON_STONE, SUN_STONE};

    /** Pokédex numbers (pret constants/pokemon_constants.asm; Gen 2 species ids are dex numbers). */
    static final int RATICATE = 20, FURRET = 162, GYARADOS = 130, MEWTWO = 150, MEW = 151, CELEBI = 251,
            CHIKORITA = 152, FERALIGATR = 160;

    public boolean owns(int dex) {
        return caught.get(dex);
    }
}
