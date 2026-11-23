package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.emulator.TestRom;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@AutoConfigureMockMvc
@Import({TestRedis.class, ScreenWebSocketTest.BasicAuth.class})
class ScreenWebSocketTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;
    @Autowired ApplicationEventPublisher events;
    @LocalServerPort int port;
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-ws-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test"
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    /** The real login is Google; a real socket client here authenticates with Basic instead. */
    @TestConfiguration
    static class BasicAuth {
        @Bean
        @Order(0)
        SecurityFilterChain basicForTests(HttpSecurity http) throws Exception {
            return http.securityMatcher(r -> r.getHeader("Authorization") != null)
                    .authorizeHttpRequests(a -> a.anyRequest().hasRole("PLAYER"))
                    .httpBasic(b -> {})
                    .userDetailsService(new InMemoryUserDetailsManager(
                            User.withUsername("Friend@x.y").password("{noop}pw").roles("PLAYER").build(),
                            User.withUsername("Boss@x.y").password("{noop}pw").roles("PLAYER", "ADMIN").build()))
                    .build();
        }
    }

    @Test
    void handshakeGoesThroughTheProductionChain() throws Exception {
        mvc.perform(get("/ws/screen")).andExpect(status().isUnauthorized()); // browsers get the Google redirect
        mvc.perform(get("/ws/screen").with(user("p@x.y").roles("ADMIN"))).andExpect(status().isForbidden());
        // A player gets past security to the handshake handler, which rejects a non-upgrade GET.
        mvc.perform(get("/ws/screen").with(user("p@x.y").roles("PLAYER"))).andExpect(status().isBadRequest());
    }

    @Test
    void streamsPngFramesAndClosesOnRevoke() throws Exception {
        consoles.pick("friend@x.y", "test");
        try {
            BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
            CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setBasicAuth("Friend@x.y", "pw");
            WebSocketSession ws = new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
                @Override
                protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                    byte[] b = new byte[m.getPayloadLength()];
                    m.getPayload().get(b);
                    frames.add(b);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    closed.complete(status);
                }
            }, headers, URI.create("ws://localhost:" + port + "/ws/screen")).get(5, TimeUnit.SECONDS);

            // Generous waits: on a busy machine (a CI runner, other emulators) booting and encoding take seconds.
            byte[] png = frames.poll(15, TimeUnit.SECONDS);
            assertThat(png).isNotNull().startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            assertThat(png.length).isLessThan(10_000);
            assertThat(frames.poll(15, TimeUnit.SECONDS)).isNotNull(); // keeps streaming

            events.publishEvent(new UserRevokedEvent("friend@x.y"));
            assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        } finally {
            consoles.idle("friend@x.y");
        }
    }

    /** A console shut down under an open tab (nobody touched it, or an admin closed it): the screen closes with 4408. */
    @Test
    void anOpenScreenIsToldWhenItsConsoleCloses() throws Exception {
        consoles.pick("friend@x.y", "test");
        BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setBasicAuth("Friend@x.y", "pw");
        new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                frames.add(new byte[0]);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.complete(status);
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/screen")).get(5, TimeUnit.SECONDS);
        assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull();

        assertThat(consoles.close("friend@x.y")).isTrue();
        assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4408);
        assertThat(consoles.running()).doesNotContain("friend@x.y");
    }

    @Autowired dev.pppp.service.LinkService link;

    /** Leaving /play for another tab closes the screen socket; a linked player keeps the cable in until it ends. */
    @Test
    void linkedPlayerLeavingThePageStaysLinked() throws Exception {
        String friend = "friend@x.y", pal = "pal@x.y";
        consoles.pick(friend, "test");
        consoles.pick(pal, "test");
        try {
            CompletableFuture<Void> closed = new CompletableFuture<>();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setBasicAuth("Friend@x.y", "pw");
            WebSocketSession ws = new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    closed.complete(null);
                }
            }, headers, URI.create("ws://localhost:" + port + "/ws/screen")).get(5, TimeUnit.SECONDS);
            link.challenge(pal, friend, "TRADE");
            link.accept(friend);
            assertThat(consoles.linkedWith(friend)).isEqualTo(pal);

            ws.close();
            closed.get(5, TimeUnit.SECONDS);
            Thread.sleep(300); // the server's close callback
            assertThat(consoles.linkedWith(friend)).isEqualTo(pal);
            assertThat(consoles.isAwake(friend)).isTrue();

            link.unplug(pal);
            assertThat(consoles.linkedWith(friend)).isNull();
            assertThat(consoles.isAwake(friend)).as("nobody watching: left, still running").isTrue();
        } finally {
            consoles.idle(friend);
            consoles.idle(pal);
        }
    }

    /** A third Pib watches one end of a running link: frames until the cable comes out, and only then. */
    @Test
    void spectatorsWatchALinkedPlayerReadOnly() throws Exception {
        String pal = "pal@x.y", buddy = "buddy@x.y";
        consoles.pick(pal, "test");
        consoles.pick(buddy, "test");
        try {
            CompletableFuture<CloseStatus> early = new CompletableFuture<>();
            watch(pal, new LinkedBlockingQueue<>(), early);
            assertThat(early.get(5, TimeUnit.SECONDS).getCode()).as("not linked").isEqualTo(4404);

            consoles.open(pal);
            consoles.open(buddy);
            link.challenge(buddy, pal, "BATTLE");
            link.accept(pal);
            BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
            CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
            watch(pal, frames, closed);
            assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull().startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            assertThat(consoles.isAwake("friend@x.y")).as("watching wakes no console of the spectator's").isFalse();

            link.unplug(buddy);
            assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4404);
        } finally {
            consoles.idle(pal);
            consoles.idle(buddy);
        }
    }

    /** An admin watches anyone whose console runs, linked or not, until it stops (NOT_PLAYING, 4410). */
    @Test
    void adminsWatchAnyRunningConsole() throws Exception {
        String solo = "solo@x.y";
        consoles.pick(solo, "test");
        try {
            CompletableFuture<CloseStatus> early = new CompletableFuture<>();
            watch(solo, new LinkedBlockingQueue<>(), early, "Boss@x.y");
            assertThat(early.get(5, TimeUnit.SECONDS).getCode()).as("not playing").isEqualTo(4410);

            consoles.open(solo);
            CompletableFuture<CloseStatus> player = new CompletableFuture<>();
            watch(solo, new LinkedBlockingQueue<>(), player);
            assertThat(player.get(5, TimeUnit.SECONDS).getCode()).as("players only watch links").isEqualTo(4404);

            BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
            CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
            watch(solo, frames, closed, "Boss@x.y");
            assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull().startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

            consoles.idle(solo);
            assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4410);
        } finally {
            consoles.discard(solo);
        }
    }

    private void watch(String user, BlockingQueue<byte[]> frames, CompletableFuture<CloseStatus> closed) throws Exception {
        watch(user, frames, closed, "Friend@x.y");
    }

    private void watch(String user, BlockingQueue<byte[]> frames, CompletableFuture<CloseStatus> closed, String as) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setBasicAuth(as, "pw");
        new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                byte[] b = new byte[m.getPayloadLength()];
                m.getPayload().get(b);
                frames.add(b);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.complete(status);
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/watch/" + user)).get(5, TimeUnit.SECONDS);
    }
}
