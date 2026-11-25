package dev.pppp.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Totals and activity kept for the metrics. Redis hash "pibs:stats" (field -> running total: giftsSent,
 * giftsDelivered, accessAccepted, accessWaitSeconds, accessRejected); sets "pibs:active:{yyyy-mm-dd}" (UTC) of the
 * players who played that day, kept 40 days.
 */
@Repository
public class StatsRepository {
    static final String STATS = "pibs:stats";
    private static final String ACTIVE = "pibs:active:";
    private static final java.time.Duration KEEP_ACTIVE = java.time.Duration.ofDays(40);

    private final StringRedisTemplate redis;

    public StatsRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(String field, long by) {
        redis.opsForHash().increment(STATS, field, by);
    }

    public Map<String, Long> totals() {
        Map<String, Long> out = new HashMap<>();
        redis.<String, String>opsForHash().entries(STATS).forEach((k, v) -> out.put(k, Long.parseLong(v)));
        return out;
    }

    public void active(String userId, java.time.LocalDate day) {
        String key = ACTIVE + day;
        redis.opsForSet().add(key, userId);
        redis.expire(key, KEEP_ACTIVE);
    }

    /** Everyone who played on any of these days. */
    public Set<String> active(List<java.time.LocalDate> days) {
        return redis.opsForSet().union(days.stream().map(d -> ACTIVE + d).toList());
    }
}
