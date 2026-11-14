package dev.pppp.controller;

import dev.pppp.TestRedis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class SettingsApiTest {
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;

    @Test
    void adminUpdatesInputSettingsPlayerCannot() throws Exception {
        String body = "{\"holdFrames\":4,\"releaseFrames\":3}";
        mvc.perform(put("/api/v1/admin/settings/input").with(csrf()).with(user("p").roles("PLAYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/settings/input").with(csrf()).with(user("a").roles("PLAYER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/settings/input").with(user("a").roles("ADMIN")))
                .andExpect(jsonPath("$.holdFrames").value(4))
                .andExpect(jsonPath("$.releaseFrames").value(3));
        mvc.perform(put("/api/v1/admin/settings/input").with(csrf()).with(user("a").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"holdFrames\":0,\"releaseFrames\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("holdFrames must be greater than or equal to 1"));
        mvc.perform(get("/api/v1/admin/settings/rate-limit").with(user("a").roles("ADMIN"))).andExpect(status().isNotFound());
    }

    /** What v1 stored: fields that are gone are ignored, the ones that are left are kept. */
    @Test
    void settingsStoredByV1StillLoad() throws Exception {
        redis.opsForValue().set("settings:input", "{\"mode\":\"ANARCHY\",\"democracyWindowSeconds\":10,"
                + "\"enterDemocracyPercent\":80,\"leaveDemocracyPercent\":50,\"holdFrames\":9,\"releaseFrames\":2,"
                + "\"maxQueueLength\":50,\"chatCommands\":true}");
        mvc.perform(get("/api/v1/admin/settings/input").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdFrames").value(9))
                .andExpect(jsonPath("$.releaseFrames").value(2))
                .andExpect(jsonPath("$.mode").doesNotExist());
    }

    @Test
    void anonymousApiCallGets401() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
    }
}
