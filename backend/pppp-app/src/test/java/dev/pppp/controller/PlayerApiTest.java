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
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
        mvc.perform(json(post("/api/v1/me/inputs"), "{\"command\":\"a\",\"walk\":true}")).andExpect(status().isBadRequest());

        consoles.idle("pib@x.y"); // leaving parks the state next to the player's own battery save
        assertThat(DATA.resolve("saves/pib@x.y/test.state")).isNotEmptyFile();
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

        mvc.perform(delete("/api/v1/admin/players/" + who).with(player).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/players/" + who).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        assertThat(consoles.running()).doesNotContain(who);
        assertThat(DATA.resolve("saves/" + who)).doesNotExist();
        mvc.perform(get("/api/v1/me/game").with(player)).andExpect(jsonPath("$.game").doesNotExist());
        mvc.perform(get("/api/v1/scoreboard").with(player))
                .andExpect(jsonPath("$[?(@.userId=='" + who + "')]").isEmpty());
        mvc.perform(put("/api/v1/me/game").with(player).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"other\"}")).andExpect(status().isOk()); // a different game now
        mvc.perform(delete("/api/v1/admin/players/nobody@x.y").with(ADMIN).with(csrf())).andExpect(status().isNotFound());
    }
}
