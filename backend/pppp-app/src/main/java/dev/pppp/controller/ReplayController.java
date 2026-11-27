package dev.pppp.controller;

import dev.pppp.exception.NotFoundException;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.Bloodstains;
import dev.pppp.service.game.Replays;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A bloodstain's replay (the last seconds before that blackout, a GIF played once: Replays), and reactions to it. */
@RestController
public class ReplayController {
    public record React(String reaction) {}

    private final Replays replays;
    private final Bloodstains stains;

    public ReplayController(Replays replays, Bloodstains stains) {
        this.replays = replays;
        this.stains = stains;
    }

    /** One of the chat's six faces on someone else's stain; the same one again takes it back. */
    @PostMapping("/api/v1/bloodstains/{id}/reaction")
    public void react(@PathVariable String id, @RequestBody React req, Authentication auth) {
        stains.react(CurrentUser.id(auth), id, req.reaction());
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
