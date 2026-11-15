package dev.pppp.realtime;

import dev.pppp.model.ChatMessage;
import dev.pppp.model.LiveEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Forwards LiveEvents published by core services to every /ws/events socket; ADMIN chat lines to admins only. */
@Component
public class LiveEventRelay {
    private final EventPublisher events;

    public LiveEventRelay(EventPublisher events) {
        this.events = events;
    }

    @EventListener
    public void relay(LiveEvent event) {
        if (event.payload() instanceof ChatMessage m && "ADMIN".equals(m.kind())) events.publishToAdmins(event.type(), m);
        else events.publish(event.type(), event.payload());
    }
}
