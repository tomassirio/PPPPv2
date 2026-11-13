package dev.pppp.repository;

import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * The scoreboard's Redis keys: "pibs:progress:{userId}", a hash {badges, caught, seen ('|'-joined names), level,
 * hallOfFame} of everything already awarded; "pibs:scoreboard", a sorted set of points per user id (every player
 * who picked a game is a member, from 0 points); "pibs:events", the activity stream {user, type, detail, points,
 * at} capped at ~10,000 entries.
 */
@Repository
public class ProgressRepository {
    static final String SCOREBOARD = "pibs:scoreboard", EVENTS = "pibs:events";

    private final StringRedisTemplate redis;

    public ProgressRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** What the player was already awarded for; EMPTY when nothing is stored. */
    public Progress progress(String userId) {
        Map<Object, Object> h = redis.opsForHash().entries(key(userId));
        if (h.isEmpty()) return Progress.EMPTY;
        return new Progress(names(h.get("badges")), names(h.get("caught")), names(h.get("seen")),
                Integer.parseInt((String) h.getOrDefault("level", "0")), "true".equals(h.get("hallOfFame")));
    }

    /** Stores the progress, appends its events and adds their points, in one transaction. */
    public void award(String userId, Progress p, List<ScoreEvent> events) {
        Map<String, String> hash = Map.of("badges", String.join("|", p.badges()), "caught", String.join("|", p.caught()),
                "seen", String.join("|", p.seen()), "level", Integer.toString(p.level()),
                "hallOfFame", Boolean.toString(p.hallOfFame()));
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) {
                ops.multi();
                ops.opsForHash().putAll(key(userId), hash);
                for (ScoreEvent e : events) {
                    ops.opsForStream().add(StreamRecords.newRecord().in(EVENTS).ofMap(Map.of("user", e.user(),
                                    "type", e.type(), "detail", e.detail(), "points", Integer.toString(e.points()),
                                    "at", Long.toString(e.at()))),
                            XAddOptions.maxlen(10_000).approximateTrimming(true));
                }
                // Also with 0 points: it makes the player a member of the scoreboard.
                ops.opsForZSet().incrementScore(SCOREBOARD, userId, events.stream().mapToInt(ScoreEvent::points).sum());
                return ops.exec();
            }
        });
    }

    /** Points per user id, most first. */
    public Map<String, Integer> scores() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        var rows = redis.opsForZSet().reverseRangeWithScores(SCOREBOARD, 0, -1);
        if (rows != null) rows.forEach(t -> scores.put(t.getValue(), t.getScore().intValue()));
        return scores;
    }

    public List<ScoreEvent> newest(int limit) {
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(EVENTS, Range.unbounded(), Limit.limit().count(limit));
        return records == null ? List.of() : records.stream().map(r -> {
            Map<Object, Object> f = r.getValue();
            return new ScoreEvent((String) f.get("user"), (String) f.get("type"), (String) f.get("detail"),
                    Integer.parseInt((String) f.get("points")), Long.parseLong((String) f.get("at")));
        }).toList();
    }

    private static Set<String> names(Object joined) {
        return joined == null || joined.toString().isEmpty() ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(joined.toString().split("\\|")));
    }

    private static String key(String userId) {
        return "pibs:progress:" + userId;
    }
}
