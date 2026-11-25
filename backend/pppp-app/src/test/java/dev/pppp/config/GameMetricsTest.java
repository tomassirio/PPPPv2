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
    @Autowired dev.pppp.service.ActivityService activity;

    @Test
    void prometheusListsTheGameWithoutSigningIn() throws Exception {
        activity.played("a@b.c");
        activity.played("a@b.c"); // once a day
        events.publishEvent(new LiveEvent("CHAT_MESSAGE", new ChatMessage("1", "a@b.c", "A", "hi", "USER", 0L, null)));
        String body = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("pppp_consoles{state=\"RUNNING\"} 0.0", "pppp_players_online 0.0",
                "pppp_websockets{endpoint=\"screen\"}", "pppp_link_sessions{kind=\"TRADE\"} 0.0", "pppp_players_reached{step=\"4 champion\"} 0.0",
                "pppp_players_by_badges{badges=\"16\"} 0.0", "pppp_pokemon_caught 0.0", "pppp_players_active{window=\"today\"} 1.0", "pppp_players_returned{after=\"1d\"} 0.0",
                "pppp_gifts{state=\"sent\"} 0.0", "pppp_access_requests 0.0", "pppp_shinies 0.0", "pppp_play_session_seconds_count",
                "pppp_events_total{detail=\"USER\",kind=\"\",type=\"CHAT_MESSAGE\"} 1.0", "jvm_memory_used_bytes");
    }

    @Test
    void cartridgesFoldIntoGames() {
        assertThat(GameMetrics.game("pokemon-crystal-version-usa-europe-rev-1")).isEqualTo("crystal");
        assertThat(GameMetrics.game("pokemon-red-patched")).isEqualTo("red");
        assertThat(GameMetrics.game("tetris")).isEqualTo("other");
        assertThat(GameMetrics.hours("12:30")).isEqualTo(12.5);
        assertThat(GameMetrics.hours(null)).isZero();
    }
}
