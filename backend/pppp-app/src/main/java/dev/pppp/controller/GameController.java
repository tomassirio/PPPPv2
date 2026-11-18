package dev.pppp.controller;

import dev.pppp.exception.NotFoundException;
import dev.pppp.model.GameSummary;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.ProgressTracker;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in player's own game as read from their console's RAM: the status panel (party, badges, Pokédex). */
@RestController
@RequestMapping("/api/v1")
class GameController {
    private final ProgressTracker tracker;

    GameController(ProgressTracker tracker) { this.tracker = tracker; }

    /** 204 while the player has no game or their game has no profile. */
    @GetMapping("/game/state")
    ResponseEntity<GameSummary> state(Authentication auth) {
        GameSummary s = tracker.summary(CurrentUser.id(auth));
        return s == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(s);
    }

    /** The player character's overworld sprite sheet (16x96, see PokemonRed#playerSprite), drawn from the caller's ROM. */
    @GetMapping("/game/icons/player.png")
    ResponseEntity<byte[]> player(Authentication auth) {
        return png(tracker.playerSprite(CurrentUser.id(auth)), "No player sprite");
    }

    /**
     * A sprite of the caller's game (a party member, a Pokédex entry), drawn from their ROM; with of, from that Pib's
     * (a spectator draws each side's party from its own game).
     */
    @GetMapping("/game/icons/{id}.png")
    ResponseEntity<byte[]> icon(@PathVariable int id, @RequestParam(required = false) String of, Authentication auth) {
        return png(tracker.icon(of == null || of.isBlank() ? CurrentUser.id(auth) : of.toLowerCase(), id), "No icon " + id);
    }

    /**
     * Pokédex number n as the caller's own Pokédex shows it (unseen: the number only; seen: more; caught: all); with
     * of, as that Pib's Pokédex shows it (hovering their party on the scoreboard).
     */
    @GetMapping("/game/dex/{n}")
    dev.pppp.model.PokedexEntry dex(@PathVariable int n, @RequestParam(required = false) String of, Authentication auth) {
        var entry = tracker.dex(of == null || of.isBlank() ? CurrentUser.id(auth) : of.toLowerCase(), n);
        if (entry == null) throw new NotFoundException("No Pokédex entry " + n);
        return entry;
    }

    /** A badge of the caller's game by its index in the summary's badges, drawn from their ROM (404: no art there). */
    @GetMapping("/game/badges/{index}.png")
    ResponseEntity<byte[]> badge(@PathVariable int index, Authentication auth) {
        return png(tracker.badge(CurrentUser.id(auth), index), "No badge art " + index);
    }

    /** Private cache: the same URL is another picture for a player with another game. */
    private static ResponseEntity<byte[]> png(byte[] png, String missing) {
        if (png == null) throw new NotFoundException(missing);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate()).body(png);
    }
}
