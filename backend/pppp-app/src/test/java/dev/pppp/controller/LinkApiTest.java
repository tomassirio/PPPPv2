package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import dev.pppp.service.LinkService;
import dev.pppp.service.PlayerConsoles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class LinkApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-link-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test"
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    static final String ASH = "ash@x.y", SAM = "sam@x.y", GARY = "gary@x.y";

    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;
    @Autowired LinkService link;

    /** Every test starts with all three consoles open, nobody ready, nothing pending, nobody linked. */
    @BeforeEach
    void openConsoles() {
        ReflectionTestUtils.setField(link, "challengeTtl", Duration.ofSeconds(60));
        ReflectionTestUtils.setField(link, "sessionCap", Duration.ofMinutes(15));
        for (String id : new String[] {ASH, SAM, GARY}) {
            consoles.pick(id, "test");
            consoles.idle(id); // ends whatever the previous test left
            consoles.open(id);
        }
    }

    private ResultActions as(String userId, MockHttpServletRequestBuilder req, String body) throws Exception {
        req = req.with(user(userId).roles("PLAYER")).with(csrf());
        return mvc.perform(body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions ready(String userId, String kind) throws Exception {
        return as(userId, put("/api/v1/link/ready"), "{\"kind\":" + (kind == null ? null : '"' + kind + '"') + "}");
    }

    private ResultActions challenge(String from, String to) throws Exception {
        return as(from, post("/api/v1/link/challenges"), "{\"userId\":\"" + to + "\"}");
    }

    private ResultActions offer(String from, String to, String kind) throws Exception {
        return as(from, post("/api/v1/link/challenges"), "{\"userId\":\"" + to + "\",\"kind\":\"" + kind + "\"}");
    }

    private void linkAshAndSam() throws Exception {
        ready(SAM, "TRADE").andExpect(status().isOk());
        challenge(ASH, SAM).andExpect(status().isOk());
        as(SAM, post("/api/v1/link/challenges/accept"), null).andExpect(status().isOk());
        assertThat(consoles.linkedWith(ASH)).isEqualTo(SAM);
    }

    @Test
    void playersListThemselvesAsReadyAndLeaveAgain() throws Exception {
        mvc.perform(get("/api/v1/link")).andExpect(status().isUnauthorized());
        ready(SAM, "DANCE").andExpect(status().isBadRequest());
        ready("nobody@x.y", "TRADE").andExpect(status().isConflict()); // no console open

        ready(SAM, "TRADE").andExpect(status().isOk()).andExpect(jsonPath("$.ready").value("TRADE"));
        ready(GARY, "BATTLE").andExpect(status().isOk());
        as(ASH, get("/api/v1/link"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").doesNotExist())
                .andExpect(jsonPath("$.lobby.length()").value(2))
                .andExpect(jsonPath("$.lobby[0].userId").value(GARY))
                .andExpect(jsonPath("$.lobby[0].kind").value("BATTLE"))
                .andExpect(jsonPath("$.lobby[1].userId").value(SAM))
                .andExpect(jsonPath("$.now").isNumber());

        ready(SAM, null).andExpect(status().isOk()).andExpect(jsonPath("$.ready").doesNotExist());
        consoles.idle(GARY); // closing the game leaves the lobby too
        as(ASH, get("/api/v1/link"), null).andExpect(jsonPath("$.lobby.length()").value(0));
    }

    @Test
    void acceptingAChallengeLinksBothConsolesUntilSomeoneUnplugs() throws Exception {
        ready(SAM, "TRADE");
        ready(ASH, "BATTLE");
        challenge(ASH, ASH).andExpect(status().isBadRequest());
        challenge(ASH, GARY).andExpect(status().isBadRequest()); // not in the lobby and no kind picked
        as(SAM, post("/api/v1/link/challenges/accept"), null).andExpect(status().isConflict()); // nothing waiting

        challenge(ASH, SAM).andExpect(status().isOk())
                .andExpect(jsonPath("$.outgoing.to").value(SAM))
                .andExpect(jsonPath("$.outgoing.kind").value("TRADE"));
        challenge(GARY, SAM).andExpect(status().isConflict()); // one per target
        ready(GARY, "TRADE");
        challenge(ASH, GARY).andExpect(status().isConflict()); // one per challenger
        as(SAM, get("/api/v1/link"), null)
                .andExpect(jsonPath("$.incoming.from").value(ASH))
                .andExpect(jsonPath("$.incoming.expiresAt").isNumber());

        as(SAM, post("/api/v1/link/challenges/accept"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming").doesNotExist())
                .andExpect(jsonPath("$.session.peer").value(ASH))
                .andExpect(jsonPath("$.session.kind").value("TRADE"))
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.lobby.length()").value(1)) // both left the lobby, Gary is still ready
                .andExpect(jsonPath("$.lobby[0].userId").value(GARY));
        assertThat(consoles.linkedWith(ASH)).isEqualTo(SAM);
        assertThat(consoles.linkedWith(SAM)).isEqualTo(ASH);
        as(ASH, get("/api/v1/link"), null).andExpect(jsonPath("$.session.peer").value(SAM))
                .andExpect(jsonPath("$.outgoing").doesNotExist());

        ready(ASH, "TRADE").andExpect(status().isConflict()); // while linked
        assertThatThrownBy(() -> consoles.setSpeed(SAM, 2)).isInstanceOf(IllegalStateException.class); // locked at 1x
        assertThat(consoles.speed(SAM)).isEqualTo(1.0);
        challenge(ASH, GARY).andExpect(status().isConflict());

        as(ASH, delete("/api/v1/link/session"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.session").doesNotExist());
        assertThat(consoles.linkedWith(ASH)).isNull();
        assertThat(consoles.linkedWith(SAM)).isNull();
        as(SAM, get("/api/v1/link"), null).andExpect(jsonPath("$.session").doesNotExist());
        assertThat(DATA.resolve("saves/" + ASH + "/test.state")).isNotEmptyFile();
        assertThat(DATA.resolve("saves/" + SAM + "/test.state")).isNotEmptyFile();
        assertThat(consoles.isAwake(ASH)).isTrue(); // both play on alone
        assertThat(consoles.isAwake(SAM)).isTrue();
    }

    @Test
    void aDeclinedOrExpiredChallengeLinksNobody() throws Exception {
        ready(SAM, "BATTLE");
        challenge(ASH, SAM).andExpect(status().isOk());
        as(SAM, post("/api/v1/link/challenges/decline"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming").doesNotExist())
                .andExpect(jsonPath("$.ready").value("BATTLE")); // still ready for someone else
        as(ASH, get("/api/v1/link"), null).andExpect(jsonPath("$.outgoing").doesNotExist());
        assertThat(consoles.linkedWith(SAM)).isNull();

        ReflectionTestUtils.setField(link, "challengeTtl", Duration.ZERO);
        challenge(ASH, SAM).andExpect(status().isOk()).andExpect(jsonPath("$.outgoing").doesNotExist());
        as(SAM, post("/api/v1/link/challenges/accept"), null).andExpect(status().isConflict());
        assertThat(consoles.linkedWith(SAM)).isNull();
    }

    @Test
    void theSessionEndsWhenOnePlayerLeavesOrTimeIsUp() throws Exception {
        linkAshAndSam();
        consoles.idle(ASH); // Ash's last screen socket closed
        assertThat(consoles.linkedWith(SAM)).isNull();
        assertThat(consoles.isAwake(ASH)).isFalse();
        assertThat(consoles.isAwake(SAM)).isTrue();
        as(SAM, get("/api/v1/link"), null).andExpect(jsonPath("$.session").doesNotExist());

        consoles.open(ASH);
        ReflectionTestUtils.setField(link, "sessionCap", Duration.ZERO);
        ready(SAM, "TRADE"); // not linkAshAndSam(): the scheduled sweep may end this session at any moment
        challenge(ASH, SAM).andExpect(status().isOk());
        as(SAM, post("/api/v1/link/challenges/accept"), null).andExpect(status().isOk());
        link.sweep();
        assertThat(consoles.linkedWith(ASH)).isNull();
        as(ASH, get("/api/v1/link"), null).andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    void anyPibCanBeOfferedATradeOrBattleDirectlyAndAcceptingWakesBothConsoles() throws Exception {
        consoles.idle(ASH); // both on other pages: consoles asleep, nobody in the lobby
        consoles.idle(GARY);
        offer(ASH, GARY, "DANCE").andExpect(status().isBadRequest());
        offer(ASH, "nobody@x.y", "TRADE").andExpect(status().isConflict()); // no game picked
        offer(ASH, GARY, "battle").andExpect(status().isOk())
                .andExpect(jsonPath("$.outgoing.to").value(GARY))
                .andExpect(jsonPath("$.outgoing.kind").value("BATTLE"));
        as(GARY, get("/api/v1/link"), null).andExpect(jsonPath("$.incoming.from").value(ASH))
                .andExpect(jsonPath("$.incoming.kind").value("BATTLE"));

        as(GARY, post("/api/v1/link/challenges/accept"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.peer").value(ASH));
        assertThat(consoles.isAwake(ASH)).isTrue();
        assertThat(consoles.isAwake(GARY)).isTrue();
        assertThat(consoles.linkedWith(GARY)).isEqualTo(ASH);
        offer(SAM, GARY, "TRADE").andExpect(status().isConflict()); // already linked
        as(GARY, delete("/api/v1/link/session"), null).andExpect(status().isOk());
    }
}
