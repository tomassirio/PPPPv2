package dev.pppp.repository;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Who is ready for a link-up: the Redis hash "pibs:lobby" (userId -> TRADE or BATTLE). */
@Repository
public class LobbyRepository {
    private static final String KEY = "pibs:lobby";

    private final StringRedisTemplate redis;

    public LobbyRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void put(String userId, String kind) {
        redis.opsForHash().put(KEY, userId, kind);
    }

    /** True when the player was in the lobby. */
    public boolean remove(String userId) {
        return redis.opsForHash().delete(KEY, userId) > 0;
    }

    /** The kind the player is ready for, or null. */
    public String kind(String userId) {
        Object kind = redis.opsForHash().get(KEY, userId);
        return kind == null ? null : kind.toString();
    }

    /** userId -> kind, sorted by user id. */
    public Map<String, String> all() {
        Map<String, String> all = new TreeMap<>();
        redis.opsForHash().entries(KEY).forEach((user, kind) -> all.put(user.toString(), kind.toString()));
        return all;
    }

    public void clear() {
        redis.delete(KEY);
    }
}
