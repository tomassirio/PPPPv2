package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.Note;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Notes in the Redis hash "pibs:notes" (id -> Note as JSON); who rated one Good in the set "pibs:notes:good:{id}".
 */
@Repository
public class NoteRepository {
    private static final String NOTES = "pibs:notes", GOOD = "pibs:notes:good:";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;

    public NoteRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Note> all() {
        List<Note> out = new ArrayList<>();
        for (Object v : redis.opsForHash().values(NOTES)) {
            try {
                out.add(JSON.readValue(v.toString(), Note.class));
            } catch (JsonProcessingException ignored) {
                // a broken entry is skipped
            }
        }
        return out;
    }

    public void save(Note note) {
        try {
            redis.opsForHash().put(NOTES, note.id(), JSON.writeValueAsString(note));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void delete(String id) {
        redis.opsForHash().delete(NOTES, id);
        redis.delete(GOOD + id);
    }

    /** False when this player had rated it already. */
    public boolean good(String id, String userId) {
        Long added = redis.opsForSet().add(GOOD + id, userId);
        return added != null && added > 0;
    }

    public java.util.Set<String> raters(String id) {
        java.util.Set<String> s = redis.opsForSet().members(GOOD + id);
        return s == null ? java.util.Set.of() : s;
    }
}
