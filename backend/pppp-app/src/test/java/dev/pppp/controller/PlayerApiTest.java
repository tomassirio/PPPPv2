package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class PlayerApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-player-test");
            Path rom = TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test"
            Files.copy(rom, rom.resolveSibling("other.gb"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    static final RequestPostProcessor PIB = user("pib@x.y").roles("PLAYER");
    static final RequestPostProcessor ADMIN = user("boss@x.y").roles("PLAYER", "ADMIN");

    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;
    @Autowired ProgressTracker tracker;
    @Autowired StringRedisTemplate redis;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.with(PIB).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theGameIsPickedOnceAndEachPlayerPressesTheirOwnConsole() throws Exception {
        mvc.perform(get("/api/v1/me/game")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/game").with(PIB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.game").doesNotExist())
                .andExpect(jsonPath("$.available[*].id").value(org.hamcrest.Matchers.containsInAnyOrder("test", "other")));

        mvc.perform(json(post("/api/v1/me/inputs"), "{\"command\":\"a\"}")).andExpect(status().isConflict()); // no console yet
        mvc.perform(json(put("/api/v1/me/game"), "{\"cartridgeId\":\"nope\"}")).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/me/game"), "{\"cartridgeId\":\"test\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("test"));
        mvc.perform(json(put("/api/v1/me/game"), "{\"cartridgeId\":\"other\"}")).andExpect(status().isConflict());
        mvc.perform(json(put("/api/v1/me/game"), "{\"cartridgeId\":\"test\"}")).andExpect(status().isOk()); // same pick again
        assertThat(consoles.game("someone-else@x.y")).isNull();

        consoles.open("pib@x.y"); // what connecting /ws/screen does
        mvc.perform(json(post("/api/v1/me/inputs"), "{\"command\":\"a\"}")).andExpect(status().isAccepted());
        mvc.perform(json(post("/api/v1/me/inputs"), "{\"command\":\"jump\"}")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/me/game").with(PIB)).andExpect(jsonPath("$.speed").value(1.0));
        mvc.perform(json(put("/api/v1/me/speed"), "{\"speed\":3}")).andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/v1/me/speed"), "{\"speed\":4}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/game").with(PIB)).andExpect(jsonPath("$.speed").value(4.0));

        consoles.idle("pib@x.y"); // leaving parks the state next to the player's own battery save
        assertThat(DATA.resolve("saves/pib@x.y/test.state")).isNotEmptyFile();
        consoles.discard("pib@x.y"); // shut down (a phone's locked screen, after the grace period)
        consoles.open("pib@x.y");
        assertThat(consoles.speed("pib@x.y")).isEqualTo(4.0); // boots again at the speed the chip shows
        consoles.idle("pib@x.y");
    }

    @Test
    void theKonamiCodeGivesThePrimaDonnaOnceAndOnlyWithAGame() throws Exception {
        String who = "opera@x.y";
        RequestPostProcessor player = user(who).roles("PLAYER");
        mvc.perform(post("/api/v1/me/konami").with(player).with(csrf())).andExpect(jsonPath("$.gift").value(false)); // no game yet
        mvc.perform(put("/api/v1/me/game").with(player).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/konami").with(player).with(csrf())).andExpect(jsonPath("$.gift").value(true));
        mvc.perform(post("/api/v1/me/konami").with(player).with(csrf())).andExpect(jsonPath("$.gift").value(false));
        assertThat(redis.opsForList().range("pibs:gifts:" + who, 0, -1)).containsExactly("prima-donna:1");
    }

    @Test
    void anAdminResetWipesTheRunSoThePlayerPicksAgain() throws Exception {
        String who = "reset-me@x.y";
        RequestPostProcessor player = user(who).roles("PLAYER");
        mvc.perform(put("/api/v1/me/game").with(player).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        consoles.open(who);
        consoles.idle(who);
        assertThat(DATA.resolve("saves/" + who + "/test.state")).isNotEmptyFile();
        tracker.record(who, Progress.EMPTY, ScoreboardApiTest.save(0b1)); // achievements and a Trainer Score
        assertThat(redis.hasKey("pibs:achievements:" + who)).isTrue();
        assertThat(redis.hasKey("pibs:trainer:" + who)).isTrue();
        redis.opsForValue().set("pibs:history:" + who, "{}"); // what the achievements remember

        mvc.perform(delete("/api/v1/admin/players/" + who).with(player).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/players/" + who).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        assertThat(consoles.running()).doesNotContain(who);
        assertThat(DATA.resolve("saves/" + who)).doesNotExist();
        assertThat(redis.keys("pibs:*" + who + "*")).isEmpty();
        assertThat(redis.opsForZSet().score("pibs:scoreboard", who)).isNull();
        mvc.perform(get("/api/v1/achievements").with(player)).andExpect(jsonPath("$.unlocked").value(0));
        mvc.perform(get("/api/v1/me/game").with(player)).andExpect(jsonPath("$.game").doesNotExist());
        mvc.perform(get("/api/v1/scoreboard").with(player))
                .andExpect(jsonPath("$[?(@.userId=='" + who + "')]").isEmpty());
        mvc.perform(put("/api/v1/me/game").with(player).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"other\"}")).andExpect(status().isOk()); // a different game now
        mvc.perform(delete("/api/v1/admin/players/nobody@x.y").with(ADMIN).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void removingSomeoneFromTheAllowlistWipesTheirRunToo() throws Exception {
        String who = "leaving@x.y";
        mvc.perform(post("/api/v1/admin/allowlist").with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + who + "\",\"role\":\"PLAYER\"}")).andExpect(status().isCreated());
        RequestPostProcessor player = user(who).roles("PLAYER");
        mvc.perform(put("/api/v1/me/game").with(player).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        tracker.record(who, Progress.EMPTY, ScoreboardApiTest.save(0b1));
        assertThat(redis.opsForZSet().score("pibs:scoreboard", who)).isNotNull();

        mvc.perform(delete("/api/v1/admin/allowlist/" + who).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        assertThat(redis.opsForZSet().score("pibs:scoreboard", who)).isNull();
        assertThat(redis.keys("pibs:*" + who + "*")).isEmpty();
    }

    @Test
    void keyBindingsArePerPlayerAndValidated() throws Exception {
        mvc.perform(get("/api/v1/me/keys").with(PIB))
                .andExpect(jsonPath("$.up").value("ArrowUp")).andExpect(jsonPath("$.a").value("z"));

        String wasd = "{\"up\":\"W\",\"down\":\"s\",\"left\":\"a\",\"right\":\"d\",\"a\":\"k\",\"b\":\"j\",\"start\":\"Enter\",\"select\":\" \"}";
        mvc.perform(json(put("/api/v1/me/keys"), wasd)).andExpect(status().isOk()).andExpect(jsonPath("$.up").value("w"));
        mvc.perform(get("/api/v1/me/keys").with(PIB))
                .andExpect(jsonPath("$.up").value("w")).andExpect(jsonPath("$.select").value(" "));
        mvc.perform(get("/api/v1/me/keys").with(ADMIN)).andExpect(jsonPath("$.up").value("ArrowUp")); // someone else's

        mvc.perform(json(put("/api/v1/me/keys"), wasd.replace("\"j\"", "\"K\""))).andExpect(status().isBadRequest()); // k twice
        mvc.perform(json(put("/api/v1/me/keys"), wasd.replace("\"Enter\"", "\"Escape\""))).andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/v1/me/keys"), "{\"up\":\"w\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/keys").with(PIB)).andExpect(jsonPath("$.b").value("j")); // unchanged

        // Fast forward: Space by default; any free key, or none.
        mvc.perform(get("/api/v1/me/keys").with(ADMIN)).andExpect(jsonPath("$.fast").value(" "));
        String withFast = wasd.replace("\"select\":\" \"", "\"select\":\"Shift\",\"fast\":\"F\"");
        mvc.perform(json(put("/api/v1/me/keys"), withFast)).andExpect(status().isOk()).andExpect(jsonPath("$.fast").value("f"));
        mvc.perform(get("/api/v1/me/keys").with(PIB)).andExpect(jsonPath("$.fast").value("f"));
        mvc.perform(json(put("/api/v1/me/keys"), withFast.replace("\"F\"", "\"k\""))).andExpect(status().isBadRequest()); // A's key
        mvc.perform(json(put("/api/v1/me/keys"), wasd)).andExpect(status().isOk()); // no fast: none
        mvc.perform(get("/api/v1/me/keys").with(PIB)).andExpect(jsonPath("$.fast").doesNotExist());
    }

    /** The trainer sprite: the game's own until picked; only a trainer the player's game has (the test ROM has none). */
    @Test
    void theTrainerSpriteIsOneOfTheGamesOwn() throws Exception {
        var other = user("solo@x.y").roles("PLAYER");
        mvc.perform(put("/api/v1/me/sprite").with(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"RED\"}")).andExpect(status().isConflict()); // no game yet
        consoles.pick("solo@x.y", "test");
        mvc.perform(get("/api/v1/me/sprite").with(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value("CHRIS"))
                .andExpect(jsonPath("$.choices").isEmpty()); // not a Pokémon ROM: nothing to walk as
        mvc.perform(put("/api/v1/me/sprite").with(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"RED\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/sprites/RED.png").with(other)).andExpect(status().isNotFound());
    }

    /** Only admins send god messages; they must say something, and only reach a player with a tab open. */
    @Test
    void godMessagesAreForAdminsAndOnlinePlayers() throws Exception {
        String url = "/api/v1/admin/players/pib@x.y/god-message";
        mvc.perform(post(url).with(PIB).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Behold\"}"))
                .andExpect(status().isConflict()); // no tab open
    }
}
