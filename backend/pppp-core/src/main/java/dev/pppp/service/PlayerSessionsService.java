package dev.pppp.service;

import dev.pppp.exception.NotFoundException;
import dev.pppp.repository.CartridgeRepository;
import dev.pppp.repository.PlayerRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * A player's Game Boy for the admin player page: whether a console is in memory (RUNNING, IDLE: parked and about to
 * shut down, PAUSED: could not be parked, or CLOSED), when they last did something and when it will be shut down, and
 * their two files on disk: the game's own save (.sav, written when they save in the game) and the parked state (.state, where they resume).
 */
@Service
public class PlayerSessionsService {
    /** A file on disk: its size in bytes and when it was last written (epoch ms). */
    public record FileInfo(long size, long modified) {}

    public record PlayerSession(String userId, String cartridgeId, String state, String linkedWith, Long lastActive,
                                Long closesAt, FileInfo save, FileInfo snapshot) {}

    private final PlayerConsoles consoles;
    private final PlayerRepository players;
    private final CartridgeRepository files;
    private final ChatService chat;

    public PlayerSessionsService(PlayerConsoles consoles, PlayerRepository players, CartridgeRepository files,
                                 ChatService chat) {
        this.consoles = consoles;
        this.players = players;
        this.files = files;
        this.chat = chat;
    }

    /** One player's Game Boy (CLOSED when no console is in memory). */
    public PlayerSession of(String id) {
        PlayerConsoles.Session s = consoles.sessions().stream().filter(x -> x.userId().equals(id)).findFirst().orElse(null);
        String game = s != null ? s.cartridgeId() : players.game(id);
        return new PlayerSession(id, game, s != null ? s.state() : "CLOSED", s == null ? null : s.linkedWith(),
                s == null ? null : s.lastActive(), s == null ? null : s.closesAt(),
                game == null ? null : info(files.playerBatterySave(id, game)),
                game == null ? null : info(files.playerState(id, game)));
    }

    /**
     * Parks the player's console and shuts it down now. NotFoundException when none is in memory; IllegalStateException
     * when it stays (its state could not be parked, or they came back meanwhile).
     */
    public void close(String adminId, String userId) {
        if (!consoles.close(userId)) {
            if (!consoles.running().contains(userId)) throw new NotFoundException("No console running for " + userId);
            throw new IllegalStateException("Could not close it: its state could not be saved, or the player just came back");
        }
        chat.postAdmin(adminId + " closed the Game Boy of " + userId);
    }

    /** The player's in-game save file. NotFoundException without a game or before their first save. */
    public Path save(String userId) {
        String game = players.game(userId);
        Path p = game == null ? null : files.playerBatterySave(userId, game);
        if (p == null || !Files.isRegularFile(p)) throw new NotFoundException("No save for " + userId);
        return p;
    }

    private static FileInfo info(Path p) {
        try {
            return Files.isRegularFile(p) ? new FileInfo(Files.size(p), Files.getLastModifiedTime(p).toMillis()) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
