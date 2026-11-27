package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.Bloodstain;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Bloodstains in the Redis hash "pibs:bloodstains" (id -> Bloodstain as JSON). */
@Repository
public class BloodstainRepository {
    private static final String KEY = "pibs:bloodstains", REACTIONS = "pibs:bloodstains:reactions:";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;

    public BloodstainRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Bloodstain> all() {
        List<Bloodstain> out = new ArrayList<>();
        for (Object v : redis.opsForHash().values(KEY)) {
            try {
                out.add(JSON.readValue(v.toString(), Bloodstain.class));
            } catch (JsonProcessingException ignored) {
                // a broken entry is skipped
            }
        }
        return out;
    }

    public void save(Bloodstain stain) {
        try {
            redis.opsForHash().put(KEY, stain.id(), JSON.writeValueAsString(stain));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void delete(String id) {
        redis.opsForHash().delete(KEY, id);
        redis.delete(REACTIONS + id);
    }

    /** Who reacted to a stain and with what: "pibs:bloodstains:reactions:{stainId}", user -> reaction. */
    public java.util.Map<String, String> reactions(String id) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        redis.opsForHash().entries(REACTIONS + id).forEach((k, v) -> out.put(k.toString(), v.toString()));
        return out;
    }

    /** Sets the user's reaction to a stain, or with null takes it back. */
    public void react(String id, String userId, String reaction) {
        if (reaction == null) redis.opsForHash().delete(REACTIONS + id, userId);
        else redis.opsForHash().put(REACTIONS + id, userId, reaction);
    }
}
