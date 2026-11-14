package dev.pppp.service;

import dev.pppp.model.Settings;
import dev.pppp.repository.SettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Settings records with their defaults. Every save publishes the new record as a Spring ApplicationEvent,
 * so listeners can react with @EventListener.
 */
@Service
public class SettingsService {
    private final SettingsRepository repo;
    private final ApplicationEventPublisher events;

    public SettingsService(SettingsRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    public Settings.Input input() { return repo.find("input", Settings.Input.class, Settings.Input.DEFAULT); }

    public void save(Settings.Input s) { write("input", s); }

    private void write(String name, Object value) {
        repo.save(name, value);
        events.publishEvent(value);
    }
}
