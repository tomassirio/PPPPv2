package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.emulator.TestRom;
import dev.pppp.model.Progress;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.ProgressTracker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class ScoreboardApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-score-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test": no game profile
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;
    @Autowired ProgressTracker tracker;
    @Autowired StringRedisTemplate redis;

    @Test
    void scoreboardAndActivityFollowRecordedProgress() throws Exception {
        mvc.perform(get("/api/v1/scoreboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/activity")).andExpect(status().isUnauthorized());
        for (String id : new String[] {"pib@x.y", "pibette@x.y"}) {
            RequestPostProcessor who = user(id).roles("PLAYER");
            mvc.perform(get("/api/v1/scoreboard").with(who)).andExpect(jsonPath("$[?(@.userId == '" + id + "')]").isEmpty());
            mvc.perform(put("/api/v1/me/game").with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        }
        RequestPostProcessor pib = user("pib@x.y").roles("PLAYER");
        mvc.perform(get("/api/v1/scoreboard").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)) // picking a game is enough to be listed
                .andExpect(jsonPath("$[0].points").value(0))
                .andExpect(jsonPath("$[0].online").value(false));
        mvc.perform(get("/api/v1/activity").with(pib)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        consoles.open("pibette@x.y"); // the test ROM has no profile, so the job itself scores nothing
        tracker.record("pibette@x.y", new Progress(Set.of("Boulder"), Set.of("Pidgey"), Set.of("Pidgey", "Rattata"), 12, false));
        tracker.record("pibette@x.y", new Progress(Set.of("Boulder"), Set.of("Pidgey"), Set.of("Pidgey"), 12, false)); // again: nothing
        tracker.record("pib@x.y", new Progress(Set.of(), Set.of(), Set.of("Pidgey"), 5, false));

        mvc.perform(get("/api/v1/scoreboard").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("pibette@x.y"))
                .andExpect(jsonPath("$[0].points").value(100 + 10 + 2 + 2 + 5))
                .andExpect(jsonPath("$[0].badges").value(1))
                .andExpect(jsonPath("$[0].caught").value(1))
                .andExpect(jsonPath("$[0].seen").value(2))
                .andExpect(jsonPath("$[0].game").value("test"))
                .andExpect(jsonPath("$[0].online").value(true))
                .andExpect(jsonPath("$[1].userId").value("pib@x.y"))
                .andExpect(jsonPath("$[1].points").value(2))
                .andExpect(jsonPath("$[1].online").value(false));
        mvc.perform(get("/api/v1/activity?limit=2").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user").value("pib@x.y"))
                .andExpect(jsonPath("$[0].type").value("SEEN"))
                .andExpect(jsonPath("$[0].detail").value("Pidgey"))
                .andExpect(jsonPath("$[0].points").value(2))
                .andExpect(jsonPath("$[0].at").isNumber())
                .andExpect(jsonPath("$[1].type").value("LEVEL"));
        mvc.perform(get("/api/v1/activity?limit=0").with(pib)).andExpect(status().isBadRequest());

        assertThat(redis.opsForStream().size("pibs:events")).isEqualTo(6);
        assertThat(redis.opsForZSet().score("pibs:scoreboard", "pibette@x.y")).isEqualTo(119);
        assertThat(redis.opsForHash().entries("pibs:progress:pibette@x.y")).containsEntry("badges", "Boulder")
                .containsEntry("level", "12").containsEntry("hallOfFame", "false");
        // Set.of iterates in a different order per JVM, so the stored order is not fixed.
        assertThat(((String) redis.opsForHash().get("pibs:progress:pibette@x.y", "seen")).split("\\|"))
                .containsExactlyInAnyOrder("Pidgey", "Rattata");
    }
}
