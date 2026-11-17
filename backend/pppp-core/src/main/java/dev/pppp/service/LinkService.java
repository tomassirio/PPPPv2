package dev.pppp.service;

import dev.pppp.model.LinkState;
import dev.pppp.model.LinkState.Challenge;
import dev.pppp.model.LinkState.Ready;
import dev.pppp.model.LinkState.SessionEvent;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.LobbyRepository;
import dev.pppp.service.game.PokecenterDig;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Link cable between two players: a lobby of who is ready to trade or battle, a challenge the ready player
 * accepts or declines, and the session that links their consoles until someone unplugs, leaves or time is up.
 * Live events (to every browser; they carry the user ids concerned): LOBBY_CHANGED (the lobby list),
 * LINK_CHALLENGE (LinkState.Challenge) and LINK_SESSION (LinkState.SessionEvent).
 */
@Service
public class LinkService {
    private static final Set<String> KINDS = Set.of("TRADE", "BATTLE");

    /** Non-final so tests need not wait a minute. */
    private Duration challengeTtl = Duration.ofSeconds(60);
    private Duration sessionCap = Duration.ofMinutes(15);

    // ponytail: challenges and sessions live in this JVM behind one lock (every method is synchronized), fine for
    // a single backend pod. More pods: move both to Redis with TTLs and route the link to the pod holding the consoles.
    /** Pending challenges by the challenged player. */
    private final Map<String, Challenge> challenges = new HashMap<>();
    /** Running sessions, one entry per player. */
    private final Map<String, SessionEvent> sessions = new HashMap<>();

    private final LobbyRepository lobby;
    private final PlayerConsoles consoles;
    private final PokecenterDig dig;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public LinkService(LobbyRepository lobby, PlayerConsoles consoles, PokecenterDig dig, ChatService chat,
                       ApplicationEventPublisher events) {
        this.lobby = lobby;
        this.consoles = consoles;
        this.dig = dig;
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
                s == null ? null : new LinkState.Session(s.a().equals(userId) ? s.b() : s.a(), s.kind(), s.endsAt()),
                sessions.values().stream().distinct()
                        .map(e -> new LinkState.Pair(e.a(), e.b(), e.kind(), e.endsAt())).toList(),
                now);
    }

    /** kind TRADE or BATTLE, or null to leave the lobby. */
    public synchronized void ready(String userId, String kind) {
        if (kind == null) {
            if (lobby.remove(userId)) lobbyChanged();
        } else {
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("kind must be TRADE or BATTLE");
            if (sessions.containsKey(userId)) throw new IllegalStateException("You are already linked");
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
        // Either side may have been on another page: both consoles wake up and both browsers go to /play.
        // ponytail: a console woken here with nobody watching only parks when a screen socket closes; add an idle
        // timeout if Pibs accept and never open /play.
        consoles.open(c.from());
        consoles.open(userId);
        for (String id : List.of(userId, c.from())) {
            String why = dig.blocker(id);
            if (why != null) {
                throw new IllegalStateException((id.equals(userId) ? "You can't" : name(id) + " can't")
                        + " leave right now: " + why + ". Try again in a moment.");
            }
        }
        dig.dig(c.from());
        dig.dig(userId);
        consoles.link(c.from(), userId);
        close(c, "ACCEPTED");
        for (String id : List.of(c.from(), userId)) { // both are busy now: out of the lobby, other challenges off
            lobby.remove(id);
            List.copyOf(challenges.values()).stream()
                    .filter(o -> o.from().equals(id) || o.to().equals(id))
                    .forEach(o -> close(o, "CANCELLED"));
        }
        lobbyChanged();
        var s = new SessionEvent(c.from(), userId, c.kind(), System.currentTimeMillis() + sessionCap.toMillis(), "STARTED", null);
        sessions.put(s.a(), s);
        sessions.put(s.b(), s);
        events.publishEvent(new LiveEvent("LINK_SESSION", s));
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

    /** Drops challenges nobody answered in time and sessions past the cap. */
    @Scheduled(fixedDelay = 1000)
    public synchronized void sweep() {
        long now = System.currentTimeMillis();
        List.copyOf(challenges.values()).stream().filter(c -> c.expiresAt() <= now).forEach(c -> close(c, "EXPIRED"));
        List.copyOf(sessions.values()).stream().filter(s -> s.endsAt() <= now).forEach(s -> end(s.a(), "TIME_UP"));
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
        consoles.unlink(userId); // parks both consoles
        events.publishEvent(new LiveEvent("LINK_SESSION", new SessionEvent(s.a(), s.b(), s.kind(), s.endsAt(), "ENDED", reason)));
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

    private static String name(String userId) {
        return userId.split("@")[0];
    }
}
