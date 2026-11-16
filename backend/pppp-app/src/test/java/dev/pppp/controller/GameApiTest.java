package dev.pppp.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class GameApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-game-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test": no game profile
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    static final RequestPostProcessor PIB = user("pib@x.y").roles("PLAYER");

    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;

    /** Decoding a real party box is unit-tested with synthetic RAM (ProgressTrackerTest, PokemonRedTest). */
    @Test
    void noGameDataBeforeAPickOrForACartridgeWithoutAProfile() throws Exception {
        mvc.perform(get("/api/v1/game/state")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/game/icons/25.png")).andExpect(status().isUnauthorized()); // sprites are per player now
        mvc.perform(get("/api/v1/game/badges/0.png")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/game/state").with(PIB)).andExpect(status().isNoContent()); // nothing picked

        mvc.perform(put("/api/v1/me/game").with(PIB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        consoles.open("pib@x.y");
        mvc.perform(get("/api/v1/game/state").with(PIB)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/game/icons/25.png").with(PIB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/icons/player.png").with(PIB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/badges/0.png").with(PIB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game-events").with(PIB)).andExpect(status().isNotFound()); // v1's shared history is gone
    }

    @Test
    void teaserIsPublicAndOnlyCounts() throws Exception {
        mvc.perform(get("/api/v1/public/teaser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").isNumber())
                .andExpect(jsonPath("$.players").isNumber())
                .andExpect(jsonPath("$.versions").isMap())
                .andExpect(jsonPath("$.caughtTogether").isNumber())
                .andExpect(jsonPath("$.achievements").isNumber())
                .andExpect(jsonPath("$.trades").isNumber())
                .andExpect(jsonPath("$.battles").isNumber())
                .andExpect(jsonPath("$.trainers").isArray())
                .andExpect(jsonPath("$..email").isEmpty())
                .andExpect(jsonPath("$.party").doesNotExist())
                .andExpect(jsonPath("$..userId").isEmpty()); // first names only, never emails or ids
        mvc.perform(get("/api/v1/public/sprites/nope/player.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/sprites/nope/25.png")).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(PublicController.firstName("Tomas Sirio")).isEqualTo("Tomas");
        org.assertj.core.api.Assertions.assertThat(PublicController.firstName("pib@x.y")).isEqualTo("Pib");
        org.assertj.core.api.Assertions.assertThat(PublicController.firstName(null)).isEqualTo("Pib");
        mvc.perform(get("/api/v1/public/screen.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/presence")).andExpect(status().isUnauthorized());
    }
}
