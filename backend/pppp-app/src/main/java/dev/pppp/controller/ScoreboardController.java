package dev.pppp.controller;

import dev.pppp.model.PlayerAchievements;
import dev.pppp.model.PlayerProfile;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.ProgressTracker;
import java.util.List;
import java.util.Locale;
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

    ScoreboardController(ProgressTracker tracker) { this.tracker = tracker; }

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

    /** A Pib's page: scoreboard row, party, achievements with progress, stats, newest activity; 404 for anyone else. */
    @GetMapping("/players/{userId}/profile")
    PlayerProfile profile(@PathVariable String userId) {
        return tracker.playerProfile(userId.toLowerCase(Locale.ROOT));
    }
}
