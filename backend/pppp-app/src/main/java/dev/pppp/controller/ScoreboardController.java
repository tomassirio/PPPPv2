package dev.pppp.controller;

import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.service.game.ProgressTracker;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everyone's points and progress, and the shared activity feed. Public among players by design. */
@RestController
@RequestMapping("/api/v1")
class ScoreboardController {
    private final ProgressTracker tracker;

    ScoreboardController(ProgressTracker tracker) { this.tracker = tracker; }

    /** Ranked, most points first. */
    @GetMapping("/scoreboard")
    List<ScoreRow> scoreboard() {
        return tracker.scoreboard();
    }

    /** Newest first. */
    @GetMapping("/activity")
    List<ScoreEvent> activity(@RequestParam(defaultValue = "20") int limit) {
        return tracker.activity(limit);
    }
}
