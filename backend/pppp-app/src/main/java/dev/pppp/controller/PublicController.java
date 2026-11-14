package dev.pppp.controller;

import dev.pppp.realtime.EventPublisher;
import dev.pppp.service.game.ProgressTracker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous teaser for the sign-in page: how many players there are and how many are online. Nothing about who. */
@RestController
@RequestMapping("/api/v1/public")
class PublicController {
    private final EventPublisher events;
    private final ProgressTracker tracker;

    PublicController(EventPublisher events, ProgressTracker tracker) {
        this.events = events;
        this.tracker = tracker;
    }

    /** players: everyone who picked a game. */
    record Teaser(int online, int players) {}

    @GetMapping("/teaser")
    Teaser teaser() {
        return new Teaser(events.viewers().size(), tracker.scoreboard().size());
    }
}
