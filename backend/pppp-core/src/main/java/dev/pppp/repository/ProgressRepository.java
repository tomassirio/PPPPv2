package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.TrainerCard;
import dev.pppp.model.TrainerLog;
import dev.pppp.model.TrainerScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * The scoreboard's Redis keys: "pibs:progress:{userId}", a hash {badges, caught, seen ('|'-joined names), level,
 * hallOfFame} of everything already awarded; "pibs:achievements:{userId}", a hash of achievement id -> unlock time
 * (epoch ms); "pibs:trainer:{userId}", a hash of the latest Trainer Score {score, title, grade, story, pokedex,
 * collection, team, keyItems, side, rarities} and card (JSON {@link TrainerCard}: party, location, playTime,
 * badge bitmasks, trainerName); "pibs:history:{userId}", a JSON {@link TrainerLog} (phone contacts and link partners
 * ever seen, eggs hatched, trades, the Elite Four run, link battle record); "pibs:scoreboard", the ranking: a sorted set of user ids by
 * trainer score x 10,000 + achievement points (every player who picked a game is a member, from 0);
 * "pibs:events", the activity stream {user, type, detail, points, at} capped at ~10,000 entries.
 */
@Repository
public class ProgressRepository {
    static final String SCOREBOARD = "pibs:scoreboard", EVENTS = "pibs:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public ProgressRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** What the player was already awarded for; EMPTY when nothing is stored. */
    public Progress progress(String userId) {
        Map<Object, Object> h = redis.opsForHash().entries(key(userId));
        if (h.isEmpty()) return Progress.EMPTY;
        return new Progress(names(h.get("badges")), names(h.get("caught")), names(h.get("seen")),
                Integer.parseInt((String) h.getOrDefault("level", "0")), "true".equals(h.get("hallOfFame")));
    }

    /** Stores the progress and appends its events, in one transaction. */
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
                append(ops, events);
                ops.opsForZSet().addIfAbsent(SCOREBOARD, userId, 0); // makes the player a member of the scoreboard
                return ops.exec();
            }
        });
    }

    /** Unlock time (epoch ms) per achievement id the player unlocked. */
    public Map<String, Long> achievements(String userId) {
        Map<String, Long> out = new HashMap<>();
        redis.opsForHash().entries(achievementsKey(userId)).forEach((id, at) -> out.put((String) id, Long.parseLong((String) at)));
        return out;
    }

    /** The card stored with the Trainer Score (JSON field "card"); EMPTY when none or unreadable. */
    public TrainerCard card(String userId) {
        Object v = redis.opsForHash().get(trainerKey(userId), "card");
        if (v == null) return TrainerCard.EMPTY;
        try {
            return json.readValue(v.toString(), TrainerCard.class);
        } catch (JsonProcessingException e) {
            return TrainerCard.EMPTY;
        }
    }

    /** Stores the card; drops the older "party" field ("155:12,16:5"), which the card replaces. */
    public void saveCard(String userId, TrainerCard card) {
        try {
            redis.opsForHash().put(trainerKey(userId), "card", json.writeValueAsString(card));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        redis.opsForHash().delete(trainerKey(userId), "party");
    }

    /** What the achievements remembered of the player; EMPTY when none or unreadable. */
    public TrainerLog log(String userId) {
        String v = redis.opsForValue().get(historyKey(userId));
        if (v == null) return TrainerLog.EMPTY;
        try {
            return json.readValue(v, TrainerLog.class);
        } catch (JsonProcessingException e) {
            return TrainerLog.EMPTY;
        }
    }

    public void saveLog(String userId, TrainerLog log) {
        try {
            redis.opsForValue().set(historyKey(userId), json.writeValueAsString(log));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The player's latest Trainer Score; ZERO when none is stored. */
    public TrainerScore trainer(String userId) {
        Map<Object, Object> h = redis.opsForHash().entries(trainerKey(userId));
        if (!h.containsKey("score")) return TrainerScore.ZERO; // the card can be there before a score
        return new TrainerScore(i(h, "score"), (String) h.get("title"), (String) h.get("grade"), i(h, "story"),
                i(h, "pokedex"), i(h, "collection"), i(h, "team"), i(h, "keyItems"), i(h, "side"), i(h, "rarities"));
    }

    /**
     * Stores the player's Trainer Score and newly unlocked achievements, sets their rank and appends the feed
     * entries, in one transaction.
     */
    public void rate(String userId, TrainerScore t, Map<String, Long> unlocked, long rank, List<ScoreEvent> events) {
        Map<String, String> trainer = new HashMap<>(Map.of("score", s(t.score()), "title", t.title(),
                "grade", t.grade(), "story", s(t.story()), "pokedex", s(t.pokedex()), "collection", s(t.collection()),
                "team", s(t.team()), "keyItems", s(t.keyItems()), "side", s(t.side())));
        trainer.put("rarities", s(t.rarities()));
        Map<String, String> fresh = new HashMap<>();
        unlocked.forEach((id, at) -> fresh.put(id, Long.toString(at)));
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) {
                ops.multi();
                ops.opsForHash().putAll(trainerKey(userId), trainer);
                if (!fresh.isEmpty()) ops.opsForHash().putAll(achievementsKey(userId), fresh);
                ops.opsForZSet().add(SCOREBOARD, userId, rank);
                append(ops, events);
                return ops.exec();
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void append(RedisOperations ops, List<ScoreEvent> events) {
        for (ScoreEvent e : events) {
            ops.opsForStream().add(StreamRecords.newRecord().in(EVENTS).ofMap(Map.of("user", e.user(),
                            "type", e.type(), "detail", e.detail(), "points", Integer.toString(e.points()),
                            "at", Long.toString(e.at()))),
                    XAddOptions.maxlen(10_000).approximateTrimming(true));
        }
    }

    /** Removes the player's progress, achievements, Trainer Score, history, rank and every activity entry of theirs. */
    public void delete(String userId) {
        redis.delete(List.of(key(userId), achievementsKey(userId), trainerKey(userId), historyKey(userId)));
        redis.opsForZSet().remove(SCOREBOARD, userId);
        List<MapRecord<String, Object, Object>> all = redis.opsForStream().range(EVENTS, Range.unbounded());
        if (all == null) return;
        // ponytail: scans the whole stream (capped at ~10,000); fine for a rare admin action.
        RecordId[] theirs = all.stream().filter(r -> userId.equals(r.getValue().get("user")))
                .map(MapRecord::getId).toArray(RecordId[]::new);
        if (theirs.length > 0) redis.opsForStream().delete(EVENTS, theirs);
    }

    /** Every player on the scoreboard, best rank first. */
    public List<String> ranking() {
        Set<String> ids = redis.opsForZSet().reverseRange(SCOREBOARD, 0, -1);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    public List<ScoreEvent> newest(int limit) {
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(EVENTS, Range.unbounded(), Limit.limit().count(limit));
        return records == null ? List.of() : records.stream().map(ProgressRepository::event).toList();
    }

    /** The player's newest activity entries. */
    public List<ScoreEvent> newest(String userId, int limit) {
        // ponytail: scans the whole stream (capped at ~10,000) per profile view; page through it if that shows.
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().reverseRange(EVENTS, Range.unbounded());
        return records == null ? List.of() : records.stream().filter(r -> userId.equals(r.getValue().get("user")))
                .limit(limit).map(ProgressRepository::event).toList();
    }

    /** Every activity entry of this type, newest first. */
    public List<ScoreEvent> newestOfType(String type) {
        // ponytail: scans the whole stream (capped at ~10,000), as newest(userId) does.
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().reverseRange(EVENTS, Range.unbounded());
        return records == null ? List.of() : records.stream().filter(r -> type.equals(r.getValue().get("type")))
                .map(ProgressRepository::event).toList();
    }

    private static ScoreEvent event(MapRecord<String, Object, Object> r) {
        Map<Object, Object> f = r.getValue();
        return new ScoreEvent((String) f.get("user"), (String) f.get("type"), (String) f.get("detail"),
                Integer.parseInt((String) f.get("points")), Long.parseLong((String) f.get("at")));
    }

    private static Set<String> names(Object joined) {
        return joined == null || joined.toString().isEmpty() ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(joined.toString().split("\\|")));
    }

    private static String key(String userId) {
        return "pibs:progress:" + userId;
    }

    private static String achievementsKey(String userId) {
        return "pibs:achievements:" + userId;
    }

    private static String trainerKey(String userId) {
        return "pibs:trainer:" + userId;
    }

    private static String historyKey(String userId) {
        return "pibs:history:" + userId;
    }

    private static String s(int n) {
        return Integer.toString(n);
    }

    private static int i(Map<Object, Object> h, String field) {
        return Integer.parseInt((String) h.getOrDefault(field, "0"));
    }
}
