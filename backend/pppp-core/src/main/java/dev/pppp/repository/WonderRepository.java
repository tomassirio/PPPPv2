package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.WonderMon;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * The Wonder Trade pool: the Redis list "pibs:wonder" (WonderMon as JSON, oldest first); the list
 * "pibs:wonder:incoming:{userId}" holds what was traded for theirs, until it reaches their party; the string
 * "pibs:wonder:day:{userId}" is the UTC date of their last Wonder Trade (one a day); the set
 * "pibs:wonder:given:{userId}" keys every Pokémon the player ever put in ("{OT ID}:{DVs}:{species}", hex), so one
 * taken back by a reset without saving cannot go in twice; the hash "pibs:wonder:trades" counts trades per player.
 */
@Repository
public class WonderRepository {
    private static final String POOL = "pibs:wonder", GIVEN = "pibs:wonder:given:", TRADES = "pibs:wonder:trades",
            INCOMING = "pibs:wonder:incoming:", DAY = "pibs:wonder:day:";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;

    public WonderRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<WonderMon> pool() {
        List<String> raw = redis.opsForList().range(POOL, 0, -1);
        List<WonderMon> out = new ArrayList<>();
        for (String v : raw == null ? List.<String>of() : raw) {
            try {
                out.add(JSON.readValue(v, WonderMon.class));
            } catch (JsonProcessingException ignored) {
                // a broken entry is skipped
            }
        }
        return out;
    }

    public void add(WonderMon mon) {
        redis.opsForList().rightPush(POOL, json(mon));
    }

    /** False when someone else took it meanwhile. */
    public boolean take(WonderMon mon) {
        Long n = redis.opsForList().remove(POOL, 1, json(mon));
        return n != null && n > 0;
    }

    public boolean given(String userId, String key) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(GIVEN + userId, key));
    }

    public void give(String userId, String key) {
        redis.opsForSet().add(GIVEN + userId, key);
        redis.opsForHash().increment(TRADES, userId, 1);
    }

    /** What was traded for one of theirs, on its way to their party. */
    public void deliver(String userId, WonderMon mon) {
        redis.opsForList().rightPush(INCOMING + userId, json(mon));
    }

    public WonderMon nextIncoming(String userId) {
        String v = redis.opsForList().index(INCOMING + userId, 0);
        try {
            return v == null ? null : JSON.readValue(v, WonderMon.class);
        } catch (JsonProcessingException e) {
            redis.opsForList().leftPop(INCOMING + userId); // broken: dropped
            return null;
        }
    }

    public void delivered(String userId) {
        redis.opsForList().leftPop(INCOMING + userId);
    }

    public long incoming(String userId) {
        Long n = redis.opsForList().size(INCOMING + userId);
        return n == null ? 0 : n;
    }

    /** The UTC date (yyyy-mm-dd) of their last Wonder Trade, or null. */
    public String day(String userId) {
        return redis.opsForValue().get(DAY + userId);
    }

    public void traded(String userId, String day) {
        redis.opsForValue().set(DAY + userId, day);
    }

    /** A reset run: its Pokémon are new ones, and nothing is on its way to it any more. */
    public void forget(String userId) {
        redis.delete(List.of(GIVEN + userId, INCOMING + userId, DAY + userId));
        for (WonderMon m : pool()) if (m.user().equals(userId)) take(m);
    }

    private static String json(WonderMon mon) {
        try {
            return JSON.writeValueAsString(mon);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
