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
            "LINK_CHALLENGE", "LINK_SESSION", "PLAYER_RESET", "WATCH_REACTION", "WONDER_TRADE", "WONDER_ARRIVED", "WONDER_POOL",
            "DEX_FIRST", "DAY_PLAYED");

    private final PlayerConsoles consoles;
    private final LinkService link;
    private final EventPublisher events;
    private final ScreenWebSocketHandler screens;
    private final AudioWebSocketHandler audio;
    private final WatchWebSocketHandler watch;
    private final ProgressRepository progress;
    private final NoteRepository notes;
    private final ProgressTracker tracker;
    private final dev.pppp.repository.StatsRepository stats;
    private final dev.pppp.repository.GiftRepository gifts;
    private final dev.pppp.repository.AccessRequestRepository requests;
    private final dev.pppp.repository.BloodstainRepository stains;
    private final dev.pppp.repository.GroupDexRepository dex;
    private final dev.pppp.repository.WonderRepository wonder;
    private final dev.pppp.repository.PlayerRepository players;
    private MeterRegistry registry;

    public GameMetrics(PlayerConsoles consoles, LinkService link, EventPublisher events, ScreenWebSocketHandler screens,
                       AudioWebSocketHandler audio, WatchWebSocketHandler watch, ProgressRepository progress,
                       NoteRepository notes, ProgressTracker tracker, dev.pppp.repository.StatsRepository stats,
                       dev.pppp.repository.GiftRepository gifts, dev.pppp.repository.AccessRequestRepository requests,
                       dev.pppp.repository.BloodstainRepository stains, dev.pppp.repository.GroupDexRepository dex,
                       dev.pppp.repository.WonderRepository wonder, dev.pppp.repository.PlayerRepository players) {
        this.stains = stains;
        this.dex = dex;
        this.wonder = wonder;
        this.players = players;
        this.tracker = tracker;
        this.stats = stats;
        this.gifts = gifts;
        this.requests = requests;
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

        // Daily active and returning players (pibs:active:{day}, UTC days).
        gauge(r, "pppp_players_active", "Players who played in the window", m -> slow().activeToday, "window", "today");
        gauge(r, "pppp_players_active", "Players who played in the window", m -> slow().activeYesterday, "window", "yesterday");
        gauge(r, "pppp_players_active", "Players who played in the window", m -> slow().active7d, "window", "7d");
        gauge(r, "pppp_players_active", "Players who played in the window", m -> slow().active30d, "window", "30d");
        gauge(r, "pppp_players_returned", "Of yesterday's players, those back today", m -> slow().returnedDay, "after", "1d");
        gauge(r, "pppp_players_returned", "Of last week's players (8-14 days ago), those back in the last 7 days", m -> slow().returnedWeek, "after", "7d");
        gauge(r, "pppp_players_active_prev_week", "Players who played 8-14 days ago", m -> slow().previousWeek);
        // In-game play time at each story step, for players whose unlock was seen with a play time.
        MILESTONES.forEach((step, achievement) -> {
            gauge(r, "pppp_hours_to_avg", "Average in-game hours when players reached this step", m -> slow().hoursTo.getOrDefault(step, new double[]{Double.NaN, 0})[0], "step", step);
            gauge(r, "pppp_hours_to_players", "Players with a known play time for this step", m -> slow().hoursTo.getOrDefault(step, new double[]{0, 0})[1], "step", step);
        });
        gauge(r, "pppp_eggs_hatched", "Eggs hatched, added up over every player", m -> slow().hatched);
        gauge(r, "pppp_shinies", "Shiny Pokémon ever seen in a party, added up over every player", m -> slow().shinies);
        gauge(r, "pppp_gifts", "Mystery Gifts", m -> slow().totals.getOrDefault("giftsSent", 0L), "state", "sent");
        gauge(r, "pppp_gifts", "Mystery Gifts", m -> slow().totals.getOrDefault("giftsDelivered", 0L), "state", "delivered");
        gauge(r, "pppp_gifts", "Mystery Gifts", m -> slow().giftsWaiting, "state", "waiting");
        gauge(r, "pppp_notes_rated", "Notes at least one player rated Good", m -> slow().notesRated);
        gauge(r, "pppp_note_goods", "Good ratings on notes still on the ground", m -> slow().noteGoods);
        gauge(r, "pppp_access_requests", "Sign-in requests waiting for an admin", m -> slow().requests);
        gauge(r, "pppp_access_request_oldest_seconds", "How long the oldest waiting request has waited", m -> slow().oldestWait);
        gauge(r, "pppp_access_accepted", "Sign-in requests let in (running total)", m -> slow().totals.getOrDefault("accessAccepted", 0L));
        gauge(r, "pppp_access_rejected", "Sign-in requests turned down (running total)", m -> slow().totals.getOrDefault("accessRejected", 0L));
        gauge(r, "pppp_access_wait_seconds", "Time requests waited before being let in, added up", m -> slow().totals.getOrDefault("accessWaitSeconds", 0L));
        // Later features: bloodstains, the Pibs' Pokédex, Wonder Trade, AAAA's calls, the Konami gift.
        gauge(r, "pppp_bloodstains", "Bloodstains on the ground (blackouts in the last 48 h)", m -> slow().bloodstains);
        gauge(r, "pppp_group_dex", "Species in the Pibs' Pokédex (anyone)", m -> slow().dexSeen, "state", "seen");
        gauge(r, "pppp_group_dex", "Species in the Pibs' Pokédex (anyone)", m -> slow().dexCaught, "state", "caught");
        gauge(r, "pppp_wonder_pool", "Pokémon waiting in the Wonder Trade pool", m -> slow().wonderPool);
        for (String call : dev.pppp.service.game.AaaaCalls.IDS) {
            gauge(r, "pppp_aaaa_calls", "Players AAAA's phone call has rung for", m -> slow().calls.getOrDefault(call, 0L), "call", call);
        }
        gauge(r, "pppp_konami_gifts", "Players who found the Konami code (and got the prima donna)", m -> slow().konami);
    }

    /** Steps timed in play hours and their achievements. */
    private static final java.util.Map<String, String> MILESTONES = new java.util.LinkedHashMap<>();
    static {
        MILESTONES.put("first badge", "zephyr-badge");
        MILESTONES.put("eight badges", "rising-badge");
        MILESTONES.put("champion", "champion-of-johto");
        MILESTONES.put("red defeated", "the-silent-summit");
    }

    /** What the Redis-heavy gauges read, built at most once a minute. */
    private record Slow(long activeToday, long activeYesterday, long active7d, long active30d, long returnedDay,
                        long returnedWeek, long previousWeek, java.util.Map<String, double[]> hoursTo, long hatched,
                        long shinies, java.util.Map<String, Long> totals, long giftsWaiting, long notesRated,
                        long noteGoods, long requests, double oldestWait, long bloodstains, long dexSeen,
                        long dexCaught, long wonderPool, java.util.Map<String, Long> calls, long konami) {}

    private volatile Slow slow;
    private volatile long slowAt;

    private Slow slow() {
        long now = System.currentTimeMillis();
        if (slow == null || now - slowAt > 60_000) {
            slow = buildSlow();
            slowAt = now;
        }
        return slow;
    }

    private Slow buildSlow() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        Set<String> todays = stats.active(List.of(today)), yesterdays = stats.active(List.of(today.minusDays(1)));
        Set<String> week = stats.active(days(today, 0, 7)), previous = stats.active(days(today, 7, 14));
        java.util.Map<String, double[]> hoursTo = new java.util.HashMap<>();
        MILESTONES.forEach((step, id) -> {
            List<Integer> mins = tracker.minutesTo(id);
            hoursTo.put(step, new double[]{mins.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN) / 60, mins.size()});
        });
        var logs = tracker.logs();
        var allNotes = notes.all();
        long rated = 0, goods = 0;
        for (var n : allNotes) {
            long g = notes.goods(n.id());
            if (g > 0) rated++;
            goods += g;
        }
        var waiting = requests.findAll();
        long since = System.currentTimeMillis() - java.time.Duration.ofHours(48).toMillis();
        java.util.Map<String, Long> calls = new java.util.HashMap<>();
        long konami = 0;
        for (String user : progress.ranking()) {
            players.aaaaCalls(user).forEach((call, v) -> {
                if (!"pending".equals(v)) calls.merge(call, 1L, Long::sum);
            });
            if (players.konamiGift(user)) konami++;
        }
        long oldest = waiting.stream().mapToLong(dev.pppp.model.AccessRequest::requestedAt).min().orElse(0);
        return new Slow(todays.size(), yesterdays.size(), week.size(), stats.active(days(today, 0, 30)).size(),
                yesterdays.stream().filter(todays::contains).count(), previous.stream().filter(week::contains).count(),
                previous.size(), hoursTo, logs.stream().mapToLong(dev.pppp.model.TrainerLog::hatched).sum(),
                logs.stream().mapToLong(l -> l.shinies().size()).sum(), stats.totals(), gifts.waiting(progress.ranking()),
                rated, goods, waiting.size(), oldest == 0 ? 0 : (System.currentTimeMillis() - oldest) / 1000.0,
                stains.all().stream().filter(b -> b.at() >= since).count(), dex.all(dev.pppp.repository.GroupDexRepository.SEEN).size(),
                dex.all(dev.pppp.repository.GroupDexRepository.CAUGHT).size(), wonder.pool().size(), calls, konami);
    }

    /** UTC days from today - from to today - (to - 1). */
    private static List<java.time.LocalDate> days(java.time.LocalDate today, int from, int to) {
        return java.util.stream.IntStream.range(from, to).mapToObj(today::minusDays).toList();
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
