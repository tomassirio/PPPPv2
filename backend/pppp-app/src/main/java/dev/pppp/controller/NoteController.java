package dev.pppp.controller;

import dev.pppp.model.Note;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.NoteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Notes left on Johto's tiles (NoteService). They reach screens with the name tags (/ws/screen). */
@RestController
public class NoteController {
    public record Leave(int phrase, String word) {}
    /** Where a note would go: the map constant and tile. */
    public record Spot(String map, int x, int y) {}

    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping("/api/v1/notes/words")
    public NoteService.Words words() {
        return notes.words();
    }

    /** 409 unless the player stands still, free in the overworld. */
    @GetMapping("/api/v1/notes/spot")
    public Spot spot(Authentication auth) {
        var w = notes.spot(CurrentUser.id(auth));
        return new Spot(w.map(), w.x() / 16, w.y() / 16);
    }

    @PostMapping("/api/v1/notes")
    public Note leave(@RequestBody Leave req, Authentication auth) {
        return notes.leave(CurrentUser.id(auth), req.phrase(), req.word());
    }

    @PostMapping("/api/v1/notes/{id}/good")
    public void good(@PathVariable String id, Authentication auth) {
        notes.rate(CurrentUser.id(auth), id);
    }

    @DeleteMapping("/api/v1/notes/{id}")
    public void delete(@PathVariable String id, Authentication auth) {
        notes.delete(CurrentUser.id(auth), CurrentUser.isAdmin(auth), id);
    }
}
