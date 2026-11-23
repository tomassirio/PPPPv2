package dev.pppp.controller;

import dev.pppp.security.CurrentUser;
import dev.pppp.service.PlayerSessionsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin: a player's Game Boy session and files, closing it, and downloading their save. */
@RestController
@RequestMapping("/api/v1/admin/sessions")
public class SessionsController {
    private final PlayerSessionsService sessions;

    public SessionsController(PlayerSessionsService sessions) {
        this.sessions = sessions;
    }

    /** One player's session and files (the admin player page). */
    @GetMapping("/{userId}")
    public PlayerSessionsService.PlayerSession one(@PathVariable String userId) {
        return sessions.of(userId.toLowerCase(Locale.ROOT));
    }

    /** Parks the console and shuts it down; 404 when none is in memory. */
    @PostMapping("/{userId}/close")
    public ResponseEntity<Void> close(@PathVariable String userId, Authentication auth) {
        sessions.close(CurrentUser.id(auth), userId.toLowerCase(Locale.ROOT));
        return ResponseEntity.noContent().build();
    }

    /** The player's in-game save (.sav) as a download; 404 before their first save. */
    @GetMapping("/{userId}/save")
    public ResponseEntity<byte[]> save(@PathVariable String userId) throws IOException {
        Path p = sessions.save(userId.toLowerCase(Locale.ROOT));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(userId.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + p.getFileName()).build().toString())
                .body(Files.readAllBytes(p));
    }
}
