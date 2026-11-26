package dev.pppp.repository;

import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * The Pibs' shared Pokédex (GroupDex): Redis hashes "pibs:dex:seen" and "pibs:dex:caught", species name ->
 * "{userId}|{epoch ms}" of the first player to see / catch it (0: before records were kept).
 */
@Repository
public class GroupDexRepository {
    public static final String SEEN = "pibs:dex:seen", CAUGHT = "pibs:dex:caught";

    private final StringRedisTemplate redis;

    public GroupDexRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** True when this player is the first (nobody had it yet). */
    public boolean claim(String key, String species, String userId, long at) {
        return Boolean.TRUE.equals(redis.opsForHash().putIfAbsent(key, species, userId + "|" + at));
    }

    /** species -> "{userId}|{epoch ms}". */
    public Map<String, String> all(String key) {
        Map<String, String> out = new HashMap<>();
        redis.<String, String>opsForHash().entries(key).forEach(out::put);
        return out;
    }
}
