package dev.pppp.repository;

import java.time.LocalDate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** The days each player played (DailyRewards): the Redis set "pibs:days:{userId}" of UTC dates (yyyy-mm-dd), kept for good. */
@Repository
public class DaysRepository {
    private final StringRedisTemplate redis;

    public DaysRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** False when the day was already there. */
    public boolean add(String userId, LocalDate day) {
        Long added = redis.opsForSet().add(key(userId), day.toString());
        return added != null && added > 0;
    }

    public boolean played(String userId, LocalDate day) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key(userId), day.toString()));
    }

    public int count(String userId) {
        Long n = redis.opsForSet().size(key(userId));
        return n == null ? 0 : n.intValue();
    }

    public void delete(String userId) {
        redis.delete(key(userId));
    }

    private static String key(String userId) {
        return "pibs:days:" + userId;
    }
}
