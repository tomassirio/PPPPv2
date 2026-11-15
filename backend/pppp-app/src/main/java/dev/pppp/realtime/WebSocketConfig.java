package dev.pppp.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/** Same-origin only (Spring's default), which blocks cross-site WebSocket hijacking with the session cookie. */
@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    private final ScreenWebSocketHandler screen;
    private final AudioWebSocketHandler audio;
    private final InputWebSocketHandler input;
    private final EventPublisher events;

    WebSocketConfig(ScreenWebSocketHandler screen, AudioWebSocketHandler audio, InputWebSocketHandler input, EventPublisher events) {
        this.events = events;
        this.screen = screen;
        this.audio = audio;
        this.input = input;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(screen, "/ws/screen");
        registry.addHandler(audio, "/ws/audio");
        registry.addHandler(input, "/ws/input");
        // Copies the login session id, so signing out closes the socket.
        registry.addHandler(events, "/ws/events").addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
