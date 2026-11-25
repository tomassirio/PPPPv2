package dev.pppp.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Mystery Gifts waiting to reach a player's PC: the Redis list "pibs:gifts:{userId}", oldest first, each entry
 * "{gift id}:{quantity}" (MysteryGifts.CATALOG).
 */
@Repository
public class GiftRepository {
    private final StringRedisTemplate redis;

    public GiftRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(String userId, String giftId, int quantity) {
        redis.opsForList().rightPush(key(userId), giftId + ":" + quantity);
        redis.opsForHash().increment(StatsRepository.STATS, "giftsSent", 1);
    }

    /** The oldest gift reached the player's game. */
    public void delivered(String userId) {
        pop(userId);
        redis.opsForHash().increment(StatsRepository.STATS, "giftsDelivered", 1);
    }

    /** Gifts still waiting for these players. */
    public long waiting(java.util.Collection<String> userIds) {
        return userIds.stream().mapToLong(id -> java.util.Objects.requireNonNullElse(redis.opsForList().size(key(id)), 0L)).sum();
    }

    /** The oldest waiting gift, or null. */
    public String peek(String userId) {
        return redis.opsForList().index(key(userId), 0);
    }

    public void pop(String userId) {
        redis.opsForList().leftPop(key(userId));
    }

    private static String key(String userId) {
        return "pibs:gifts:" + userId;
    }
}
