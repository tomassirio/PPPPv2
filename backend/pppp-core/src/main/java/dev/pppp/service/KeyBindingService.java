package dev.pppp.service;

import dev.pppp.model.KeyBindings;
import dev.pppp.repository.PlayerRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Each player's key bindings (Settings in the account menu). Kept across a game reset: they are not progress. */
@Service
public class KeyBindingService {
    /** They move focus and close dialogs in the browser. */
    private static final Set<String> RESERVED = Set.of("Escape", "Tab");

    private final PlayerRepository players;

    public KeyBindingService(PlayerRepository players) {
        this.players = players;
    }

    public KeyBindings get(String userId) {
        KeyBindings saved = players.keys(userId);
        return saved == null ? KeyBindings.DEFAULT : saved;
    }

    /** 400 (IllegalArgumentException) for a missing, reserved, over-long or repeated key. */
    public KeyBindings set(String userId, KeyBindings req) {
        if (req == null) throw new IllegalArgumentException("Key bindings are required");
        KeyBindings b = new KeyBindings(norm(req.up()), norm(req.down()), norm(req.left()), norm(req.right()),
                norm(req.a()), norm(req.b()), norm(req.start()), norm(req.select()));
        if (new HashSet<>(b.keys()).size() != b.keys().size()) {
            throw new IllegalArgumentException("Each key can press only one button");
        }
        players.saveKeys(userId, b);
        return b;
    }

    private static String norm(String key) {
        if (key == null || key.isEmpty() || key.length() > 20) throw new IllegalArgumentException("Every button needs a key");
        if (RESERVED.contains(key)) throw new IllegalArgumentException(key + " can't be bound");
        return key.length() == 1 ? key.toLowerCase(Locale.ROOT) : key;
    }
}
