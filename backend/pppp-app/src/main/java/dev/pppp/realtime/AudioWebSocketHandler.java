package dev.pppp.realtime;

import dev.pppp.service.PlayerConsoles;
import dev.pppp.model.UserRevokedEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws/audio: the signed-in player's own console. One text header {"sampleRate":N,"channels":1,"format":"s16le"}, then a binary message of
 * mono s16le PCM per emulated frame (~369 samples, ~740 bytes, ~44 KB/s). Each socket keeps at most
 * MAX_PENDING chunks; a lagging client loses its oldest ones. Same security as /ws/screen.
 */
@Component
public class AudioWebSocketHandler extends AbstractWebSocketHandler {

    /** ~100 ms: anything later is better dropped than played late. */
    private static final int MAX_PENDING = 6;

    private final Map<WebSocketSession, Handoff<byte[]>> sessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> users = new ConcurrentHashMap<>();
    private final String header;

    AudioWebSocketHandler(PlayerConsoles host) {
        header = "{\"sampleRate\":" + host.audioSampleRate() + ",\"channels\":1,\"format\":\"s16le\"}";
        host.onAudio(this::broadcast);
    }

    /** Emulator thread: encode once, hand off to every socket, never block. */
    private void broadcast(String userId, short[] samples) {
        if (!users.containsValue(userId)) return;
        ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asShortBuffer().put(samples);
        byte[] chunk = bytes.array();
        sessions.forEach((session, h) -> {
            if (userId.equals(users.get(session))) h.offer(chunk);
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        session.sendMessage(new TextMessage(header)); // before registering, so no concurrent send
        users.put(session, ScreenWebSocketHandler.userId(session));
        sessions.put(session, new Handoff<>(MAX_PENDING, chunk -> send(session, chunk)));
    }

    private void send(WebSocketSession session, byte[] chunk) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new BinaryMessage(chunk));
        } catch (IOException | IllegalStateException e) {
            ScreenWebSocketHandler.close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        users.remove(session);
    }

    @EventListener
    public void onUserRevoked(UserRevokedEvent event) {
        ScreenWebSocketHandler.closeSessionsOf(sessions.keySet(), event.userId());
    }

    /** Open /ws/audio sockets (metrics). */
    public int sockets() {
        return sessions.size();
    }
}
