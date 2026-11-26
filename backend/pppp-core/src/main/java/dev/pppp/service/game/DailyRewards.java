package dev.pppp.service.game;

import dev.pppp.model.LiveEvent;
import dev.pppp.repository.DaysRepository;
import dev.pppp.repository.GiftRepository;
import dev.pppp.service.PlayerConsoles;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Daily rewards: each day a player plays (UTC; days played, not in a row, so a missed day costs nothing) from the
 * second on earns that day's reward from the calendar (day 1 only welcomes them and shows what tomorrow brings), sent as Mystery Gifts (MysteryGifts delivers them to the PC or, for a Pokémon, the
 * party). A week at a time, drawn once with a fixed seed (plan): small things on days 1-5, something better on day 6,
 * the week's big one plus a bonus on day 7 (the event Pokémon on weeks 4, 7, 9 and 13); the pools improve with the weeks. The
 * first activity of a day (PlayerConsoles.onActive: opening /play, a button, an activity ping) checks in; DAY_PLAYED
 * {user, day, reward} tells the browser.
 */
@Service
public class DailyRewards {
    private static final Logger log = LoggerFactory.getLogger(DailyRewards.class);

    /** quantity of a MysteryGifts.CATALOG gift; "random-stone" rolls one of STONES at check-in. */
    public record Grant(String gift, String name, int quantity, boolean pokemon) {}

    /**
     * A day of the calendar. tier frames it: common (days 1-5 of a week), rare (day 6), epic (day 7, the week's big
     * one), legendary (a Pokémon). title: the welcome, the weeks, the finale; null on the others.
     */
    public record Reward(int day, String title, String tier, List<Grant> grants) {}

    /** The player's count of days played, whether today counted yet, and the calendar. */
    public record Days(int days, boolean today, List<Reward> calendar) {}

    /** 13 weeks: the last day is the finale. */
    static final int CALENDAR = 91;
    private static final String RANDOM_STONE = "random-stone";
    private static final List<String> STONES = List.of("fire-stone", "water-stone", "thunderstone", "leaf-stone", "moon-stone", "sun-stone");

    /**
     * Day 7 of each week, by week (1-13): the week's big one, then a bonus. The Pokémon land on weeks 4, 7, 9 and 13
     * so the "next Pokémon" countdown always has one to show. Past week 13, weeks 5-12 again.
     */
    private static final List<List<Object>> WEEKLY = List.of(
            List.of("rare-candy", 2, "great-ball", 3),
            List.of(RANDOM_STONE, 1, "super-potion", 3),
            List.of("amulet-coin", 1, "ultra-ball", 3),
            List.of("surfing-pikachu", 1, "ultra-ball", 5),
            List.of("rare-candy", 3, "hyper-potion", 3),
            List.of(RANDOM_STONE, 1, "max-revive", 2),
            List.of("odd-egg", 1, "ultra-ball", 5),
            List.of("pp-up", 3, "full-restore", 2),
            List.of("mew", 1, "max-revive", 3),
            List.of(RANDOM_STONE, 1, "rare-candy", 1),
            List.of("rare-candy", 3, "nugget", 2),
            List.of("pp-up", 3, "rare-candy", 2),
            List.of("celebi", 1, "rare-candy", 3, "ultra-ball", 10));
    /** Day 6 (rare) after the first two weeks (Great Balls, then Ultra Balls), by phase: weeks 3-8, then 9 on. */
    private static final List<List<List<Object>>> RARE = List.of(
            List.of(List.of("ultra-ball", 5), List.of("max-revive", 2), List.of("full-restore", 2), List.of("pp-up", 1),
                    List.of("rare-candy", 1), List.of("nugget", 1)),
            List.of(List.of("ultra-ball", 8), List.of("max-revive", 3), List.of("full-restore", 3), List.of("pp-up", 2),
                    List.of("rare-candy", 2), List.of("nugget", 2)));
    /** Days 1-5 (common), by phase: weeks 1-3, 4-8, 9 on. */
    private static final List<List<List<Object>>> COMMON = List.of(
            List.of(List.of("poke-ball", 5), List.of("super-potion", 3), List.of("great-ball", 3), List.of("max-repel", 2),
                    List.of("revive", 1), List.of("moomoo-milk", 3)),
            List.of(List.of("great-ball", 5), List.of("hyper-potion", 2), List.of("revive", 2), List.of("max-repel", 3),
                    List.of("moomoo-milk", 4), List.of("ultra-ball", 2), List.of("gold-berry", 2)),
            List.of(List.of("ultra-ball", 3), List.of("hyper-potion", 3), List.of("max-revive", 1), List.of("full-restore", 1),
                    List.of("revive", 3), List.of("max-repel", 4), List.of("gold-berry", 3)));
    /** The plan, drawn once with a fixed seed: the same calendar for everyone, every run. 30 weeks. */
    private static final List<Reward> PLAN = plan(30 * 7, new Random(2026));

    private final DaysRepository days;
    private final GiftRepository gifts;
    private final ApplicationEventPublisher events;
    private final Map<String, LocalDate> checked = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public DailyRewards(PlayerConsoles consoles, DaysRepository days, GiftRepository gifts, ApplicationEventPublisher events) {
        this.days = days;
        this.gifts = gifts;
        this.events = events;
        consoles.onActive(this::checkIn);
    }

    /** The reward for the n-th day played (past the plan, its last weeks again). */
    static Reward reward(int day) {
        Reward r = PLAN.get(day <= PLAN.size() ? day - 1 : PLAN.size() - 14 + (day - PLAN.size() - 1) % 14);
        return day <= PLAN.size() ? r : new Reward(day, r.title(), r.tier(), r.grants());
    }

    /**
     * Random but progressive: days 1-5 are five different things from the common pool of their phase, day 6
     * is Great Balls in week 1, Ultra Balls in week 2, then a draw from the rare pool; day 7 is the week's (WEEKLY).
     * Day 1 only welcomes; day 2 is always 5 Poké Balls.
     */
    static List<Reward> plan(int days, Random random) {
        List<Reward> out = new ArrayList<>();
        String last = null;
        List<List<Object>> bag = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            int week = (day - 1) / 7 + 1, weekday = (day - 1) % 7 + 1;
            if (weekday == 1) { // this week's small things: the pool shuffled, none twice
                bag = new ArrayList<>(COMMON.get(week <= 3 ? 0 : week <= 8 ? 1 : 2));
                java.util.Collections.shuffle(bag, random);
                if (week == 1) bag.removeIf(g -> g.get(0).equals("poke-ball")); // day 2's
            }
            Reward r;
            if (day == 1) {
                r = new Reward(1, "Welcome, Pib!", "common", List.of());
            } else if (weekday == 7) {
                List<Object> g = WEEKLY.get(week <= WEEKLY.size() ? week - 1 : 4 + (week - WEEKLY.size() - 1) % 8);
                List<Grant> grants = new ArrayList<>();
                for (int i = 0; i < g.size(); i += 2) grants.add(grant((String) g.get(i), (Integer) g.get(i + 1)));
                boolean mon = grants.stream().anyMatch(Grant::pokemon);
                r = new Reward(day, day == CALENDAR ? "Three months!" : "Week " + week, mon ? "legendary" : "epic", List.copyOf(grants));
            } else if (weekday == 6) {
                List<Object> g = week == 1 ? List.of("great-ball", 5) : week == 2 ? List.of("ultra-ball", 5)
                        : draw(RARE.get(week <= 8 ? 0 : 1), last, random);
                r = new Reward(day, null, "rare", List.of(grant((String) g.get(0), (Integer) g.get(1))));
            } else {
                List<Object> g = day == 2 ? List.of("poke-ball", 5) : bag.removeFirst();
                r = new Reward(day, null, "common", List.of(grant((String) g.get(0), (Integer) g.get(1))));
            }
            out.add(r);
            last = r.grants().isEmpty() ? null : r.grants().getFirst().gift();
        }
        return List.copyOf(out);
    }

    private static List<Object> draw(List<List<Object>> pool, String not, Random random) {
        List<Object> g;
        do g = pool.get(random.nextInt(pool.size())); while (g.get(0).equals(not));
        return g;
    }

    static List<Reward> calendar() {
        return PLAN.subList(0, CALENDAR);
    }

    public Days days(String userId) {
        return new Days(days.count(userId), days.played(userId, today()), calendar());
    }

    /** The player's first activity today counts the day and queues its reward; later ones do nothing. */
    public void checkIn(String userId) {
        LocalDate today = today();
        if (today.equals(checked.put(userId, today))) return;
        if (!days.add(userId, today)) return; // counted already (before a restart)
        int day = days.count(userId);
        Reward reward = reward(day);
        List<Grant> sent = new ArrayList<>();
        for (Grant g : reward.grants()) {
            String gift = g.gift().equals(RANDOM_STONE) ? STONES.get(random.nextInt(STONES.size())) : g.gift();
            gifts.add(userId, gift, g.quantity());
            sent.add(grant(gift, g.quantity()));
        }
        events.publishEvent(new LiveEvent("DAY_PLAYED", Map.of("user", userId, "day", day,
                "reward", new Reward(day, reward.title(), reward.tier(), sent))));
        log.info("{} played day {}: {}", userId, day, sent);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static Grant grant(String gift, int quantity) {
        if (gift.equals(RANDOM_STONE)) return new Grant(gift, "Mystery stone", quantity, false);
        MysteryGifts.Gift g = MysteryGifts.CATALOG.stream().filter(c -> c.id().equals(gift)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no gift " + gift));
        return new Grant(g.id(), g.name(), quantity, g.pokemon());
    }
}
