package dev.pppp.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.LinkState.SessionEvent;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.Ghosts;
import dev.pppp.service.game.NoteService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws/screen: the signed-in player's own console, every second emulated frame as a PNG binary message.
 * Connecting wakes the console; closing the player's last socket leaves it running (PlayerConsoles.leave: it pauses
 * after an hour untouched). Each socket has a one-frame
 * handoff, so a slow client just skips frames; nothing here blocks an emulator thread.
 * Before a frame whose players changed (and to a socket that just connected) goes a text message,
 * {"ghosts": [{"user", "x", "y"}]}: the player (first) and each ghost's sprite top left in screen pixels, for the
 * browser's name labels and chat bubbles (Ghosts.onScreen); and "notes": the notes in view (NoteService.visible).
 * The handshake goes through the normal security chain (ROLE_PLAYER), so the principal is the Authentication.
 */
@Component
public class ScreenWebSocketHandler extends AbstractWebSocketHandler {
    /** Closing code for a player who has not picked a game yet. */
    static final CloseStatus NO_GAME = new CloseStatus(4409, "Pick a game first");
    /** Closing code when the console stopped under an open tab (nobody touched it, an admin closed it or reset the
     * run; PlayerConsoles.onAway): the page says so and waits for Continue instead of reconnecting. */
    static final CloseStatus AWAY = new CloseStatus(4408, "Paused while you were away");

    private record Frame(int[] pixels, List<Ghosts.OnScreen> ghosts, List<NoteService.Shown> notes) {}

    /** players: the frame's players as the text message; a socket sends it only when it differs from its last. */
    private record Out(String players, byte[] png) {}

    private record Viewer(Handoff<Frame> encoder, Map<WebSocketSession, Handoff<Out>> sockets) {}

    private final Map<String, Viewer> viewers = new ConcurrentHashMap<>();
    private final PlayerConsoles consoles;
    private final ObjectMapper json;

    ScreenWebSocketHandler(PlayerConsoles consoles, Ghosts ghosts, NoteService notes, ObjectMapper json) {
        this.consoles = consoles;
        this.json = json;
        consoles.onFrame((userId, pixels) -> {
            Viewer v = viewers.get(userId);
            if (v != null) v.encoder().offer(new Frame(pixels, ghosts.onScreen(userId), notes.visible(userId)));
        });
        // Shut down for inactivity: the player's screens close with AWAY, so the page says so instead of reconnecting.
        // The console stopped (paused and parked): its screens close with AWAY. Removing the viewer happens in a
        // compute on its key, as adding a socket does (afterConnectionEstablished), so a socket is either closed here
        // or lands in a fresh viewer; a console resumed meanwhile (someone just opened it) keeps its screens. No lock
        // of ours: this runs inside idle(), also from LinkService while it holds its own lock. The closes run on a
        // virtual thread so a slow client cannot hold anyone up.
        consoles.onAway(userId -> {
            List<WebSocketSession> closing = new java.util.ArrayList<>();
            viewers.computeIfPresent(userId, (id, v) -> {
                if (consoles.isAwake(id)) return v;
                closing.addAll(v.sockets().keySet());
                v.sockets().clear();
                return null;
            });
            if (!closing.isEmpty()) Thread.ofVirtual().start(() -> closing.forEach(s -> close(s, AWAY)));
        });
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) {
        String userId = userId(session);
        String[] sent = {null}; // the players this socket last got; only its handoff's thread touches it
        viewers.compute(userId, (id, v) -> {
            if (v == null) {
                Map<WebSocketSession, Handoff<Out>> sockets = new ConcurrentHashMap<>();
                v = new Viewer(new Handoff<>(1, frame -> {
                    Out out = new Out(ghostsJson(frame.ghosts(), frame.notes()), FrameEncoder.png(frame.pixels()));
                    sockets.values().forEach(slot -> slot.offer(out));
                }), sockets);
            }
            v.sockets().put(session, new Handoff<>(1, out -> send(session, out, sent)));
            return v;
        });
        try {
            consoles.open(userId);
        } catch (RuntimeException e) { // no game picked, or its ROM is gone
            close(session, NO_GAME);
            return;
        }
        // The console went idle between registering and opening (onAway took this socket, closing it with AWAY):
        // open() just resumed it; with no screen left it simply keeps running, as after any leave.
        Viewer v = viewers.get(userId);
        if (v == null || !v.sockets().containsKey(session)) {
            if (!viewers.containsKey(userId) && consoles.linkedWith(userId) == null) consoles.leave(userId);
        }
    }

    private String ghostsJson(List<Ghosts.OnScreen> ghosts, List<NoteService.Shown> notes) {
        try {
            return json.writeValueAsString(Map.of("ghosts", ghosts, "notes", notes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void send(WebSocketSession session, Out out, String[] sent) {
        if (!session.isOpen()) return;
        try {
            if (!out.players().equals(sent[0])) {
                session.sendMessage(new TextMessage(out.players()));
                sent[0] = out.players();
            }
            session.sendMessage(new BinaryMessage(out.png()));
        } catch (IOException | IllegalStateException e) {
            close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = userId(session);
        boolean[] last = {false};
        viewers.computeIfPresent(userId, (id, v) -> {
            if (v.sockets().remove(session) == null || !v.sockets().isEmpty()) return v;
            last[0] = true;
            return null;
        });
        if (!last[0]) return;
        // A linked player who went to another tab keeps their console running and the cable in: the partner's
        // game runs in step with it, until the session ends (below).
        // A closed browser: LinkService ends the session after a minute with no events socket.
        if (consoles.linkedWith(userId) == null) consoles.leave(userId);
    }

    /** A link session ended: whoever of the pair is not watching their screen has left (still running). No lock of ours. */
    @EventListener
    public void onLinkEnded(LiveEvent event) {
        if (!(event.payload() instanceof SessionEvent s) || !"ENDED".equals(s.status())) return;
        for (String id : List.of(s.a(), s.b())) {
            if (!viewers.containsKey(id)) consoles.leave(id);
        }
    }

    @EventListener
    public void onUserRevoked(UserRevokedEvent event) {
        closeSessionsOf(viewers.values().stream().flatMap(v -> v.sockets().keySet().stream()).toList(), event.userId());
    }

    static String userId(WebSocketSession session) {
        return CurrentUser.id((Authentication) session.getPrincipal());
    }

    static void closeSessionsOf(java.util.Collection<WebSocketSession> sessions, String userId) {
        sessions.stream()
                .filter(s -> s.getPrincipal() instanceof Authentication auth && CurrentUser.id(auth).equals(userId))
                .forEach(s -> close(s, CloseStatus.POLICY_VIOLATION));
    }

    static void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // already gone
        }
    }

    /** Open /ws/screen sockets (metrics). */
    public int sockets() {
        return viewers.values().stream().mapToInt(v -> v.sockets().size()).sum();
    }
}
