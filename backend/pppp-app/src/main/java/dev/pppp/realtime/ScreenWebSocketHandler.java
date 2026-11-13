package dev.pppp.realtime;

import dev.pppp.model.UserRevokedEvent;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws/screen: the signed-in player's own console, every second emulated frame as a PNG binary message.
 * Connecting wakes the console; closing the player's last socket pauses it. Each socket has a one-frame
 * handoff, so a slow client just skips frames; nothing here blocks an emulator thread.
 * The handshake goes through the normal security chain (ROLE_PLAYER), so the principal is the Authentication.
 */
@Component
public class ScreenWebSocketHandler extends AbstractWebSocketHandler {
    /** Closing code for a player who has not picked a game yet. */
    static final CloseStatus NO_GAME = new CloseStatus(4409, "Pick a game first");

    private record Viewer(Handoff<int[]> encoder, Map<WebSocketSession, Handoff<byte[]>> sockets) {}

    private final Map<String, Viewer> viewers = new ConcurrentHashMap<>();
    private final PlayerConsoles consoles;

    ScreenWebSocketHandler(PlayerConsoles consoles) {
        this.consoles = consoles;
        consoles.onFrame((userId, pixels) -> {
            Viewer v = viewers.get(userId);
            if (v != null) v.encoder().offer(pixels);
        });
    }

    /** v1 showed a delayed public screenshot of the shared game; screens are private now, so there is none. */
    public byte[] latest() {
        return null;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) {
        String userId = userId(session);
        Viewer v = viewers.computeIfAbsent(userId, id -> {
            Map<WebSocketSession, Handoff<byte[]>> sockets = new ConcurrentHashMap<>();
            return new Viewer(new Handoff<>(1, pixels -> {
                byte[] png = FrameEncoder.png(pixels);
                sockets.values().forEach(slot -> slot.offer(png));
            }), sockets);
        });
        v.sockets().put(session, new Handoff<>(1, png -> send(session, png)));
        try {
            consoles.open(userId);
        } catch (RuntimeException e) { // no game picked, or its ROM is gone
            close(session, NO_GAME);
        }
    }

    private void send(WebSocketSession session, byte[] png) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new BinaryMessage(png));
        } catch (IOException | IllegalStateException e) {
            close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = userId(session);
        Viewer v = viewers.get(userId);
        if (v == null || v.sockets().remove(session) == null || !v.sockets().isEmpty()) return;
        viewers.remove(userId);
        consoles.idle(userId);
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
}
