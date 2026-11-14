package dev.pppp.controller;

import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {
    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/input")
    public Settings.Input input() { return settings.input(); }

    @PutMapping("/input")
    public Settings.Input input(@Valid @RequestBody Settings.Input s) { settings.save(s); return s; }
}
