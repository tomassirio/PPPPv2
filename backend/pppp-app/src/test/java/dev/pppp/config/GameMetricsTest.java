package dev.pppp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.model.ChatMessage;
import dev.pppp.model.LiveEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(TestRedis.class)
class GameMetricsTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationEventPublisher events;

    @Test
    void prometheusListsTheGameWithoutSigningIn() throws Exception {
        events.publishEvent(new LiveEvent("CHAT_MESSAGE", new ChatMessage("1", "a@b.c", "A", "hi", "USER", 0L, null)));
        String body = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("pppp_consoles{state=\"RUNNING\"} 0.0", "pppp_players_online 0.0",
                "pppp_websockets{endpoint=\"screen\"}", "pppp_link_sessions 0.0",
                "pppp_events_total{detail=\"USER\",type=\"CHAT_MESSAGE\"} 1.0", "jvm_memory_used_bytes");
    }

    @Test
    void cartridgesFoldIntoGames() {
        assertThat(GameMetrics.game("pokemon-crystal-version-usa-europe-rev-1")).isEqualTo("crystal");
        assertThat(GameMetrics.game("pokemon-red-patched")).isEqualTo("red");
        assertThat(GameMetrics.game("tetris")).isEqualTo("other");
    }
}
