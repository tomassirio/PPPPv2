package dev.pppp.realtime;

import dev.pppp.model.LinkState.SessionEvent;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws/watch/{userId}: another player's screen, for spectators of a link battle or trade. Only while that player is
 * linked: otherwise, and when their session ends, the socket closes with NOT_LINKED. Read only: it never wakes,
 * pauses or parks the console, and anything a spectator sends is ignored (their buttons only ever reach their own
 * console). PNG frames as in /ws/screen, without the ghosts message and without sound.
 */
@Component
public class WatchWebSocketHandler extends AbstractWebSocketHandler {
    /** Closing code when the watched player is not (or no longer) linked. */
    static final CloseStatus NOT_LINKED = new CloseStatus(4404, "Not in a link session");

    private record Watched(Handoff<int[]> encoder, Map<WebSocketSession, Handoff<byte[]>> sockets) {}

    /** By the watched player's id. */
    private final Map<String, Watched> watched = new ConcurrentHashMap<>();
    private final PlayerConsoles consoles;

    WatchWebSocketHandler(PlayerConsoles consoles) {
        this.consoles = consoles;
        consoles.onFrame((userId, pixels) -> {
            Watched w = watched.get(userId);
            if (w != null) w.encoder().offer(pixels);
        });
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) {
        String target = target(session);
        if (target == null || consoles.linkedWith(target) == null) {
            ScreenWebSocketHandler.close(session, NOT_LINKED);
            return;
        }
        Watched w = watched.computeIfAbsent(target, id -> {
            Map<WebSocketSession, Handoff<byte[]>> sockets = new ConcurrentHashMap<>();
            return new Watched(new Handoff<>(1, pixels -> {
                byte[] png = FrameEncoder.png(pixels);
                sockets.values().forEach(slot -> slot.offer(png));
            }), sockets);
        });
        w.sockets().put(session, new Handoff<>(1, png -> send(session, png)));
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String target = target(session);
        Watched w = target == null ? null : watched.get(target);
        if (w == null) return;
        w.sockets().remove(session);
        if (w.sockets().isEmpty()) watched.remove(target);
    }

    /** The cable came out: everyone watching either player is told so. */
    @EventListener
    public void onLinkEnded(LiveEvent event) {
        if (!(event.payload() instanceof SessionEvent s) || !"ENDED".equals(s.status())) return;
        for (String id : List.of(s.a(), s.b())) {
            Watched w = watched.get(id);
            if (w != null) List.copyOf(w.sockets().keySet()).forEach(ws -> ScreenWebSocketHandler.close(ws, NOT_LINKED));
        }
    }

    @EventListener
    public void onUserRevoked(UserRevokedEvent event) {
        ScreenWebSocketHandler.closeSessionsOf(
                watched.values().stream().flatMap(w -> w.sockets().keySet().stream()).toList(), event.userId());
    }

    /** The watched player's id: the last path segment, lower-cased as user ids are. */
    static String target(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getRawPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.isEmpty() ? null : URLDecoder.decode(last, StandardCharsets.UTF_8).toLowerCase();
    }

    private static void send(WebSocketSession session, byte[] png) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new BinaryMessage(png));
        } catch (IOException | IllegalStateException e) {
            ScreenWebSocketHandler.close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }
}
