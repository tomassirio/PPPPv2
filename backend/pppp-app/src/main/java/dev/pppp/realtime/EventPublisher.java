package dev.pppp.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.Viewer;
import dev.pppp.security.CurrentUser;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * /ws/events: everything that changes for everyone while people play, pushed to every open tab as one JSON text
 * message per event, {"type": ..., "data": ...}. Types: CHAT_MESSAGE, CHAT_DELETED, CHAT_REACTION, CHAT_CLEARED,
 * GAME_STATE, PROGRESS, ACHIEVEMENT_UNLOCKED, SCOREBOARD_CHANGED, LOBBY_CHANGED, LINK_CHALLENGE, LINK_SESSION,
 * PLAYER_RESET, ACCESS_REQUESTS, PRESENCE; GOD_MESSAGE to one player only. Server to browser only; anything the browser sends is ignored.
 *
 * <p>Presence = who has a socket open, kept in memory (single instance). PRESENCE carries the list of viewers,
 * one entry per user however many tabs they have open.
 */
@Component
public class EventPublisher extends TextWebSocketHandler {
    /** sessionId: the login session that opened the socket (null in tests); it closes when that session ends. */
    private record Stream(WebSocketSession socket, Viewer viewer, boolean admin, String sessionId) {}
    private record Message(String type, Object data) {}
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final Map<WebSocketSession, Stream> streams = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public EventPublisher(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var auth = (Authentication) session.getPrincipal();
        var sessionId = (String) session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        // Sends come from many threads; a client that falls 5 s or 1 MB behind is dropped and reconnects.
        var socket = new ConcurrentWebSocketSessionDecorator(session, 5_000, 1 << 20);
        streams.put(session, new Stream(socket, CurrentUser.viewer(auth), CurrentUser.isAdmin(auth), sessionId));
        log.debug("Events socket opened for {} ({} open)", CurrentUser.id(auth), streams.size());
        publish("PRESENCE", viewers());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        drop(session);
    }

    /**
     * One entry per user id, sorted by name. The same Pib can have sockets whose names differ (a Google login with
     * their full name and a dev login with only the id): the first one wins, never two entries for one id.
     */
    public List<Viewer> viewers() {
        Map<String, Viewer> byId = new java.util.LinkedHashMap<>();
        streams.values().forEach(s -> byId.putIfAbsent(s.viewer().id(), s.viewer()));
        return byId.values().stream()
                .sorted(Comparator.comparing(Viewer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Whether the player has any tab open. */
    public boolean isOnline(String userId) {
        return streams.values().stream().anyMatch(s -> s.viewer().id().equals(userId));
    }

    public void publish(String type, Object data) {
        send(type, data, s -> true);
    }

    /** Only to admins' sockets (ADMIN chat lines). */
    public void publishToAdmins(String type, Object data) {
        send(type, data, Stream::admin);
    }

    /** Only to one player's sockets (an admin's god message). False when they have no tab open. */
    public boolean publishTo(String userId, String type, Object data) {
        if (!isOnline(userId)) return false;
        send(type, data, s -> s.viewer().id().equals(userId));
        return true;
    }

    private void send(String type, Object data, java.util.function.Predicate<Stream> to) {
        TextMessage message;
        try {
            message = new TextMessage(json.writeValueAsString(new Message(type, data)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + type, e);
        }
        streams.forEach((session, stream) -> {
            if (to.test(stream)) write(session, stream, message);
        });
    }

    /**
     * Sign-out, expiry or revocation: close that session's sockets. Other tabs of a signed-out user would
     * otherwise stay connected (authorised when opened) and online; they cannot reconnect after this.
     */
    @EventListener
    public void onSessionEnded(SessionDestroyedEvent e) {
        streams.forEach((session, stream) -> {
            if (e.getSessionId().equals(stream.sessionId())) {
                ScreenWebSocketHandler.close(session, CloseStatus.POLICY_VIOLATION);
                drop(session);
            }
        });
    }

    /** Keeps idle sockets alive through proxies, and a vanished tab fails the write and leaves presence. */
    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        var ping = new PingMessage();
        streams.forEach((session, stream) -> write(session, stream, ping));
    }

    private void write(WebSocketSession session, Stream stream, WebSocketMessage<?> message) {
        try {
            stream.socket().sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            ScreenWebSocketHandler.close(session, CloseStatus.SESSION_NOT_RELIABLE);
            drop(session);
        }
    }

    private void drop(WebSocketSession session) {
        Stream gone = streams.remove(session);
        if (gone == null) return;
        log.debug("Events socket closed for {} ({} open)", gone.viewer().id(), streams.size());
        publish("PRESENCE", viewers());
    }
}
