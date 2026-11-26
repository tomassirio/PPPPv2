package dev.pppp.repository;

import dev.pppp.model.KeyBindings;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Per-player facts in the Redis hash "pibs:player:{userId}" {game, sprite (the trainer they walk as, PlayerSprites), spriteColor (null: its own),
 * aaaaMail, cableClub, call:{id} (AAAA's phone calls: "pending" or when it rang)}. Picking also enters the player on the scoreboard.
 * Key bindings live apart in "pibs:keys:{userId}" {up, down, left, right, a, b, start, select, fast ("" = none)}, so a reset keeps them.
 */
@Repository
public class PlayerRepository {
    private final StringRedisTemplate redis;

    public PlayerRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Write-once: false when the player already has a game (which is left as it was). */
    public boolean pickGame(String userId, String cartridgeId) {
        redis.opsForZSet().addIfAbsent(ProgressRepository.SCOREBOARD, userId, 0);
        return redis.opsForHash().putIfAbsent(key(userId), "game", cartridgeId);
    }

    /** Forgets the pick, so the player chooses again. */
    public void delete(String userId) {
        redis.delete(key(userId));
    }

    /** The picked cartridge id, or null. */
    public String game(String userId) {
        Object game = redis.opsForHash().get(key(userId), "game");
        return game == null ? null : game.toString();
    }

    /** Whether AAAA's Mail has reached this player's PC mailbox (AaaaMail); a reset forgets it with the rest. */
    public boolean aaaaMail(String userId) {
        return redis.opsForHash().hasKey(key(userId), "aaaaMail");
    }

    public void markAaaaMail(String userId) {
        redis.opsForHash().put(key(userId), "aaaaMail", String.valueOf(System.currentTimeMillis()));
    }

    /** AAAA's phone calls (AaaaCalls): call id -> "pending" or the epoch ms it rang; fields "call:{id}". */
    public Map<String, String> aaaaCalls(String userId) {
        Map<String, String> calls = new java.util.HashMap<>();
        redis.<String, String>opsForHash().entries(key(userId)).forEach((k, v) -> {
            if (k.startsWith("call:")) calls.put(k.substring(5), v);
        });
        return calls;
    }

    /** Write-once: false when this call was queued (or rang) before. */
    public boolean queueAaaaCall(String userId, String call) {
        return redis.opsForHash().putIfAbsent(key(userId), "call:" + call, "pending");
    }

    public void madeAaaaCall(String userId, String call) {
        redis.opsForHash().put(key(userId), "call:" + call, String.valueOf(System.currentTimeMillis()));
    }

    /** Dev only: forgets a call, so its moment rings it again. */
    public void forgetAaaaCall(String userId, String call) {
        redis.opsForHash().delete(key(userId), "call:" + call);
    }

    /** The trainer sprite key the player walks as, or null for the game's own. */
    public String sprite(String userId) {
        Object sprite = redis.opsForHash().get(key(userId), "sprite");
        return sprite == null ? null : sprite.toString();
    }

    public void saveSprite(String userId, String key) {
        redis.opsForHash().put(key(userId), "sprite", key);
    }

    /** An admin's Cable Club setting: "OPEN" or "LOCKED", or null to follow their game. */
    public String cableClub(String userId) {
        Object c = redis.opsForHash().get(key(userId), "cableClub");
        return c == null ? null : c.toString();
    }

    public void saveCableClub(String userId, String mode) {
        if (mode == null) redis.opsForHash().delete(key(userId), "cableClub");
        else redis.opsForHash().put(key(userId), "cableClub", mode);
    }

    /** The colour they walk in (a PlayerSprites.COLORS name), or null for their trainer's own. */
    public String spriteColor(String userId) {
        Object c = redis.opsForHash().get(key(userId), "spriteColor");
        return c == null ? null : c.toString();
    }

    public void saveSpriteColor(String userId, String color) {
        if (color == null) redis.opsForHash().delete(key(userId), "spriteColor");
        else redis.opsForHash().put(key(userId), "spriteColor", color);
    }

    /** The saved bindings, or null when the player never changed them. */
    public KeyBindings keys(String userId) {
        List<Object> v = redis.opsForHash().multiGet(keysKey(userId), List.of(FIELDS));
        if (v.subList(0, 8).contains(null)) return null;
        // Saved before Fast had a key: Space, unless a button already has it; "" saved means none.
        Object fast = v.get(8);
        String f = fast == null ? (v.contains(" ") ? null : " ") : fast.toString().isEmpty() ? null : fast.toString();
        return new KeyBindings((String) v.get(0), (String) v.get(1), (String) v.get(2), (String) v.get(3),
                (String) v.get(4), (String) v.get(5), (String) v.get(6), (String) v.get(7), f);
    }

    public void saveKeys(String userId, KeyBindings b) {
        List<String> k = List.of(b.up(), b.down(), b.left(), b.right(), b.a(), b.b(), b.start(), b.select(),
                b.fast() == null ? "" : b.fast());
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (int i = 0; i < FIELDS.length; i++) fields.put((String) FIELDS[i], k.get(i));
        redis.opsForHash().putAll(keysKey(userId), fields);
    }

    private static final Object[] FIELDS = {"up", "down", "left", "right", "a", "b", "start", "select", "fast"};

    private static String keysKey(String userId) {
        return "pibs:keys:" + userId;
    }

    private static String key(String userId) {
        return "pibs:player:" + userId;
    }
}
