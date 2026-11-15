package dev.pppp.service.game;

import dev.pppp.model.GameSummary;
import dev.pppp.model.Progress;

/**
 * Everything game-specific about reading a game's RAM: which memory to read, how to parse it into a state, and
 * what that state means for the scoreboard and the party box. {@link ProgressTracker} does the polling for every
 * player. To support a new game, implement this and add it to {@link #forRom}.
 *
 * @param <S> the parsed game state
 */
public interface GameProfile<S> {

    /** The profile for this ROM, or null when the game has none (it then scores nothing and has no party box). */
    static GameProfile<?> forRom(byte[] rom) {
        if (PokemonRed.matches(rom)) return new PokemonRed(rom);
        return PokemonGen2.forRom(rom);
    }

    /** First address of the memory window read on every poll. */
    int ramStart();

    int ramLength();

    /** Parses the window; null while the game is not in play (boot, title screen, loading), so nothing counts. */
    S read(Ram ram);

    /** What the scoreboard counts in this state ({@link ProgressTracker}). */
    Progress progress(S state);

    /** The save as achievements and the Trainer Score read it; null for games without them (all but Gen 2). */
    default Gen2Save save(S state) {
        return null;
    }

    /** state is null before the game first comes into play: an empty summary (no party, nothing earned). */
    GameSummary summary(S state);

    /** PNG for a {@link GameSummary.Member#icon()} id, or null. Called from request threads. */
    default byte[] icon(int id) {
        return null;
    }

    /** PNG sprite sheet of the player walking around, or null. Called from request threads. */
    default byte[] playerSprite() {
        return null;
    }
}
