package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.LinkEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * A player's link history: the Redis list "pibs:linklog:{userId}" of LinkEvent JSON, newest first, the latest KEEP;
 * "pibs:linklog:filled:{userId}" marks that the battles from before the history were filled in (ProgressTracker).
 */
@Repository
public class LinkHistoryRepository {
    static final int KEEP = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;

    public LinkHistoryRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(String userId, LinkEvent event) {
        try {
            redis.opsForList().leftPush(key(userId), JSON.writeValueAsString(event));
            redis.opsForList().trim(key(userId), 0, KEEP - 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<LinkEvent> all(String userId) {
        List<String> raw = redis.opsForList().range(key(userId), 0, -1);
        List<LinkEvent> out = new ArrayList<>();
        for (String v : raw == null ? List.<String>of() : raw) {
            try {
                out.add(JSON.readValue(v, LinkEvent.class));
            } catch (JsonProcessingException ignored) {
                // a broken entry is skipped
            }
        }
        return out;
    }

    /** Adds older entries after the ones there (each newest first), once per player; false when already done. */
    public boolean fillOnce(String userId, List<LinkEvent> older) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("pibs:linklog:filled:" + userId, "1"))) return false;
        for (LinkEvent e : older) {
            try {
                redis.opsForList().rightPush(key(userId), JSON.writeValueAsString(e));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        redis.opsForList().trim(key(userId), 0, KEEP - 1);
        return true;
    }

    public void delete(String userId) {
        redis.delete(List.of(key(userId), "pibs:linklog:filled:" + userId));
    }

    private static String key(String userId) {
        return "pibs:linklog:" + userId;
    }
}
