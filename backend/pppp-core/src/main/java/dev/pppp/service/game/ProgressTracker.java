package dev.pppp.service.game;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scores every player's progress. Once a second it reads each in-memory console's RAM through the
 * {@link GameProfile} of that player's cartridge (none: the player scores nothing) and, once a reading has held
 * for two samples in a row, awards whatever it has that the player was never awarded before.
 *
 * <p>"Awarded before" is pibs:progress:{userId}, and a player with nothing stored starts from zero rather than
 * from their first sample: every console starts as a new game, so zero is the true baseline, and progress made
 * before this job existed still counts. Stored progress only grows, so a restart, a resumed console or a
 * restarted in-game save never awards the same badge or species twice.
 */
@Service
public class ProgressTracker {
    /** Points per event; placeholders. */
    static final int BADGE = 100, CAUGHT = 10, SEEN = 2, LEVEL_MILESTONE = 5, HALL_OF_FAME = 500;
    /** The party's highest level scores each time it passes a multiple of this. */
    static final int LEVEL_STEP = 10;
    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final ProgressRepository repo;
    private final ApplicationEventPublisher events;

    private final Map<String, Optional<GameProfile<?>>> profiles = new ConcurrentHashMap<>(); // by cartridge id
    private final Map<String, Progress> samples = new ConcurrentHashMap<>(); // the previous reading per player
    private final Map<String, Progress> awarded = new ConcurrentHashMap<>(); // pibs:progress, read once per player
    private Set<String> online = Set.of(); // poll thread only

    public ProgressTracker(PlayerConsoles consoles, CartridgeService cartridges, ProgressRepository repo,
                           ApplicationEventPublisher events) {
        this.consoles = consoles;
        this.cartridges = cartridges;
        this.repo = repo;
        this.events = events;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        Set<String> running = consoles.running();
        samples.keySet().retainAll(running);
        for (String userId : running) {
            try {
                String game = consoles.game(userId);
                GameProfile<?> profile = game == null ? null : profiles.computeIfAbsent(game, this::load).orElse(null);
                if (profile == null) continue;
                byte[] bytes = consoles.readBytes(userId, profile.ramStart(), profile.ramLength());
                Progress now = bytes == null ? null : read(profile, new Ram(profile.ramStart(), bytes));
                // Not in play (boot, title screen): keep the last reading. RAM is noisy while the game writes it,
                // so only a reading seen twice counts.
                if (now != null && now.equals(samples.put(userId, now))) record(userId, now);
            } catch (RuntimeException e) {
                log.warn("Progress poll failed for {}", userId, e);
            }
        }
        if (!running.equals(online)) { // a console came into memory: the online flags changed
            online = running;
            events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
        }
    }

    /** Awards what this reading has beyond everything the player was awarded before. */
    public synchronized void record(String userId, Progress now) {
        Progress before = awarded.computeIfAbsent(userId, repo::progress);
        Progress after = before.merge(now);
        if (after.equals(before)) return;
        List<ScoreEvent> scored = score(userId, before, after, System.currentTimeMillis());
        repo.award(userId, after, scored);
        awarded.put(userId, after);
        if (scored.isEmpty()) return;
        scored.forEach(e -> events.publishEvent(new LiveEvent("PROGRESS", e)));
        events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
    }

    /** The scored steps from before to after. */
    static List<ScoreEvent> score(String userId, Progress before, Progress after, long at) {
        List<ScoreEvent> out = new ArrayList<>();
        fresh(before.badges(), after.badges()).forEach(b -> out.add(new ScoreEvent(userId, "BADGE", b, BADGE, at)));
        fresh(before.caught(), after.caught()).forEach(s -> out.add(new ScoreEvent(userId, "CAUGHT", s, CAUGHT, at)));
        fresh(before.seen(), after.seen()).forEach(s -> out.add(new ScoreEvent(userId, "SEEN", s, SEEN, at)));
        for (int l = (before.level() / LEVEL_STEP + 1) * LEVEL_STEP; l <= after.level(); l += LEVEL_STEP) {
            out.add(new ScoreEvent(userId, "LEVEL", Integer.toString(l), LEVEL_MILESTONE, at));
        }
        if (after.hallOfFame() && !before.hallOfFame()) out.add(new ScoreEvent(userId, "HALL_OF_FAME", "", HALL_OF_FAME, at));
        return out;
    }

    private static List<String> fresh(Set<String> before, Set<String> after) {
        return after.stream().filter(n -> !before.contains(n)).toList();
    }

    /** Every player who picked a game, most points first. */
    public List<ScoreRow> scoreboard() {
        Set<String> running = consoles.running();
        List<ScoreRow> rows = new ArrayList<>();
        repo.scores().forEach((userId, points) -> {
            Progress p = awarded.computeIfAbsent(userId, repo::progress);
            rows.add(new ScoreRow(userId, points, p.badges().size(), p.caught().size(), p.seen().size(),
                    consoles.game(userId), running.contains(userId)));
        });
        return rows;
    }

    /** The activity feed, newest first. */
    public List<ScoreEvent> activity(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1 to 100");
        return repo.newest(limit);
    }

    private static <S> Progress read(GameProfile<S> profile, Ram ram) {
        S state = profile.read(ram);
        return state == null ? null : profile.progress(state);
    }

    private Optional<GameProfile<?>> load(String cartridgeId) {
        try {
            return Optional.ofNullable(GameProfile.forRom(Files.readAllBytes(cartridges.require(cartridgeId))));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not load a game profile for {}", cartridgeId, e);
            return Optional.empty();
        }
    }
}
