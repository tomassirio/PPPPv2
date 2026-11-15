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
import dev.pppp.service.game.Gen2Save;
import dev.pppp.service.game.ProgressTracker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // one Redis: the scoreboard test counts every player
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

    /** A Gold/Silver/Crystal save with these Johto badge bits, the starter received and nothing else. */
    static Gen2Save save(int johtoBadges) {
        return new Gen2Save(1, johtoBadges, 0, 0, false, true, false, false, new BitSet(), new BitSet(), 0, List.of(),
                Set.of(), 0, 0, 0, 0, 0);
    }

    @Test
    @Order(1)
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
        // A Gen 2 save: Zephyr (10 story) ranks pib above pibette's points, which no longer rank anyone.
        tracker.record("pib@x.y", new Progress(Set.of(), Set.of(), Set.of("Pidgey"), 5, false), save(0b1));

        mvc.perform(get("/api/v1/scoreboard").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("pib@x.y"))
                .andExpect(jsonPath("$[0].points").value(2))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].trainerScore").value(10))
                .andExpect(jsonPath("$[0].title").value("Youngster"))
                .andExpect(jsonPath("$[0].grade").value("F"))
                .andExpect(jsonPath("$[0].achievementPoints").value(1 + 3))
                .andExpect(jsonPath("$[0].achievementsUnlocked").value(2))
                .andExpect(jsonPath("$[1].userId").value("pibette@x.y"))
                .andExpect(jsonPath("$[1].points").value(100 + 10 + 2 + 2 + 5))
                .andExpect(jsonPath("$[1].badges").value(1))
                .andExpect(jsonPath("$[1].caught").value(1))
                .andExpect(jsonPath("$[1].seen").value(2))
                .andExpect(jsonPath("$[1].game").value("test"))
                .andExpect(jsonPath("$[1].online").value(true))
                .andExpect(jsonPath("$[1].trainerScore").value(0))
                .andExpect(jsonPath("$[1].achievementsUnlocked").value(0));
        mvc.perform(get("/api/v1/activity?limit=3").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].user").value("pib@x.y"))
                .andExpect(jsonPath("$[0].type").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$[0].detail").value("Zephyr Badge"))
                .andExpect(jsonPath("$[0].points").value(3))
                .andExpect(jsonPath("$[0].at").isNumber())
                .andExpect(jsonPath("$[1].detail").value("New Bark Beginnings"))
                .andExpect(jsonPath("$[2].type").value("SEEN"))
                .andExpect(jsonPath("$[2].detail").value("Pidgey"))
                .andExpect(jsonPath("$[2].points").value(2));
        mvc.perform(get("/api/v1/activity?limit=0").with(pib)).andExpect(status().isBadRequest());

        assertThat(redis.opsForStream().size("pibs:events")).isEqualTo(5 + 1 + 2);
        assertThat(redis.opsForZSet().score("pibs:scoreboard", "pibette@x.y")).isEqualTo(0);
        assertThat(redis.opsForZSet().score("pibs:scoreboard", "pib@x.y")).isEqualTo(10 * 10_000 + 4);
        assertThat(redis.opsForHash().keys("pibs:achievements:pib@x.y"))
                .containsExactlyInAnyOrder("new-bark-beginnings", "zephyr-badge");
        assertThat(redis.opsForHash().entries("pibs:trainer:pib@x.y")).containsEntry("score", "10")
                .containsEntry("title", "Youngster").containsEntry("grade", "F").containsEntry("story", "10");
        assertThat(redis.opsForHash().entries("pibs:progress:pibette@x.y")).containsEntry("badges", "Boulder")
                .containsEntry("level", "12").containsEntry("hallOfFame", "false");
        // Set.of iterates in a different order per JVM, so the stored order is not fixed.
        assertThat(((String) redis.opsForHash().get("pibs:progress:pibette@x.y", "seen")).split("\\|"))
                .containsExactlyInAnyOrder("Pidgey", "Rattata");
    }

    @Test
    @Order(2)
    void achievementsOfTheCallerAndOfOtherPlayers() throws Exception {
        mvc.perform(get("/api/v1/achievements")).andExpect(status().isUnauthorized());
        RequestPostProcessor ash = user("Ash@X.y").roles("PLAYER");
        mvc.perform(get("/api/v1/achievements").with(ash)).andExpect(status().isOk()) // nothing yet: all locked
                .andExpect(jsonPath("$.userId").value("ash@x.y"))
                .andExpect(jsonPath("$.trainer.score").value(0))
                .andExpect(jsonPath("$.trainer.title").value("Youngster"))
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.achievements.length()").value(52))
                .andExpect(jsonPath("$.achievements[0].id").value("new-bark-beginnings"))
                .andExpect(jsonPath("$.achievements[0].unlockedAt").doesNotExist());
        mvc.perform(get("/api/v1/players/ash@x.y/achievements").with(ash)).andExpect(status().isNotFound());

        tracker.record("ash@x.y", Progress.EMPTY, save(0b11));
        mvc.perform(get("/api/v1/achievements").with(ash)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trainer.score").value(20))
                .andExpect(jsonPath("$.trainer.story").value(20))
                .andExpect(jsonPath("$.points").value(1 + 3 + 3))
                .andExpect(jsonPath("$.unlocked").value(3))
                .andExpect(jsonPath("$.achievements[0].unlockedAt").isNumber())
                .andExpect(jsonPath("$.achievements[0].category").value("Story"))
                .andExpect(jsonPath("$.achievements[0].points").value(1))
                .andExpect(jsonPath("$.achievements[0].implemented").value(true))
                .andExpect(jsonPath("$.achievements[?(@.id == 'plain-badge')].unlockedAt").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'breeder')].implemented").value(org.hamcrest.Matchers.contains(false)));
        mvc.perform(get("/api/v1/players/Ash@x.y/achievements").with(user("misty@x.y").roles("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ash@x.y"))
                .andExpect(jsonPath("$.unlocked").value(3));
    }
}
