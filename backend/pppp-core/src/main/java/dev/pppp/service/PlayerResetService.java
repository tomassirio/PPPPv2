package dev.pppp.service;

import dev.pppp.exception.NotFoundException;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.CartridgeRepository;
import dev.pppp.repository.PlayerRepository;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.game.ProgressTracker;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Admin action: wipes one player's run so they pick a game again. Their console goes away (link cable pulled,
 * lobby and challenges cleared through the idle listeners), and so do their pick, saves, progress, achievements,
 * Trainer Score, rank and activity entries (ProgressRepository.delete), days played and gifts waiting (the daily
 * rewards start again at day 1) and their Wonder Trade record. Their account, role and chat messages stay.
 */
@Service
public class PlayerResetService {
    private final PlayerConsoles consoles;
    private final PlayerRepository players;
    private final CartridgeRepository files;
    private final ProgressRepository progress;
    private final ProgressTracker tracker;
    private final ChatService chat;
    private final ApplicationEventPublisher events;
    private final dev.pppp.repository.DaysRepository days;
    private final dev.pppp.repository.GiftRepository gifts;
    private final dev.pppp.repository.WonderRepository wonder;
    private final dev.pppp.repository.LinkHistoryRepository history;

    public PlayerResetService(PlayerConsoles consoles, PlayerRepository players, CartridgeRepository files,
                              ProgressRepository progress, ProgressTracker tracker, ChatService chat,
                              ApplicationEventPublisher events, dev.pppp.repository.DaysRepository days,
                              dev.pppp.repository.GiftRepository gifts, dev.pppp.repository.WonderRepository wonder,
                              dev.pppp.repository.LinkHistoryRepository history) {
        this.history = history;
        this.days = days;
        this.gifts = gifts;
        this.wonder = wonder;
        this.consoles = consoles;
        this.players = players;
        this.files = files;
        this.progress = progress;
        this.tracker = tracker;
        this.chat = chat;
        this.events = events;
    }

    /** NotFoundException when the player never picked a game and has nothing on the scoreboard. */
    public void reset(String adminId, String userId) {
        if (players.game(userId) == null && !progress.ranking().contains(userId)) {
            throw new NotFoundException("No run to reset for " + userId);
        }
        // ponytail: a progress poll already in flight can still write one award after this; reset again if seen.
        players.delete(userId); // first: a screen reconnecting meanwhile gets "pick a game", not the old run back
        consoles.discard(userId);
        progress.delete(userId);
        files.deletePlayerFiles(userId);
        days.delete(userId);
        gifts.clear(userId);
        wonder.forget(userId);
        history.delete(userId);
        tracker.forget(userId);
        events.publishEvent(new LiveEvent("PLAYER_RESET", Map.of("user", userId)));
        chat.postAdmin(adminId + " reset the game of " + userId);
    }
}
