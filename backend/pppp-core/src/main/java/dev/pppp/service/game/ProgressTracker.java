package dev.pppp.service.game;

import dev.pppp.exception.NotFoundException;
import dev.pppp.model.Achievement;
import dev.pppp.model.GameSummary;
import dev.pppp.model.LiveEvent;
import org.springframework.context.event.EventListener;
import java.util.HashSet;
import dev.pppp.model.LinkState;
import dev.pppp.model.PlayerAchievements;
import dev.pppp.model.PlayerProfile;
import dev.pppp.model.Progress;
import dev.pppp.model.ScoreEvent;
import dev.pppp.model.ScoreRow;
import dev.pppp.model.TrainerCard;
import dev.pppp.model.TrainerLog;
import dev.pppp.model.TrainerScore;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * for two samples in a row, awards whatever it has that the player was never awarded before. The same reading is
 * the player's party box ({@link #summary}); a change goes out as GAME_STATE {user, state}.
 *
 * <p>"Awarded before" is pibs:progress:{userId}, and a player with nothing stored starts from zero rather than
 * from their first sample: every console starts as a new game, so zero is the true baseline, and progress made
 * before this job existed still counts. Stored progress only grows, so a restart, a resumed console or a
 * restarted in-game save never awards the same badge or species twice.
 *
 * <p>For Gold/Silver/Crystal the reading also unlocks {@link Achievements} (kept for good in
 * pibs:achievements:{userId}, the same way: never unlocked twice) and rates the save with a Trainer Score
 * ({@link TrainerScoring}; live, it can go down). The scoreboard ranks by Trainer Score, then achievement points.
 * What the achievements need remembered goes to pibs:history:{userId} ({@link TrainerLog}). The battery save (PC
 * boxes, link battle record, Battle Tower) is read once a minute, every few seconds in the Battle Tower, and
 * counts once two reads in a row agree.
 */
@Service
public class ProgressTracker {
    /** Points per event; placeholders. */
    static final int BADGE = 100, CAUGHT = 10, SEEN = 2, LEVEL_MILESTONE = 5, HALL_OF_FAME = 500;
    static final int LINK_WIN = 25; // per link battle won (the save's record), added to the activity points
    /** The party's highest level scores each time it passes a multiple of this. */
    static final int LEVEL_STEP = 10;
    /** How often the battery save is flushed and read (ms); in the Battle Tower, whose win lasts a short while. */
    static final long SRAM_EVERY = 60_000, SRAM_EVERY_IN_TOWER = 5_000, LINK_AFTERGLOW = 120_000;
    /** Linked players, and the ones whose cable came out less than LINK_AFTERGLOW ago: userId -> until when (MAX_VALUE while linked). */
    private final Map<String, Long> linkFast = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final ProgressRepository repo;
    private final ApplicationEventPublisher events;

    private final Map<String, Optional<GameProfile<?>>> profiles = new ConcurrentHashMap<>(); // by cartridge id
    /** One sample of a console: what it scores, what the party box shows, and the Gen 2 save (or null). */
    private record Reading(Progress progress, GameSummary summary, Gen2Save save) {}

    private final Map<String, Reading> samples = new ConcurrentHashMap<>(); // the previous reading per player
    private final Map<String, GameSummary> summaries = new ConcurrentHashMap<>(); // the last confirmed one per player
    private final Map<String, Progress> awarded = new ConcurrentHashMap<>(); // pibs:progress, read once per player
    private final Map<String, Map<String, Long>> unlocked = new ConcurrentHashMap<>(); // pibs:achievements, likewise
    private final Map<String, TrainerScore> trainers = new ConcurrentHashMap<>(); // pibs:trainer, likewise
    private final Map<String, Gen2Save> saves = new ConcurrentHashMap<>(); // the last confirmed save per running console
    private final Map<String, TrainerCard> cards = new ConcurrentHashMap<>(); // pibs:trainer card, read once
    private final Map<String, TrainerLog> logs = new ConcurrentHashMap<>(); // pibs:history, read once
    private final Map<String, Gen2Save.Sram> srams = new ConcurrentHashMap<>(); // the last confirmed battery save read
    private final Map<String, Gen2Save.Sram> sramSamples = new ConcurrentHashMap<>(); // the previous one per player
    private final Map<String, String> lastPartner = new ConcurrentHashMap<>(); // who was last on their link cable
    long sramEvery = SRAM_EVERY; // tests read the battery save on every poll
    private final Map<String, Long> sramReadAt = new ConcurrentHashMap<>(); // poll thread only
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
        summaries.keySet().retainAll(running);
        saves.keySet().retainAll(running);
        sramSamples.keySet().retainAll(running);
        for (String userId : running) {
            try {
                GameProfile<?> profile = profile(userId);
                if (profile == null) continue;
                byte[] bytes = consoles.readBytes(userId, profile.ramStart(), profile.ramLength());
                Reading now = bytes == null ? null : read(profile, new Ram(profile.ramStart(), bytes));
                // Not in play (boot, title screen): keep the last reading. RAM is noisy while the game writes it,
                // so only a reading seen twice counts.
                if (now == null || !now.equals(samples.put(userId, now))) continue;
                if (!now.summary().equals(summaries.put(userId, now.summary()))) {
                    events.publishEvent(new LiveEvent("GAME_STATE", Map.of("user", userId, "state", now.summary())));
                }
                // A paused console's cartridge RAM cannot change: no need to write its .sav again, once its last
                // change is confirmed (a player who leaves /play right after a link battle still gets it counted).
                if (now.save() != null && profile instanceof PokemonGen2 && (consoles.isAwake(userId)
                        || srams.get(userId) == null || !srams.get(userId).equals(sramSamples.get(userId)))) {
                    readSram(userId, now.save());
                }
                record(userId, now.progress(), now.save());
            } catch (RuntimeException e) {
                log.warn("Progress poll failed for {}", userId, e);
            }
        }
        if (!running.equals(online)) { // a console came into memory: the online flags changed
            online = running;
            events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
        }
    }

    /**
     * Flushes and reads the player's battery save when it is due; it counts once two reads in a row agree. Every 5 s in
     * the Battle Tower, while linked and for two minutes after (the link battle record lives only there), else every
     * minute.
     */
    private void readSram(String userId, Gen2Save save) {
        long now = System.currentTimeMillis();
        boolean fast = save.map() != null && save.map().startsWith("BATTLE_TOWER") || linkFast.getOrDefault(userId, 0L) > now;
        long due = fast ? Math.min(SRAM_EVERY_IN_TOWER, sramEvery) : sramEvery;
        if (now - sramReadAt.getOrDefault(userId, 0L) < due) return;
        sramReadAt.put(userId, now);
        Gen2Save.Sram read = PokemonGen2.sram(save.crystal(), consoles.batterySave(userId), save.curBox());
        if (read != null && read.equals(sramSamples.put(userId, read))) srams.put(userId, read);
    }

    /** Awards what this reading has beyond everything the player was awarded before. */
    public void record(String userId, Progress now) {
        record(userId, now, null);
    }

    /**
     * Like {@link #record(String, Progress)}, and for a Gen 2 save also unlocks achievements and rates it. The
     * achievements that look at a moment (the Hall of Fame, beating Red) compare it with the previous save
     * recorded for this player while their console stays in memory.
     */
    public synchronized void record(String userId, Progress now, Gen2Save save) {
        boolean changed = award(userId, now);
        if (save != null) changed |= rate(userId, saves.put(userId, save), save) | card(userId, save);
        if (changed) events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
    }

    /** True when it scored something. */
    private boolean award(String userId, Progress now) {
        Progress before = awarded.computeIfAbsent(userId, repo::progress);
        Progress after = before.merge(now);
        if (after.equals(before)) return false;
        List<ScoreEvent> scored = score(userId, before, after, System.currentTimeMillis());
        repo.award(userId, after, scored);
        awarded.put(userId, after);
        scored.forEach(e -> events.publishEvent(new LiveEvent("PROGRESS", e)));
        return !scored.isEmpty();
    }

    /**
     * Stores the card (party, location, play time, badges) when it changed; true when the scoreboard shows the
     * change. The play time alone changes every minute, so it is stored but not pushed; HP is pushed as the
     * scoreboard shows it (a percentage), and only from a confirmed reading like everything else.
     */
    private boolean card(String userId, Gen2Save save) {
        TrainerCard now = new TrainerCard(save.members(), save.location(), PokemonGen2.playTime(save),
                save.johtoBadges(), save.kantoBadges(), save.trainerName(), save.pin(), save.spot(), cableClub(save));
        TrainerCard was = cards.computeIfAbsent(userId, repo::card);
        if (now.equals(was)) return false;
        repo.saveCard(userId, now);
        cards.put(userId, now);
        return !members(now).equals(members(was)) || !Objects.equals(now.location(), was.location())
                || now.johtoBadges() != was.johtoBadges() || now.kantoBadges() != was.kantoBadges()
                || !Objects.equals(now.trainerName(), was.trainerName()) || now.cableClub() != was.cableClub();
    }

    /** TrainerCard.cableClub of a reading: the story flags that open the Cable Club, furthest first. */
    static int cableClub(Gen2Save save) {
        if (save.flags().contains(Gen2Save.EVENT_GAVE_MYSTERY_EGG_TO_ELM)) return TrainerCard.CABLE_CLUB_OPEN;
        if (save.flags().contains(Gen2Save.EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON)) return 2;
        return save.gotStarter() ? 1 : 0;
    }

    /**
     * Whether the player can use a Cable Club: always for games without one to wait for, and for Gold, Silver and
     * Crystal once they gave Elm the Mystery Egg (their last confirmed reading, kept while they are away).
     */
    public boolean cableClubOpen(String userId) {
        if (!(profile(userId) instanceof PokemonGen2)) return true;
        return cableClub(cards.computeIfAbsent(userId, repo::card), logs.computeIfAbsent(userId, repo::log))
                >= TrainerCard.CABLE_CLUB_OPEN;
    }

    /**
     * The card's Cable Club step, or open for a card saved before the step existed (it reads 0) when the player has
     * a badge (Elm gets the egg before Violet's gym) or ever linked.
     */
    static int cableClub(TrainerCard card, TrainerLog log) {
        boolean shows = card.johtoBadges() != 0 || card.kantoBadges() != 0
                || log != null && (log.trades() + log.linkWins() + log.linkLosses() > 0);
        return shows ? TrainerCard.CABLE_CLUB_OPEN : card.cableClub();
    }

    private static List<ScoreRow.Member> members(TrainerCard card) {
        return card.party().stream().map(ScoreRow.Member::of).toList();
    }

    /** Unlocks what the save earns for the first time and stores its Trainer Score; true when either changed. */
    private boolean rate(String userId, Gen2Save before, Gen2Save save) {
        TrainerLog logBefore = logs.computeIfAbsent(userId, repo::log);
        TrainerLog log = log(userId, before, save);
        long at = System.currentTimeMillis();
        List<ScoreEvent> battles = battles(userId, logBefore == null ? TrainerLog.EMPTY : logBefore, log, at);
        Map<String, Long> had = unlocked.computeIfAbsent(userId, repo::achievements);
        TrainerScore was = trainers.computeIfAbsent(userId, repo::trainer), score = TrainerScoring.score(save);
        Map<String, Long> fresh = new LinkedHashMap<>();
        Achievements.earned(new Achievements.Moment(before, save, log, srams.get(userId))).stream()
                .filter(id -> !had.containsKey(id)).forEach(id -> fresh.put(id, at));
        if (fresh.isEmpty() && score.equals(was) && battles.isEmpty()) return false;
        Map<String, Long> all = new HashMap<>(had);
        all.putAll(fresh);
        List<ScoreEvent> feed = new ArrayList<>(battles);
        fresh.keySet().forEach(id -> feed.add(new ScoreEvent(userId, "ACHIEVEMENT", name(id), Achievements.points(id), at)));
        if (!score.title().equals(was.title())) {
            feed.add(new ScoreEvent(userId, "TITLE", score.title() + " (" + score.grade() + ")", 0, at));
        }
        repo.rate(userId, score, fresh, rank(score, all), feed);
        unlocked.put(userId, all);
        trainers.put(userId, score);
        fresh.keySet().forEach(id -> events.publishEvent(new LiveEvent("ACHIEVEMENT_UNLOCKED", Map.of("user", userId,
                "id", id, "name", name(id), "points", Achievements.points(id), "at", at))));
        feed.forEach(e -> events.publishEvent(new LiveEvent("PROGRESS", e)));
        return true;
    }

    /**
     * A link cable went in (their battery saves are read every 5 s from now until two minutes after it comes out): each
     * Pib is the other's partner from this moment, whether or not a reading of their game
     * gets confirmed during the session (sessions can be a few seconds), and the link achievements that need only
     * that (Plugged In, Social Butterfly) unlock for both at once.
     */
    @EventListener
    public void onLink(LiveEvent event) {
        if (!"LINK_SESSION".equals(event.type()) || !(event.payload() instanceof LinkState.SessionEvent s)) return;
        if ("ENDED".equals(s.status())) {
            long until = System.currentTimeMillis() + LINK_AFTERGLOW;
            linkFast.put(s.a(), until);
            linkFast.put(s.b(), until);
        }
        if (!"STARTED".equals(s.status())) return;
        linkFast.put(s.a(), Long.MAX_VALUE);
        linkFast.put(s.b(), Long.MAX_VALUE);
        linked(s.a(), s.b());
        linked(s.b(), s.a());
    }

    /**
     * Partners recorded only on one side before links were recorded at plug-in time: give the other side theirs
     * (and its link achievements). Runs once at start-up; a no-op when every pair is already mutual.
     */
    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void backfillPartners() {
        for (String userId : repo.ranking()) {
            TrainerLog log = repo.log(userId);
            if (log != null) log.partners().forEach(p -> linked(p, userId));
        }
    }

    private synchronized void linked(String userId, String partner) {
        TrainerLog was = logs.computeIfAbsent(userId, repo::log);
        if (was == null) was = TrainerLog.EMPTY;
        if (was.partners().contains(partner)) return;
        Set<String> partners = new HashSet<>(was.partners());
        partners.add(partner);
        TrainerLog now = new TrainerLog(was.contacts(), was.hatched(), Set.copyOf(partners), was.trades(),
                was.crossVersion(), was.eliteFour(), was.linkWins(), was.linkLosses());
        repo.saveLog(userId, now);
        logs.put(userId, now);
        unlock(userId, Achievements.earnedFromLog(now));
        events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
    }

    /** Unlocks these achievements for the player (those not unlocked yet), keeping their Trainer Score. */
    private void unlock(String userId, List<String> ids) {
        Map<String, Long> had = unlocked.computeIfAbsent(userId, repo::achievements);
        long at = System.currentTimeMillis();
        Map<String, Long> fresh = new LinkedHashMap<>();
        ids.stream().filter(id -> !had.containsKey(id)).forEach(id -> fresh.put(id, at));
        if (fresh.isEmpty()) return;
        Map<String, Long> all = new HashMap<>(had);
        all.putAll(fresh);
        TrainerScore score = trainers.computeIfAbsent(userId, repo::trainer);
        List<ScoreEvent> feed = new ArrayList<>();
        fresh.keySet().forEach(id -> feed.add(new ScoreEvent(userId, "ACHIEVEMENT", name(id), Achievements.points(id), at)));
        repo.rate(userId, score, fresh, rank(score, all), feed);
        unlocked.put(userId, all);
        fresh.keySet().forEach(id -> events.publishEvent(new LiveEvent("ACHIEVEMENT_UNLOCKED", Map.of("user", userId,
                "id", id, "name", name(id), "points", Achievements.points(id), "at", at))));
        feed.forEach(e -> events.publishEvent(new LiveEvent("PROGRESS", e)));
    }

    /** Two Pibs' link battles against each other: wins each, and the newest few (LINK_WIN entries: user won, detail lost). */
    public record Versus(String a, String b, int aWins, int bWins, List<ScoreEvent> recent) {}

    /** a and b's head-to-head, from the LINK_WIN feed entries naming the other (battles before those existed are not in it). */
    public Versus versus(String a, String b) {
        List<ScoreEvent> between = repo.newestOfType("LINK_WIN").stream()
                .filter(e -> e.user().equals(a) && b.equals(e.detail()) || e.user().equals(b) && a.equals(e.detail()))
                .toList();
        int aWins = (int) between.stream().filter(e -> e.user().equals(a)).count();
        return new Versus(a, b, aWins, between.size() - aWins, between.stream().limit(5).toList());
    }

    /**
     * A LINK_WIN feed entry (LINK_WIN points) per link battle the save's record gained since the last read: one entry per
     * battle, the winner's, naming the loser; losses only count in the record (the loser's game reports them too, and
     * two entries for one battle read twice in the feed). detail: the opponent's user id (on the cable now, else the
     * last one there, else their only partner ever), or "" when unknown. At most a few per read, so an old save's first
     * read does not flood the feed.
     */
    private List<ScoreEvent> battles(String userId, TrainerLog was, TrainerLog now, long at) {
        String partner = consoles.linkedWith(userId);
        if (partner == null) partner = lastPartner.get(userId);
        if (partner == null) partner = now.partners().size() == 1 ? now.partners().iterator().next() : "";
        List<ScoreEvent> out = new ArrayList<>();
        for (int i = was.linkWins(); i < Math.min(now.linkWins(), was.linkWins() + 3); i++) {
            out.add(new ScoreEvent(userId, "LINK_WIN", partner, LINK_WIN, at));
        }
        return out;
    }

    /** Updates and stores the player's {@link TrainerLog} with this reading. */
    private TrainerLog log(String userId, Gen2Save before, Gen2Save save) {
        TrainerLog was = logs.computeIfAbsent(userId, repo::log);
        String partner = consoles.linkedWith(userId);
        if (partner != null) lastPartner.put(userId, partner);
        boolean otherVersion = partner != null && !Objects.equals(version(userId), version(partner));
        TrainerLog now = Achievements.log(was, before, save, partner, otherVersion, srams.get(userId));
        if (!now.equals(was)) {
            repo.saveLog(userId, now);
            logs.put(userId, now);
            // The link record is on every scoreboard row: tell everyone when it moves.
            if (!ScoreRow.Link.of(now).equals(ScoreRow.Link.of(was))) events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
        }
        return now;
    }

    /** "Gold", "Silver", "Crystal", or the cartridge id for other games. */
    private String version(String userId) {
        return profile(userId) instanceof PokemonGen2 g ? g.version() : consoles.game(userId);
    }

    /** The pibs:scoreboard score: Trainer Score first, achievement points break ties. Only a tie-break now: scoreboard() ranks by the uncapped total. */
    private static long rank(TrainerScore score, Map<String, Long> unlocked) {
        return score.score() * 10_000L + points(unlocked);
    }

    private static int points(Map<String, Long> unlocked) {
        return unlocked.keySet().stream().mapToInt(Achievements::points).sum();
    }

    private static String name(String id) {
        return Achievements.catalogue().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow().name();
    }

    /** The per-step points of everything awarded: what {@link #score} added up over time. */
    static int points(Progress p) {
        return BADGE * p.badges().size() + CAUGHT * p.caught().size() + SEEN * p.seen().size()
                + LEVEL_MILESTONE * (p.level() / LEVEL_STEP) + (p.hallOfFame() ? HALL_OF_FAME : 0);
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

    /** Drops what this tracker remembers about the player (after their stored progress was deleted). */
    public void forget(String userId) {
        samples.remove(userId);
        summaries.remove(userId);
        awarded.remove(userId);
        unlocked.remove(userId);
        trainers.remove(userId);
        saves.remove(userId);
        cards.remove(userId);
        logs.remove(userId);
        srams.remove(userId);
        linkFast.remove(userId);
        sramSamples.remove(userId);
        sramReadAt.remove(userId);
        events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", scoreboard()));
    }

    /**
     * A player on the town map for the sign-in page: their spot (null before their first reading on a map), their
     * cartridge, whether online, and their party's species (eggs aside) in order.
     */
    public record OnMap(String userId, dev.pppp.model.MapPin pin, dev.pppp.model.MapSpot spot, String game,
                        boolean online, List<Integer> party) {}

    /** Every player who picked a game, where they last were. */
    public List<OnMap> onMap() {
        Set<String> running = consoles.running();
        List<OnMap> out = new ArrayList<>();
        for (String userId : repo.ranking()) {
            TrainerCard c = cards.computeIfAbsent(userId, repo::card);
            // Spots read before indoor ones became the door outside may still be indoors: unknown until the next one.
            var spot = c.spot() != null && art() != null && !art().outdoor(c.spot().map()) ? null : c.spot();
            out.add(new OnMap(userId, c.pin(), spot, consoles.game(userId), running.contains(userId),
                    c.party().stream().filter(m -> !m.egg()).map(dev.pppp.model.PartyMon::species).toList()));
        }
        return out;
    }

    private volatile Gen2MapArt mapArt; // from the uploaded Crystal cartridge, loaded on first use

    /**
     * A map of Crystal as the game draws it (PNG), for the sign-in page's camera: time 0 morning, 1 day, 2 night;
     * margin blocks of the map's border round it. Null without a Crystal cartridge or for an unknown map.
     */
    public byte[] mapArt(String map, int time, int margin) {
        Gen2MapArt art = art();
        return art == null ? null : art.png(map, time, margin);
    }

    /** Neighbours, NPCs, tall grass and wild Pokémon of a Crystal map at a time of day (Gen2MapArt.Info); or null. */
    public Object mapInfo(String map, int time, int margin) {
        Gen2MapArt art = art();
        return art == null ? null : art.info(map, time, margin);
    }

    /** PNG sheet of a Crystal overworld sprite in a palette at a time of day; or null. */
    public byte[] npcSheet(int sprite, int palette, int time) {
        Gen2MapArt art = art();
        return art == null ? null : art.npcSheet(sprite, palette, time);
    }

    private Gen2MapArt art() {
        Gen2MapArt art = mapArt;
        if (art == null) {
            for (var c : cartridges.list()) {
                try {
                    byte[] rom = Files.readAllBytes(cartridges.require(c.id()));
                    if (Gen2MapArt.fits(rom)) art = mapArt = new Gen2MapArt(rom);
                } catch (IOException | RuntimeException e) {
                    log.warn("Could not read {} for the map art", c.id(), e);
                }
                if (art != null) break;
            }
        }
        return art;
    }

    /** PNG sprite of a cartridge's game, by icon id (a Pokédex number), for the public sign-in page; or null. */
    public byte[] iconOf(String cartridgeId, int id) {
        GameProfile<?> profile = profiles.computeIfAbsent(cartridgeId, this::load).orElse(null);
        return profile == null ? null : profile.icon(id);
    }

    /** PNG sheet of a cartridge's player walking around, for the public sign-in page; or null. */
    public byte[] playerSpriteOf(String cartridgeId) {
        GameProfile<?> profile = profiles.computeIfAbsent(cartridgeId, this::load).orElse(null);
        return profile == null ? null : profile.playerSprite();
    }

    /** Pokémon caught by every player, added up (each player's caught species). */
    public int caughtByAll() {
        int n = 0;
        for (String userId : repo.ranking()) n += awarded.computeIfAbsent(userId, repo::progress).caught().size();
        return n;
    }

    /** Every player who picked a game, best Trainer Score first (then achievement points). */
    public List<ScoreRow> scoreboard() {
        Set<String> running = consoles.running();
        List<ScoreRow> rows = new ArrayList<>();
        for (String userId : repo.ranking()) {
            Progress p = awarded.computeIfAbsent(userId, repo::progress);
            TrainerScore t = trainers.computeIfAbsent(userId, repo::trainer);
            Map<String, Long> a = unlocked.computeIfAbsent(userId, repo::achievements);
            TrainerCard c = cards.computeIfAbsent(userId, repo::card);
            TrainerLog log = logs.computeIfAbsent(userId, repo::log);
            int linkPoints = log == null ? 0 : log.linkWins() * LINK_WIN;
            rows.add(new ScoreRow(userId, points(p) + linkPoints, p.badges().size(), p.caught().size(), p.seen().size(),
                    consoles.game(userId), running.contains(userId), t.score(), t.title(), t.grade(), points(a),
                    a.size(), members(c), c.location(), c.playTime(), c.johtoBadges(), c.kantoBadges(), c.trainerName(),
                    ScoreRow.Link.of(log), profile(userId) instanceof PokemonGen2 ? cableClub(c, log) : -1));
        }
        // Ranked by the uncapped total (activity points + achievement points); the capped Trainer Score order of
        // pibs:scoreboard breaks ties (the sort is stable).
        rows.sort(java.util.Comparator.comparingInt((ScoreRow r) -> r.points() + r.achievementPoints()).reversed());
        return rows;
    }

    /** The player's Trainer Score and the achievement catalogue with their unlock times. */
    public PlayerAchievements achievements(String userId) {
        Map<String, Long> a = unlocked.computeIfAbsent(userId, repo::achievements);
        List<Achievement> all = Achievements.catalogue().stream().map(x -> x.seenBy(a.get(x.id()))).toList();
        return new PlayerAchievements(userId, trainers.computeIfAbsent(userId, repo::trainer), points(a), a.size(), all);
    }

    /** {@link #achievements} of another player; NotFoundException when they are not on the scoreboard. */
    public PlayerAchievements achievementsOf(String userId) {
        if (!repo.ranking().contains(userId)) throw new NotFoundException("No player " + userId);
        return achievements(userId);
    }

    /**
     * A player's page: their scoreboard row, party, achievements, stats and newest activity; NotFoundException
     * when they are not on the scoreboard. Progress towards an achievement only where the stored counts give it.
     */
    public PlayerProfile playerProfile(String userId) {
        ScoreRow row = scoreboard().stream().filter(r -> r.userId().equals(userId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No player " + userId));
        TrainerCard card = cards.computeIfAbsent(userId, repo::card);
        TrainerLog log = logs.computeIfAbsent(userId, repo::log);
        Map<String, Long> a = unlocked.computeIfAbsent(userId, repo::achievements);
        List<PlayerProfile.Entry> entries = Achievements.catalogue().stream().map(c -> c.seenBy(a.get(c.id()))).map(x -> {
            Integer goal = x.hidden() ? null : switch (x.id()) { // the counts in Achievements' rules
                case "field-researcher" -> 50;
                case "dex-holder" -> 100;
                case "back-to-kanto" -> 8;
                case "breeder" -> Achievements.BREEDER;
                case "social-butterfly" -> Achievements.SOCIAL_BUTTERFLY;
                default -> null;
            };
            Integer progress = goal == null ? null : Math.min(goal, switch (x.id()) {
                case "back-to-kanto" -> Integer.bitCount(card.kantoBadges());
                case "breeder" -> log.hatched();
                case "social-butterfly" -> log.partners().size();
                default -> row.caught();
            });
            return new PlayerProfile.Entry(x.id(), x.category(), x.name(), x.description(), x.points(), x.rarity(),
                    x.hidden(), x.unlockedAt() != null, progress, goal);
        }).toList();
        return new PlayerProfile(row, card.party(), entries, new PlayerProfile.Stats(row.caught(), row.seen(),
                card.playTime(), log.trades(), log.linkWins(), log.linkLosses(), List.copyOf(log.partners())), repo.newest(userId, 10),
                card.spot());
    }

    /** The activity feed, newest first. */
    public List<ScoreEvent> activity(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1 to 100");
        return repo.newest(limit);
    }

    /**
     * The player's own party box: their last confirmed reading, empty until their game comes into play. Null when
     * they have not picked a game or it has no profile.
     */
    public GameSummary summary(String userId) {
        GameProfile<?> profile = profile(userId);
        if (profile == null) return null;
        GameSummary confirmed = summaries.get(userId);
        return confirmed != null ? confirmed : profile.summary(null);
    }

    /**
     * PNG for an icon id of the player's summary, drawn from their cartridge; for a Pib with no game yet (a spectator),
     * from the first installed cartridge that draws one. Null when none does.
     */
    public byte[] icon(String userId, int id) {
        GameProfile<?> profile = profile(userId);
        if (profile != null) return profile.icon(id);
        for (var c : cartridges.list()) {
            byte[] png = iconOf(c.id(), id);
            if (png != null) return png;
        }
        return null;
    }

    /** PNG of a badge of the player's summary (by its index), drawn from their cartridge, or null. */
    public byte[] badge(String userId, int index) {
        GameProfile<?> profile = profile(userId);
        return profile == null ? null : profile.badge(index);
    }

    /**
     * Pokédex number n as the player's own Pokédex shows it: the more of it they have seen and caught, the more of
     * the ROM's entry comes back. Null when their game has no Pokédex entries.
     */
    public dev.pppp.model.PokedexEntry dex(String userId, int n) {
        GameProfile<?> profile = profile(userId);
        GameProfile.Dex d = profile == null ? null : profile.dex(n);
        if (d == null) return null;
        GameSummary.Collection c = summary(userId).collection();
        // Your own party member is caught by definition (answers before your console's first reading too).
        TrainerCard card = cards.computeIfAbsent(userId, repo::card);
        boolean inParty = card != null && card.party() != null && card.party().stream().anyMatch(m -> !m.egg() && m.species() == n);
        boolean caught = inParty || c != null && c.owned().contains(n), seen = caught || c != null && c.seen().contains(n);
        if (!seen) return new dev.pppp.model.PokedexEntry(n, "unseen", null, null, null, null, null, null, null);
        if (!caught) return new dev.pppp.model.PokedexEntry(n, "seen", d.name(), d.category(), d.types(), null, null, null, null);
        return new dev.pppp.model.PokedexEntry(n, "caught", d.name(), d.category(), d.types(), d.heightFeet(),
                d.heightInches(), d.weightTenths() / 10.0, d.text());
    }

    /** PNG sprite sheet of the player character in the player's game, or null. */
    public byte[] playerSprite(String userId) {
        GameProfile<?> profile = profile(userId);
        return profile == null ? null : profile.playerSprite();
    }

    private static <S> Reading read(GameProfile<S> profile, Ram ram) {
        S state = profile.read(ram);
        return state == null ? null : new Reading(profile.progress(state), profile.summary(state), profile.save(state));
    }

    private GameProfile<?> profile(String userId) {
        String game = consoles.game(userId);
        return game == null ? null : profiles.computeIfAbsent(game, this::load).orElse(null);
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
