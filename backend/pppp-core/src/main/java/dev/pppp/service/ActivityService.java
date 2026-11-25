package dev.pppp.service;

import dev.pppp.repository.StatsRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Who played which day (daily active / returning players): each player at most one Redis write a day. */
@Service
public class ActivityService {
    private final StatsRepository stats;
    private final Map<String, LocalDate> marked = new ConcurrentHashMap<>();

    public ActivityService(PlayerConsoles consoles, StatsRepository stats) {
        this.stats = stats;
        consoles.onActive(this::played);
    }

    public void played(String userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (today.equals(marked.put(userId, today))) return;
        stats.active(userId, today);
    }
}
