package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class AdminApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("pppp-admin-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;

    static final RequestPostProcessor ADMIN = user("a@x.y").roles("PLAYER", "ADMIN");
    static final RequestPostProcessor PLAYER = user("p@x.y").roles("PLAYER");

    /** 32 KB with a valid header checksum. */
    static byte[] rom(String title, boolean cgb) {
        byte[] rom = new byte[32 * 1024];
        byte[] t = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, rom, 0x134, t.length);
        if (cgb) rom[0x143] = (byte) 0x80;
        int x = 0;
        for (int i = 0x134; i <= 0x14C; i++) x = x - rom[i] - 1;
        rom[0x14D] = (byte) x;
        return rom;
    }

    void upload(String filename, byte[] content, int expectedStatus) throws Exception {
        mvc.perform(multipart("/api/v1/admin/cartridges").file(new MockMultipartFile("file", filename, null, content))
                        .with(ADMIN).with(csrf()))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void uploadListDeleteRom() throws Exception {
        upload("../../evil/Pokemon Red!.GB", rom("POKEMON RED", false), 201);
        upload("crystal.gbc", rom("PM_CRYSTAL", true), 201);
        assertThat(DATA.resolve("roms/pokemon-red.gb")).exists();

        mvc.perform(get("/api/v1/admin/cartridges").with(ADMIN))
                .andExpect(jsonPath("$[?(@.id=='pokemon-red')].title").value("POKEMON RED"))
                .andExpect(jsonPath("$[?(@.id=='pokemon-red')].cgb").value(false))
                .andExpect(jsonPath("$[?(@.id=='crystal')].cgb").value(true))
                .andExpect(jsonPath("$[?(@.id=='crystal')].size").value(32 * 1024));

        upload("pokemon red.gb", rom("POKEMON RED", false), 409);
        byte[] broken = rom("X", false);
        broken[0x14D]++;
        upload("broken.gb", broken, 400);
        upload("notes.txt", rom("X", false), 400);
        upload("tiny.gb", new byte[0x150], 400);
        mvc.perform(get("/api/v1/admin/cartridges").with(PLAYER)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/admin/cartridges/pokemon-red").with(ADMIN).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/cartridges/pokemon-red").with(ADMIN).with(csrf())).andExpect(status().isNotFound());
        assertThat(DATA.resolve("roms/pokemon-red.gb")).doesNotExist();
    }
}
