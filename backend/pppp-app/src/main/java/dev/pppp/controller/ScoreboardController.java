package dev.pppp.controller;

import dev.pppp.model.PlayerAchievements;
import dev.pppp.model.PlayerProfile;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.ProgressTracker;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everyone's Trainer Score, achievements and progress, and the shared activity feed. Public among players by design. */
@RestController
@RequestMapping("/api/v1")
class ScoreboardController {
    private final ProgressTracker tracker;

    private final dev.pppp.repository.RankRepository ranks;

    ScoreboardController(ProgressTracker tracker, dev.pppp.repository.RankRepository ranks) {
        this.tracker = tracker;
        this.ranks = ranks;
    }

    /** Each player's rank when today began (UTC): the scoreboard's up / down arrows compare with it. */
    @GetMapping("/scoreboard/baseline")
    java.util.Map<String, Integer> baseline() {
        return ranks.today(tracker.scoreboard().stream().map(dev.pppp.model.ScoreRow::userId).toList());
    }

    /** Ranked: best Trainer Score first, then achievement points. */
    @GetMapping("/scoreboard")
    List<ScoreRow> scoreboard() {
        return tracker.scoreboard();
    }

    /** Newest first. */
    @GetMapping("/activity")
    List<ScoreEvent> activity(@RequestParam(defaultValue = "20") int limit) {
        return tracker.activity(limit);
    }

    /** The caller's Trainer Score and the achievement catalogue with their unlocks. */
    @GetMapping("/achievements")
    PlayerAchievements achievements(Authentication auth) {
        return tracker.achievements(CurrentUser.id(auth));
    }

    /** The same for another player on the scoreboard; 404 for anyone else. */
    @GetMapping("/players/{userId}/achievements")
    PlayerAchievements achievementsOf(@PathVariable String userId) {
        return tracker.achievementsOf(userId.toLowerCase(Locale.ROOT));
    }

    /** Where a Pib is on the Pokégear town map (the location's hover); 204 when off the map. */
    @GetMapping("/players/{userId}/pin")
    ResponseEntity<dev.pppp.model.MapPin> pin(@PathVariable String userId) {
        var pin = tracker.pin(userId.toLowerCase(Locale.ROOT));
        return pin == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(pin);
    }

    /** A Pib's page: scoreboard row, party, achievements with progress, stats, newest activity; 404 for anyone else. */
    @GetMapping("/players/{userId}/profile")
    PlayerProfile profile(@PathVariable String userId) {
        return tracker.playerProfile(userId.toLowerCase(Locale.ROOT));
    }

    /** A Pib's link history, newest first: battles (result, both teams), trades and Wonder Trades (the last 200). */
    @GetMapping("/players/{userId}/link-history")
    java.util.List<dev.pppp.model.LinkEvent> linkHistory(@PathVariable String userId) {
        return tracker.linkHistory(userId.toLowerCase(Locale.ROOT));
    }
}
