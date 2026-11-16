package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.model.ScoreRow;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.game.ProgressTracker;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous teaser for the sign-in page: totals about the group, never anything about who (no names, no single
 * player's numbers).
 */
@RestController
@RequestMapping("/api/v1/public")
class PublicController {
    private final EventPublisher events;
    private final ProgressTracker tracker;
    private final CartridgeService cartridges;

    PublicController(EventPublisher events, ProgressTracker tracker, CartridgeService cartridges) {
        this.events = events;
        this.tracker = tracker;
        this.cartridges = cartridges;
    }

    /**
     * players: everyone who picked a game; versions: how many play each, by ROM header title ("POKEMON_GLD");
     * caughtTogether: species caught by at least one Pib; achievements: unlocked, all Pibs together; trades: link
     * trades (each one is a Pokémon received on both ends, so half the received); battles: link battles won.
     */
    record Teaser(int online, int players, Map<String, Integer> versions, int caughtTogether, int achievements,
                  int trades, int battles) {}

    @GetMapping("/teaser")
    Teaser teaser() {
        var rows = tracker.scoreboard();
        Map<String, String> titles = cartridges.list().stream()
                .collect(Collectors.toMap(Cartridge::id, Cartridge::title, (a, b) -> a));
        Map<String, Integer> versions = new TreeMap<>();
        rows.forEach(r -> versions.merge(titles.getOrDefault(r.game(), "OTHER"), 1, Integer::sum));
        return new Teaser(events.viewers().size(), rows.size(), versions, tracker.caughtTogether(),
                rows.stream().mapToInt(ScoreRow::achievementsUnlocked).sum(),
                rows.stream().mapToInt(r -> r.link().trades()).sum() / 2,
                rows.stream().mapToInt(r -> r.link().wins()).sum());
    }
}
