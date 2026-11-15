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
 * lobby and challenges cleared through the idle listeners), and so do their pick, saves, progress, points and
 * activity entries. Their account, role and chat messages stay.
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

    public PlayerResetService(PlayerConsoles consoles, PlayerRepository players, CartridgeRepository files,
                              ProgressRepository progress, ProgressTracker tracker, ChatService chat,
                              ApplicationEventPublisher events) {
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
        if (players.game(userId) == null && !progress.scores().containsKey(userId)) {
            throw new NotFoundException("No run to reset for " + userId);
        }
        // ponytail: a progress poll already in flight can still write one award after this; reset again if seen.
        consoles.discard(userId);
        players.delete(userId);
        progress.delete(userId);
        files.deletePlayerFiles(userId);
        tracker.forget(userId);
        events.publishEvent(new LiveEvent("PLAYER_RESET", Map.of("user", userId)));
        chat.postAdmin(adminId + " reset the game of " + userId);
    }
}
