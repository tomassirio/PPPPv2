package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.TestRedis;
import dev.pppp.model.LiveEvent;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@AutoConfigureMockMvc
@Import({TestRedis.class, ScreenWebSocketTest.BasicAuth.class})
class EventsWebSocketTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationEventPublisher events;
    @LocalServerPort int port;
    final ObjectMapper json = new ObjectMapper();

    @Test
    void handshakeNeedsAPlayer() throws Exception {
        mvc.perform(get("/ws/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws/events").with(user("p@x.y").roles("PLAYER"))).andExpect(status().isBadRequest());
    }

    @Test
    void pushesPresenceAndLiveEventsToEveryOpenSocket() throws Exception {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setBasicAuth("Friend@x.y", "pw");
        WebSocketSession ws = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
                received.add(json.readTree(m.getPayload()));
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/events")).get(5, TimeUnit.SECONDS);
        try {
            JsonNode presence = received.poll(5, TimeUnit.SECONDS);
            assertThat(presence.get("type").asText()).isEqualTo("PRESENCE");
            assertThat(presence.get("data").get(0).get("id").asText()).isEqualTo("friend@x.y");
            mvc.perform(get("/api/v1/presence").with(user("abe@x.y").roles("PLAYER")))
                    .andExpect(jsonPath("$[0].id").value("friend@x.y"));

            events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", Map.of("rows", 1)));
            JsonNode board = received.poll(5, TimeUnit.SECONDS);
            assertThat(board.get("type").asText()).isEqualTo("SCOREBOARD_CHANGED");
            assertThat(board.get("data").get("rows").asInt()).isEqualTo(1);
        } finally {
            ws.close();
        }
    }
}
