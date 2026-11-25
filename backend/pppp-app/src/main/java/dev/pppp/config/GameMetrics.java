package dev.pppp.config;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.ChatMessage;
import dev.pppp.model.LinkState;
import dev.pppp.model.LiveEvent;
import dev.pppp.realtime.AudioWebSocketHandler;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.realtime.ScreenWebSocketHandler;
import dev.pppp.realtime.WatchWebSocketHandler;
import dev.pppp.repository.NoteRepository;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.LinkService;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.ProgressTracker;
import dev.pppp.model.ScoreRow;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Game metrics for /actuator/prometheus (Grafana's PPPPv2 dashboard, turing-pi-server). Gauges are read at each
 * scrape; no player ids in labels, only aggregates. Counters come from the LiveEvents the services already publish.
 */
@Component
public class GameMetrics implements MeterBinder {
    private static final List<String> STATES = List.of("RUNNING", "IDLE", "PAUSED");
    private static final List<String> GAMES = List.of("crystal", "gold", "silver", "red", "other");
    private static final List<String> KINDS = List.of("TRADE", "BATTLE");
    /** Events worth counting; the rest (SCOREBOARD_CHANGED, GAME_STATE, ...) are just refreshes. */
    private static final Set<String> COUNTED = Set.of("PROGRESS", "ACHIEVEMENT_UNLOCKED", "MYSTERY_GIFT", "CHAT_MESSAGE",
            "LINK_CHALLENGE", "LINK_SESSION", "PLAYER_RESET");

    private final PlayerConsoles consoles;
    private final LinkService link;
    private final EventPublisher events;
    private final ScreenWebSocketHandler screens;
    private final AudioWebSocketHandler audio;
    private final WatchWebSocketHandler watch;
    private final ProgressRepository progress;
    private final NoteRepository notes;
    private final ProgressTracker tracker;
    private MeterRegistry registry;

    public GameMetrics(PlayerConsoles consoles, LinkService link, EventPublisher events, ScreenWebSocketHandler screens,
                       AudioWebSocketHandler audio, WatchWebSocketHandler watch, ProgressRepository progress,
                       NoteRepository notes, ProgressTracker tracker) {
        this.tracker = tracker;
        this.consoles = consoles;
        this.link = link;
        this.events = events;
        this.screens = screens;
        this.audio = audio;
        this.watch = watch;
        this.progress = progress;
        this.notes = notes;
    }

    @Override
    public void bindTo(MeterRegistry r) {
        registry = r;
        for (String state : STATES) {
            gauge(r, "pppp_consoles", "Game Boys in memory by state", m -> count(s -> state.equals(s.state())), "state", state);
        }
        for (String game : GAMES) {
            gauge(r, "pppp_consoles_by_game", "Game Boys in memory by cartridge", m -> count(s -> game.equals(game(s.cartridgeId()))), "game", game);
        }
        gauge(r, "pppp_consoles_fast", "Running Game Boys on Fast", m -> running().stream()
                .map(consoles::speed).filter(v -> v != null && v > 1).count());
        gauge(r, "pppp_console_speed_avg", "Average measured speed of running Game Boys (1 = a real Game Boy)",
                m -> speeds().average().orElse(Double.NaN));
        gauge(r, "pppp_console_speed_min", "Slowest running Game Boy's measured speed", m -> speeds().min().orElse(Double.NaN));
        gauge(r, "pppp_consoles_lagging", "Running Game Boys below 90% of the speed they are set to", m -> running().stream()
                .filter(id -> {
                    Double set = consoles.speed(id), real = consoles.measuredSpeed(id);
                    // ponytail: Fast (4x) runs "as fast as it can", so only 1x consoles can lag.
                    return set != null && real != null && set == 1 && real > 0 && real < 0.9;
                }).count());
        gauge(r, "pppp_emulator_cpu_cores", "CPU the emulator threads used over their last second, in cores", m -> timing(EmulatorHost.Timing::emulateCpuMs));
        gauge(r, "pppp_emulator_listeners_cores", "Wall time spent handing frames and sound to sockets, in cores", m -> timing(EmulatorHost.Timing::listenersMs));
        gauge(r, "pppp_emulator_hooks_cores", "Wall time in per-frame game hooks, in cores", m -> timing(EmulatorHost.Timing::hooksMs));

        gauge(r, "pppp_players_online", "Players with a tab open", m -> events.viewers().size());
        gauge(r, "pppp_scoreboard_players", "Players on the scoreboard", m -> progress.ranking().size());
        gauge(r, "pppp_websockets", "Open WebSockets", m -> events.sockets(), "endpoint", "events");
        gauge(r, "pppp_websockets", "Open WebSockets", m -> screens.sockets(), "endpoint", "screen");
        gauge(r, "pppp_websockets", "Open WebSockets", m -> audio.sockets(), "endpoint", "audio");
        gauge(r, "pppp_websockets", "Open WebSockets", m -> watch.sockets(), "endpoint", "watch");

        for (String kind : KINDS) {
            gauge(r, "pppp_link_sessions", "Link cable sessions plugged in", m -> lobby().links().stream().filter(l -> kind.equals(l.kind())).count(), "kind", kind);
            gauge(r, "pppp_lobby_ready", "Players waiting in the lobby", m -> lobby().lobby().stream().filter(l -> kind.equals(l.kind())).count(), "kind", kind);
        }
        gauge(r, "pppp_notes", "Notes on the ground", m -> notes.all().size());

        // The players' games, from the scoreboard (ProgressTracker's caches of the confirmed readings).
        gauge(r, "pppp_pokemon_caught", "Pokédex species caught, added up over every player", m -> rows(ScoreRow::caught));
        gauge(r, "pppp_pokemon_seen", "Pokédex species seen, added up over every player", m -> rows(ScoreRow::seen));
        gauge(r, "pppp_badges", "Badges earned, added up over every player", m -> rows(x -> Integer.bitCount(x.johtoBadges() & 0xFF)), "region", "johto");
        gauge(r, "pppp_badges", "Badges earned, added up over every player", m -> rows(x -> Integer.bitCount(x.kantoBadges() & 0xFF)), "region", "kanto");
        for (int n = 0; n <= 16; n++) {
            int badges = n;
            gauge(r, "pppp_players_by_badges", "Players holding this many badges (Johto + Kanto)",
                    m -> scoreboard().stream().filter(x -> badges(x) == badges).count(), "badges", String.valueOf(n));
        }
        for (String game : GAMES) {
            gauge(r, "pppp_players_by_game", "Players by the game they picked", m -> scoreboard().stream().filter(x -> game.equals(game(x.game()))).count(), "game", game);
        }
        JOURNEY.forEach((step, achievement) -> gauge(r, "pppp_players_reached", "Players who reached this point of the story",
                m -> tracker.unlockedBy(achievement), "step", step));
        gauge(r, "pppp_pokemon_traded", "Pokémon received in link trades, added up (a trade between two Pibs counts 2)", m -> rows(x -> x.link().trades()));
        gauge(r, "pppp_link_battle_wins", "Link battles won, added up (each finished battle has one winner)", m -> rows(x -> x.link().wins()));
        gauge(r, "pppp_achievements_unlocked", "Achievements unlocked, added up over every player", m -> rows(ScoreRow::achievementsUnlocked));
        gauge(r, "pppp_trainer_score_avg", "Average Trainer Score (0-1000) of players who picked a game",
                m -> scoreboard().stream().mapToInt(ScoreRow::trainerScore).average().orElse(0));
        gauge(r, "pppp_play_hours", "In-game play time, added up over every player, in hours",
                m -> scoreboard().stream().mapToDouble(x -> hours(x.playTime())).sum());
    }

    /** Story steps and the achievement that marks each (Achievements). */
    private static final java.util.Map<String, String> JOURNEY = new java.util.LinkedHashMap<>();
    static {
        JOURNEY.put("1 starter", "new-bark-beginnings");
        JOURNEY.put("2 first badge", "zephyr-badge");
        JOURNEY.put("3 eight badges", "rising-badge");
        JOURNEY.put("4 champion", "champion-of-johto");
        JOURNEY.put("5 kanto badges", "back-to-kanto");
        JOURNEY.put("6 red defeated", "the-silent-summit");
    }

    private volatile List<ScoreRow> rows = List.of();
    private volatile long rowsAt;

    /** The scoreboard, at most 10 s old: one build serves every gauge of a scrape. */
    private List<ScoreRow> scoreboard() {
        long now = System.currentTimeMillis();
        if (now - rowsAt > 10_000) {
            rows = tracker.scoreboard();
            rowsAt = now;
        }
        return rows;
    }

    private double rows(ToIntFunction<ScoreRow> f) {
        return scoreboard().stream().mapToInt(f).sum();
    }

    /** Badges held, Johto + Kanto (TrainerCard keeps the games' bitmasks). */
    static int badges(ScoreRow x) {
        return Integer.bitCount(x.johtoBadges() & 0xFF) + Integer.bitCount(x.kantoBadges() & 0xFF);
    }

    /** "H:MM" in hours; 0 for anything else. */
    static double hours(String playTime) {
        if (playTime == null || !playTime.matches("\\d+:\\d{2}")) return 0;
        String[] p = playTime.split(":");
        return Integer.parseInt(p[0]) + Integer.parseInt(p[1]) / 60.0;
    }

    /** One counter per counted live event: pppp_events_total{type, detail, kind} (detail: chat kind or link status; kind: TRADE/BATTLE). */
    @EventListener
    public void count(LiveEvent e) {
        if (registry == null || !COUNTED.contains(e.type())) return;
        String detail = switch (e.payload()) {
            case ChatMessage m -> m.kind();
            case LinkState.SessionEvent s -> s.status();
            case LinkState.Challenge c -> Objects.requireNonNullElse(c.status(), "SENT");
            default -> "";
        };
        String kind = switch (e.payload()) {
            case LinkState.SessionEvent s -> s.kind();
            case LinkState.Challenge c -> c.kind();
            default -> "";
        };
        Counter.builder("pppp_events").description("Game events").tag("type", e.type()).tag("detail", detail)
                .tag("kind", Objects.requireNonNullElse(kind, "")).register(registry).increment();
    }

    private void gauge(MeterRegistry r, String name, String help, ToDoubleFunction<GameMetrics> f, String... tags) {
        Gauge.builder(name, this, f).description(help).tags(tags).register(r); // held weakly: the bean outlives it
    }

    private long count(java.util.function.Predicate<PlayerConsoles.Session> p) {
        return consoles.sessions().stream().filter(p).count();
    }

    private List<String> running() {
        return consoles.sessions().stream().filter(s -> "RUNNING".equals(s.state())).map(PlayerConsoles.Session::userId).toList();
    }

    private java.util.stream.DoubleStream speeds() {
        return running().stream().map(consoles::measuredSpeed).filter(v -> v != null && v > 0).mapToDouble(Double::doubleValue);
    }

    private double timing(ToDoubleFunction<EmulatorHost.Timing> part) {
        return running().stream().map(consoles::timing).filter(Objects::nonNull).mapToDouble(part).sum() / 1000;
    }

    private LinkState lobby() {
        return link.state("");
    }

    static String game(String cartridgeId) {
        if (cartridgeId == null) return "other";
        for (String g : List.of("crystal", "gold", "silver", "red")) if (cartridgeId.contains(g)) return g;
        return "other";
    }
}
