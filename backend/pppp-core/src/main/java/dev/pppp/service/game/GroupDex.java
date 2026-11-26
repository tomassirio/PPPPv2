package dev.pppp.service.game;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.repository.GroupDexRepository;
import dev.pppp.repository.ProgressRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * The Pibs' shared Pokédex: every species anyone has seen or caught, with the first player to do it and when. Fed by
 * the PROGRESS events (CAUGHT / SEEN, ProgressTracker); a first catch also goes out as DEX_FIRST {user, species, kind,
 * at}. The first time it is used it fills itself from the activity stream (oldest first, so the true first wins, at
 * its time), then from every player's stored progress for what the stream no longer holds (time 0: unknown).
 */
@Service
public class GroupDex {
    /** Who was first, and when (epoch ms; 0 = before records were kept). */
    public record First(String user, long at) {}

    /** By species name (as the games spell it). */
    public record Dex(Map<String, First> seen, Map<String, First> caught) {}

    private final GroupDexRepository repo;
    private final ProgressRepository progress;
    private final ApplicationEventPublisher events;
    private volatile boolean filled;

    public GroupDex(GroupDexRepository repo, ProgressRepository progress, ApplicationEventPublisher events) {
        this.repo = repo;
        this.progress = progress;
        this.events = events;
    }

    @EventListener
    public void on(LiveEvent e) {
        if (!"PROGRESS".equals(e.type()) || !(e.payload() instanceof ScoreEvent s)) return;
        fill();
        String key = key(s.type());
        if (key == null || !repo.claim(key, s.detail(), s.user(), s.at())) return;
        if (key.equals(GroupDexRepository.CAUGHT)) {
            events.publishEvent(new LiveEvent("DEX_FIRST", Map.of("user", s.user(), "species", s.detail(),
                    "kind", s.type(), "at", s.at())));
        }
    }

    public Dex dex() {
        fill();
        return new Dex(read(GroupDexRepository.SEEN), read(GroupDexRepository.CAUGHT));
    }

    private Map<String, First> read(String key) {
        Map<String, First> out = new HashMap<>();
        repo.all(key).forEach((species, v) -> {
            int bar = v.lastIndexOf('|');
            if (bar > 0) out.put(species, new First(v.substring(0, bar), Long.parseLong(v.substring(bar + 1))));
        });
        return out;
    }

    private synchronized void fill() {
        if (filled) return;
        if (repo.all(GroupDexRepository.SEEN).isEmpty()) {
            for (ScoreEvent s : progress.oldestFirst()) {
                String key = key(s.type());
                if (key != null) repo.claim(key, s.detail(), s.user(), s.at());
            }
            for (String user : progress.ranking()) {
                Progress p = progress.progress(user);
                p.caught().forEach(n -> repo.claim(GroupDexRepository.CAUGHT, n, user, 0));
                p.seen().forEach(n -> repo.claim(GroupDexRepository.SEEN, n, user, 0));
            }
        }
        filled = true;
    }

    private static String key(String type) {
        return switch (type) {
            case "CAUGHT" -> GroupDexRepository.CAUGHT;
            case "SEEN" -> GroupDexRepository.SEEN;
            default -> null;
        };
    }
}
