package dev.pppp.repository;

import dev.pppp.model.KeyBindings;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Per-player facts in the Redis hash "pibs:player:{userId}" {game, sprite (the trainer they walk as, PlayerSprites)}. Picking also enters the player on the scoreboard.
 * Key bindings live apart in "pibs:keys:{userId}" {up, down, left, right, a, b, start, select}, so a reset keeps them.
 */
@Repository
public class PlayerRepository {
    private final StringRedisTemplate redis;

    public PlayerRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Write-once: false when the player already has a game (which is left as it was). */
    public boolean pickGame(String userId, String cartridgeId) {
        redis.opsForZSet().addIfAbsent(ProgressRepository.SCOREBOARD, userId, 0);
        return redis.opsForHash().putIfAbsent(key(userId), "game", cartridgeId);
    }

    /** Forgets the pick, so the player chooses again. */
    public void delete(String userId) {
        redis.delete(key(userId));
    }

    /** The picked cartridge id, or null. */
    public String game(String userId) {
        Object game = redis.opsForHash().get(key(userId), "game");
        return game == null ? null : game.toString();
    }

    /** The trainer sprite key the player walks as, or null for the game's own. */
    public String sprite(String userId) {
        Object sprite = redis.opsForHash().get(key(userId), "sprite");
        return sprite == null ? null : sprite.toString();
    }

    public void saveSprite(String userId, String key) {
        redis.opsForHash().put(key(userId), "sprite", key);
    }

    /** The saved bindings, or null when the player never changed them. */
    public KeyBindings keys(String userId) {
        List<Object> v = redis.opsForHash().multiGet(keysKey(userId), List.of(FIELDS));
        if (v.contains(null)) return null;
        return new KeyBindings((String) v.get(0), (String) v.get(1), (String) v.get(2), (String) v.get(3),
                (String) v.get(4), (String) v.get(5), (String) v.get(6), (String) v.get(7));
    }

    public void saveKeys(String userId, KeyBindings b) {
        List<String> k = b.keys();
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (int i = 0; i < FIELDS.length; i++) fields.put((String) FIELDS[i], k.get(i));
        redis.opsForHash().putAll(keysKey(userId), fields);
    }

    private static final Object[] FIELDS = {"up", "down", "left", "right", "a", "b", "start", "select"};

    private static String keysKey(String userId) {
        return "pibs:keys:" + userId;
    }

    private static String key(String userId) {
        return "pibs:player:" + userId;
    }
}
