package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
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

/** Consoles only live in memory while someone plays; the admin overview of sessions and saves. */
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class SessionsApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-sessions-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test"
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

    @AfterEach
    void defaults() {
        consoles.limits(Duration.ofSeconds(60), Duration.ofMinutes(15));
    }

    private void pick(String userId) throws Exception {
        mvc.perform(put("/api/v1/me/game").with(user(userId).roles("PLAYER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
    }

    @Test
    void aConsoleLeftAloneIsParkedAndShutDown() throws Exception {
        pick("pib@x.y");
        consoles.limits(Duration.ofMillis(200), Duration.ofMinutes(15));
        consoles.open("pib@x.y");
        consoles.idle("pib@x.y"); // the last tab closed
        consoles.sweep();
        assertThat(consoles.running()).contains("pib@x.y"); // still in its grace period

        consoles.open("pib@x.y"); // back within it: the shutdown is off
        Thread.sleep(250);
        consoles.sweep();
        assertThat(consoles.isAwake("pib@x.y")).isTrue();

        consoles.idle("pib@x.y");
        Thread.sleep(250);
        consoles.sweep();
        assertThat(consoles.running()).doesNotContain("pib@x.y");
        assertThat(DATA.resolve("saves/pib@x.y/test.state")).isRegularFile();

        consoles.open("pib@x.y"); // boots again from the parked state
        assertThat(consoles.isAwake("pib@x.y")).isTrue();
        consoles.discard("pib@x.y");
    }

    @Test
    void anUntouchedConsoleIsPausedThenShutDownAfterTheGracePeriod() throws Exception {
        pick("idle@x.y");
        String[] away = {null};
        consoles.onAway(id -> away[0] = id);
        consoles.limits(Duration.ofMillis(300), Duration.ofMillis(200)); // stands in for a day paused, an hour untouched
        consoles.open("idle@x.y");
        Thread.sleep(120);
        consoles.touch("idle@x.y"); // the browser's activity ping
        Thread.sleep(120);
        consoles.sweep();
        assertThat(consoles.isAwake("idle@x.y")).isTrue();
        Thread.sleep(250);
        consoles.sweep();
        assertThat(away[0]).isEqualTo("idle@x.y");
        // Paused, parked, still in memory: Continue resumes it at once.
        assertThat(consoles.isAwake("idle@x.y")).isFalse();
        assertThat(consoles.running()).contains("idle@x.y");
        Thread.sleep(350);
        consoles.sweep();
        assertThat(consoles.running()).doesNotContain("idle@x.y");
    }

    @Test
    void leavingKeepsTheConsoleRunning() throws Exception {
        pick("leaver@x.y");
        consoles.open("leaver@x.y");
        consoles.leave("leaver@x.y"); // the last tab closed
        consoles.sweep();
        assertThat(consoles.isAwake("leaver@x.y")).isTrue();
        consoles.discard("leaver@x.y");
    }

    @Test
    void linkedConsolesAreLeftToTheLinkSession() throws Exception {
        pick("a@x.y");
        pick("b@x.y");
        consoles.open("a@x.y");
        consoles.open("b@x.y");
        consoles.link("a@x.y", "b@x.y");
        consoles.limits(Duration.ofMillis(100), Duration.ofMillis(100)); // after linking: the scheduled sweep runs too
        Thread.sleep(200);
        consoles.sweep();
        assertThat(consoles.isAwake("a@x.y")).isTrue();
        assertThat(consoles.isAwake("b@x.y")).isTrue();
        consoles.unlink("a@x.y");
        consoles.discard("a@x.y");
        consoles.discard("b@x.y");
    }

    @Test
    void adminsSeeEverySessionAndCanCloseOne() throws Exception {
        pick("seen@x.y");
        consoles.limits(Duration.ofSeconds(60), Duration.ofMinutes(15));
        consoles.open("seen@x.y");
        mvc.perform(get("/api/v1/admin/sessions/seen@x.y").with(PIB)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/sessions/SEEN@x.y").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.cartridgeId").value("test"));

        mvc.perform(post("/api/v1/admin/sessions/seen@x.y/close").with(ADMIN)).andExpect(status().isForbidden()); // no CSRF
        mvc.perform(post("/api/v1/admin/sessions/seen@x.y/close").with(ADMIN).with(csrf())).andExpect(status().isNoContent());
        assertThat(consoles.running()).doesNotContain("seen@x.y");
        mvc.perform(post("/api/v1/admin/sessions/seen@x.y/close").with(ADMIN).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/sessions/nobody@x.y/save").with(ADMIN)).andExpect(status().isNotFound());
    }
}
