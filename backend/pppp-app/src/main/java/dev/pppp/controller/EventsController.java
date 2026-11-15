package dev.pppp.controller;

import dev.pppp.model.Viewer;
import dev.pppp.realtime.EventPublisher;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventsController {
    private final EventPublisher events;

    public EventsController(EventPublisher events) {
        this.events = events;
    }

    /** Who has /play open right now. Kept current afterwards by PRESENCE events. */
    @GetMapping("/api/v1/presence")
    public List<Viewer> presence() {
        return events.viewers();
    }
}
