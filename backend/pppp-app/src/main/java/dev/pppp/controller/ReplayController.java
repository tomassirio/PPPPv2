package dev.pppp.controller;

import dev.pppp.exception.NotFoundException;
import dev.pppp.service.game.Replays;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A bloodstain's replay: the last seconds before that blackout, a GIF played once (Replays). */
@RestController
public class ReplayController {
    private final Replays replays;

    public ReplayController(Replays replays) {
        this.replays = replays;
    }

    @GetMapping("/api/v1/bloodstains/{id}/replay.gif")
    public ResponseEntity<FileSystemResource> replay(@PathVariable String id) {
        Path file = replays.find(id);
        if (file == null) throw new NotFoundException("No replay for that bloodstain");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(2)).cachePrivate()) // never changes; gone with the stain
                .body(new FileSystemResource(file));
    }
}
