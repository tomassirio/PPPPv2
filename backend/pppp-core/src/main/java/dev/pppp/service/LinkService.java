package dev.pppp.service;

import dev.pppp.model.LinkState;
import dev.pppp.model.LinkState.Challenge;
import dev.pppp.model.LinkState.Ready;
import dev.pppp.model.LinkState.SessionEvent;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.LobbyRepository;
import dev.pppp.service.game.PokecenterDig;
import dev.pppp.service.game.ProgressTracker;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Link cable between two players: a lobby of who is ready to trade or battle, a challenge the ready player
 * accepts or declines, and the session that links their consoles until someone unplugs or leaves (no page open for a minute).
 * Live events (to every browser; they carry the user ids concerned): LOBBY_CHANGED (the lobby list),
 * LINK_CHALLENGE (LinkState.Challenge) and LINK_SESSION (LinkState.SessionEvent).
 */
@Service
public class LinkService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LinkService.class);
    /** How often a running session is logged (rooms, dialogs, speed, cable traffic): what a stuck link needs. */
    private static final long DIAGNOSE_MS = 10_000;
    private final Map<String, Long> bits = new HashMap<>();
    private long diagnosed;
    private static final Set<String> KINDS = Set.of("TRADE", "BATTLE");

    /** Non-final so tests need not wait a minute. */
    private Duration challengeTtl = Duration.ofSeconds(60);
    /** How long a linked player may have no page open (a closed browser) before their session ends. */
    private Duration awayLimit = Duration.ofSeconds(60);
    /** How long a player may stand in a link room with no cable before their Game Boy is reset. */
    private Duration strandedLimit = Duration.ofSeconds(60);
    /** How long after accepting the dig may take before A is no longer pressed for the player. */
    private static final Duration TALK_WITHIN = Duration.ofSeconds(20);
    /** Whether the player has a page open; the app plugs in the events socket's presence. */
    private Predicate<String> online = id -> true;

    // ponytail: challenges and sessions live in this JVM behind one lock (every method is synchronized), fine for
    // a single backend pod. More pods: move both to Redis with TTLs and route the link to the pod holding the consoles.
    /** Pending challenges by the challenged player. */
    private final Map<String, Challenge> challenges = new HashMap<>();
    /** Running sessions, one entry per player. */
    private final Map<String, SessionEvent> sessions = new HashMap<>();
    /** Linked players standing in a link room that does not fit their session's kind: userId -> room. */
    private final Map<String, String> wrongRoom = new HashMap<>();
    /** Linked players who have been in the link room that fits their session: leaving it ends the session. */
    private final Set<String> visited = new java.util.HashSet<>();
    /** Linked players about to talk to their receptionist: userId -> give up after (epoch ms). */
    private final Map<String, Long> talk = new HashMap<>();
    /** Players in a link room with no cable plugged in (it came out mid-battle): userId -> since when. */
    private final Map<String, Long> stranded = new HashMap<>();
    /** Linked players with no page open: userId -> since when. */
    private final Map<String, Long> away = new HashMap<>();

    private final LobbyRepository lobby;
    private final PlayerConsoles consoles;
    private final PokecenterDig dig;
    private final ProgressTracker tracker;
    private final AllowlistService names;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public LinkService(LobbyRepository lobby, PlayerConsoles consoles, PokecenterDig dig, ProgressTracker tracker,
                       AllowlistService names, ChatService chat, ApplicationEventPublisher events) {
        this.names = names;
        this.lobby = lobby;
        this.consoles = consoles;
        this.dig = dig;
        this.tracker = tracker;
        this.chat = chat;
        this.events = events;
        lobby.clear(); // no console survives a restart, so nobody is ready
        consoles.onIdle(this::left);
    }

    public synchronized LinkState state(String userId) {
        long now = System.currentTimeMillis();
        Challenge in = challenges.get(userId);
        Challenge out = challenges.values().stream().filter(c -> c.from().equals(userId)).findFirst().orElse(null);
        SessionEvent s = sessions.get(userId);
        return new LinkState(lobbyList(), lobby.kind(userId),
                in != null && in.expiresAt() > now ? in : null,
                out != null && out.expiresAt() > now ? out : null,
                s == null ? null : session(userId, s),
                sessions.values().stream().distinct()
                        .map(e -> new LinkState.Pair(e.a(), e.b(), e.kind(), e.since())).toList(),
                now);
    }

    private LinkState.Session session(String userId, SessionEvent s) {
        String peer = s.a().equals(userId) ? s.b() : s.a();
        return new LinkState.Session(peer, s.kind(), s.since(), wrongRoom.get(userId), wrongRoom.get(peer));
    }

    /** Tells the service who has a page open (the app's events socket). */
    public void presence(Predicate<String> online) {
        this.online = online;
    }

    /** kind TRADE or BATTLE, or null to leave the lobby. */
    public synchronized void ready(String userId, String kind) {
        if (kind == null) {
            if (lobby.remove(userId)) lobbyChanged();
        } else {
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("kind must be TRADE or BATTLE");
            if (sessions.containsKey(userId)) throw new IllegalStateException("You are already linked");
            requireCableClub(userId, userId);
            if (!consoles.isAwake(userId)) throw new IllegalStateException("Open your Game Boy first");
            lobby.put(userId, kind);
            lobbyChanged();
        }
        // whoever challenged them asked for what they were ready for before
        Challenge c = challenges.get(userId);
        if (c != null && !c.kind().equals(kind)) close(c, "CANCELLED");
    }

    /**
     * Offers a trade or a battle to one Pib, whether or not they are in the lobby. kind: TRADE or BATTLE, or null to
     * take what they are ready for. Either console may be asleep: accepting wakes both.
     */
    public synchronized void challenge(String from, String to, String kind) {
        if (to == null || from.equals(to)) throw new IllegalArgumentException("Challenge someone else");
        sweep();
        if (kind == null) kind = lobby.kind(to);
        if (kind == null) throw new IllegalArgumentException("Pick a trade or a battle");
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("kind must be TRADE or BATTLE");
        if (consoles.game(to) == null) throw new IllegalStateException(name(to) + " has not picked a game yet");
        requireCableClub(from, from);
        requireCableClub(from, to);
        if (sessions.containsKey(from)) throw new IllegalStateException("You are already linked");
        if (sessions.containsKey(to)) throw new IllegalStateException(name(to) + " is linked with someone else");
        if (challenges.containsKey(to)) throw new IllegalStateException(name(to) + " already has a challenge waiting");
        if (challenges.values().stream().anyMatch(c -> c.from().equals(from))) {
            throw new IllegalStateException("You already challenged someone");
        }
        Challenge c = new Challenge(from, to, kind, System.currentTimeMillis() + challengeTtl.toMillis(), "PENDING");
        challenges.put(to, c);
        events.publishEvent(new LiveEvent("LINK_CHALLENGE", c));
    }

    /**
     * Links the two consoles, after sending both players digging to their Pokémon Center (PokecenterDig).
     * IllegalStateException when no challenge is waiting, a console is gone, or a player cannot leave where they are
     * (the challenge stays, to accept again once they can).
     */
    public synchronized void accept(String userId) {
        sweep();
        Challenge c = challenges.get(userId);
        if (c == null) throw new IllegalStateException("No challenge waiting");
        requireCableClub(userId, userId);
        requireCableClub(userId, c.from());
        // Either side may have been on another page: both consoles wake up and both browsers go to /play.
        // A console woken here with nobody watching runs until the session ends (then it parks and shuts down after
        // PlayerConsoles' grace period).
        consoles.open(c.from());
        consoles.open(userId);
        for (String id : List.of(userId, c.from())) {
            String why = dig.blocker(id);
            if (why != null) {
                throw new IllegalStateException((id.equals(userId) ? "You can't" : name(id) + " can't")
                        + " leave right now: " + why + ". Try again in a moment.");
            }
        }
        dig.dig(c.from(), c.kind());
        dig.dig(userId, c.kind());
        consoles.link(c.from(), userId);
        // Arrows locked until they are in the link room; A pressed for them once the dig has them facing her.
        for (String id : List.of(c.from(), userId)) {
            consoles.pin(id, true);
            talk.put(id, System.currentTimeMillis() + TALK_WITHIN.toMillis());
        }
        close(c, "ACCEPTED");
        for (String id : List.of(c.from(), userId)) { // both are busy now: out of the lobby, other challenges off
            lobby.remove(id);
            List.copyOf(challenges.values()).stream()
                    .filter(o -> o.from().equals(id) || o.to().equals(id))
                    .forEach(o -> close(o, "CANCELLED"));
        }
        lobbyChanged();
        var s = new SessionEvent(c.from(), userId, c.kind(), System.currentTimeMillis(), "STARTED", null);
        sessions.put(s.a(), s);
        sessions.put(s.b(), s);
        events.publishEvent(new LiveEvent("LINK_SESSION", s));
        log.info("Link {} started: {} and {}", s.kind(), s.a(), s.b());
        chat.postSystem(name(s.a()) + " and " + name(s.b()) + " plugged in a link cable to "
                + ("TRADE".equals(s.kind()) ? "trade" : "battle"));
    }

    /** No-op when nothing is waiting. */
    public synchronized void decline(String userId) {
        Challenge c = challenges.get(userId);
        if (c != null) close(c, "DECLINED");
    }

    /** Pulls the player's cable. No-op when they are not linked. */
    public synchronized void unplug(String userId) {
        end(userId, "UNPLUGGED");
    }

    /**
     * Drops challenges nobody answered in time, ends the session of a player who has had no page open for awayLimit
     * (sessions have no time cap: a battle must not be cut off), and tells a pair when one of them walks into the
     * wrong link room (LINK_SESSION status ROOM): the trade room for a battle, the Colosseum for a trade. A player who
     * was in the right room and is back upstairs is done: the cable comes out (ENDED, FINISHED). A wrong room, left
     * as the warning says, does not count.
     */
    @Scheduled(fixedDelay = 1000)
    public synchronized void sweep() {
        long now = System.currentTimeMillis();
        List.copyOf(challenges.values()).stream().filter(c -> c.expiresAt() <= now).forEach(c -> close(c, "EXPIRED"));
        for (String id : List.copyOf(sessions.keySet())) {
            if (online.test(id)) away.remove(id);
            else if (now - away.computeIfAbsent(id, k -> now) >= awayLimit.toMillis()) end(id, "LEFT");
        }
        // A cable that came out mid-battle (a restart, a crash) leaves both games waiting forever in the link room, as
        // a real Game Boy would: after strandedLimit their console is reset, back to the save the receptionist made.
        for (String id : consoles.running()) {
            if (sessions.containsKey(id) || !consoles.isAwake(id)) {
                stranded.remove(id);
                continue;
            }
            String map = dig.map(id);
            if (map == null) continue; // cannot tell this second (another WRAM bank is in)
            if (!PokecenterDig.LINK_ROOMS.contains(map)) {
                stranded.remove(id);
            } else if (now - stranded.computeIfAbsent(id, k -> now) >= strandedLimit.toMillis()) {
                stranded.remove(id);
                try {
                    consoles.reset(id);
                } catch (IllegalStateException e) {
                    continue; // shut down meanwhile (PlayerConsoles.sweep): nothing left to reset
                }
                chat.postSystem(name(id) + "'s Game Boy was reset: the link cable came out mid-link, so it was stuck waiting");
            }
        }
        for (var t : List.copyOf(talk.entrySet())) {
            SessionEvent s = sessions.get(t.getKey());
            if (s == null || t.getValue() < now) talk.remove(t.getKey());
            else if (dig.atReceptionist(t.getKey(), s.kind())) {
                talk.remove(t.getKey());
                try {
                    consoles.press(t.getKey(), "a", 1);
                } catch (IllegalStateException e) {
                    // console gone meanwhile; the session sweep below ends what needs ending
                }
            }
        }
        if (now - diagnosed >= DIAGNOSE_MS) {
            diagnosed = now;
            for (SessionEvent s : sessions.values().stream().distinct().toList()) {
                try {
                    diagnose(s);
                } catch (RuntimeException e) {
                    log.info("Link {} {} and {}: no diagnosis ({})", s.kind(), s.a(), s.b(), e.getMessage());
                }
            }
        }
        for (SessionEvent s : sessions.values().stream().distinct().toList()) {
            boolean changed = false, finished = false;
            for (String id : List.of(s.a(), s.b())) {
                String map = dig.map(id);
                if (map == null) continue; // cannot tell this second
                String room = PokecenterDig.LINK_ROOMS.contains(map) ? map : null;
                if (room != null && fits(s.kind(), room) && visited.add(id)) consoles.pin(id, false);
                else if (room == null && visited.contains(id)) finished = true;
                String wrong = room == null || fits(s.kind(), room) ? null : room;
                if (java.util.Objects.equals(wrong, wrongRoom.get(id))) continue;
                if (wrong == null) wrongRoom.remove(id); else wrongRoom.put(id, wrong);
                changed = true;
            }
            if (finished) {
                end(s.a(), "FINISHED");
            } else if (changed) {
                events.publishEvent(new LiveEvent("LINK_SESSION", new SessionEvent(s.a(), s.b(), s.kind(), s.since(), "ROOM", null)));
            }
        }
    }

    /** One log line per session: each end's room, what holds them (a dialog, a step), speed, cable bits since last. */
    private void diagnose(SessionEvent s) {
        StringBuilder line = new StringBuilder("Link " + s.kind() + " " + (now() - s.since()) / 1000 + "s:");
        for (String id : List.of(s.a(), s.b())) {
            long b = consoles.serialBits(id), before = bits.getOrDefault(id, b);
            bits.put(id, b);
            Double speed = consoles.measuredSpeed(id);
            line.append(String.format(" %s in %s (%s, %.2fx, +%d bits);", id, dig.map(id), java.util.Objects.requireNonNullElse(dig.blocker(id), "free"),
                    speed == null ? 0 : speed, b - before));
        }
        log.info(line.toString());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** The Time Capsule trades too (with Red, Blue and Yellow). */
    private static boolean fits(String kind, String room) {
        return "BATTLE".equals(kind) ? room.equals("COLOSSEUM") : !room.equals("COLOSSEUM");
    }

    /** The player's console went idle: out of the lobby, their challenges off, their session over. */
    private synchronized void left(String userId) {
        if (lobby.remove(userId)) lobbyChanged();
        List.copyOf(challenges.values()).stream()
                .filter(c -> c.from().equals(userId) || c.to().equals(userId))
                .forEach(c -> close(c, "CANCELLED"));
        end(userId, "LEFT");
    }

    private void end(String userId, String reason) {
        SessionEvent s = sessions.remove(userId);
        if (s == null) return;
        sessions.remove(s.a().equals(userId) ? s.b() : s.a());
        wrongRoom.remove(s.a());
        wrongRoom.remove(s.b());
        away.remove(s.a());
        away.remove(s.b());
        visited.remove(s.a());
        visited.remove(s.b());
        talk.remove(s.a());
        talk.remove(s.b());
        bits.remove(s.a());
        bits.remove(s.b());
        log.info("Link {} {} and {} ended: {}", s.kind(), s.a(), s.b(), reason);
        consoles.pin(s.a(), false);
        consoles.pin(s.b(), false);
        consoles.unlink(userId); // parks both consoles
        events.publishEvent(new LiveEvent("LINK_SESSION", new SessionEvent(s.a(), s.b(), s.kind(), s.since(), "ENDED", reason)));
    }

    private void close(Challenge c, String status) {
        challenges.remove(c.to(), c);
        events.publishEvent(new LiveEvent("LINK_CHALLENGE", new Challenge(c.from(), c.to(), c.kind(), c.expiresAt(), status)));
    }

    private void lobbyChanged() {
        events.publishEvent(new LiveEvent("LOBBY_CHANGED", lobbyList()));
    }

    private List<Ready> lobbyList() {
        return lobby.all().entrySet().stream().map(e -> new Ready(e.getKey(), e.getValue())).toList();
    }

    /**
     * The Cable Club opens once the player gave Elm the Mystery Egg (ProgressTracker.cableClubOpen); before that the
     * receptionists turn them away, so no link. asking: who is told.
     */
    private void requireCableClub(String asking, String userId) {
        if (tracker.cableClubOpen(userId)) return;
        if (ProgressTracker.LOCKED.equals(tracker.cableClubMode(userId))) {
            throw new IllegalStateException(asking.equals(userId) ? "An admin has closed your Cable Club for now"
                    : name(userId) + "'s Cable Club is closed for now");
        }
        throw new IllegalStateException(asking.equals(userId)
                ? "Your Cable Club isn't open yet: bring Mr. Pokémon's Mystery Egg back to Prof. Elm first"
                : name(userId) + "'s Cable Club isn't open yet");
    }

    /** How others see the player (their nickname, see AllowlistService.displayName). */
    private String name(String userId) {
        return names.displayName(userId);
    }
}
