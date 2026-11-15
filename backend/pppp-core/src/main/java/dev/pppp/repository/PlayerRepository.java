package dev.pppp.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Per-player facts in the Redis hash "pibs:player:{userId}" {game}. Picking also enters the player on the scoreboard. */
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

    private static String key(String userId) {
        return "pibs:player:" + userId;
    }
}
