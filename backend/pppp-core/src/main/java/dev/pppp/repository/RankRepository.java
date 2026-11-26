package dev.pppp.repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Where each player stood when today began (UTC), for the scoreboard's up / down arrows: the Redis hash
 * "pibs:ranks:{yyyy-mm-dd}" (userId -> rank, 1 first), kept 3 days. A player gets today's entry the first time the
 * standings are read today (one who starts playing later: their rank then).
 */
@Repository
public class RankRepository {
    private static final Duration KEEP = Duration.ofDays(3);

    private final StringRedisTemplate redis;

    public RankRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Today's starting ranks, after filling in anyone in ranking (best first) not in them yet. */
    public Map<String, Integer> today(List<String> ranking) {
        String key = "pibs:ranks:" + LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < ranking.size(); i++) redis.opsForHash().putIfAbsent(key, ranking.get(i), Integer.toString(i + 1));
        redis.expire(key, KEEP);
        Map<String, Integer> out = new HashMap<>();
        redis.<String, String>opsForHash().entries(key).forEach((u, r) -> out.put(u, Integer.parseInt(r)));
        return out;
    }
}
