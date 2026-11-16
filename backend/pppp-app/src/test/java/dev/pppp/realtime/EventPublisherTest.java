package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.Viewer;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

class EventPublisherTest {
    static WebSocketSession socket(String user, String loginSession) {
        var s = mock(WebSocketSession.class);
        when(s.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))));
        var attributes = new HashMap<String, Object>();
        attributes.put(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, loginSession);
        when(s.getAttributes()).thenReturn(attributes);
        when(s.isOpen()).thenReturn(true);
        when(s.getId()).thenReturn(user + Math.random());
        return s;
    }

    @Test
    void signingOutClosesThatSessionsSocketsInEveryTab() {
        var events = new EventPublisher(new ObjectMapper());
        events.afterConnectionEstablished(socket("zoe@x.y", "zoe-session")); // tab 1
        events.afterConnectionEstablished(socket("zoe@x.y", "zoe-session")); // tab 2, same login
        events.afterConnectionEstablished(socket("abe@x.y", "abe-session"));
        assertThat(events.viewers()).extracting(Viewer::id).containsExactly("abe@x.y", "zoe@x.y");

        events.onSessionEnded(new SessionDeletedEvent(this, new MapSession("zoe-session")));

        assertThat(events.viewers()).extracting(Viewer::id).containsExactly("abe@x.y");
    }

    @Test
    void oneEntryPerUserEvenWhenTheirSocketsCarryDifferentNames() {
        var events = new EventPublisher(new ObjectMapper());
        events.afterConnectionEstablished(socket("zoe@x.y", "a"));
        var google = socket("zoe@x.y", "b"); // same id, a full name from Google
        when(google.getPrincipal()).thenReturn(new org.springframework.security.authentication.TestingAuthenticationToken(
                "zoe@x.y", null, "ROLE_PLAYER"));
        events.afterConnectionEstablished(google);
        assertThat(events.viewers()).extracting(Viewer::id).containsExactly("zoe@x.y");
    }
}
