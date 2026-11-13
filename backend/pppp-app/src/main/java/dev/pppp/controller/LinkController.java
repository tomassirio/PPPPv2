package dev.pppp.controller;

import dev.pppp.model.LinkState;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.LinkService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Link cable: the lobby, challenges and the caller's session. Every call answers with the caller's new LinkState. */
@RestController
@RequestMapping("/api/v1/link")
public class LinkController {
    /** kind: TRADE, BATTLE or null (not ready). */
    public record ReadyRequest(String kind) {}
    public record ChallengeRequest(String userId) {}

    private final LinkService link;

    public LinkController(LinkService link) {
        this.link = link;
    }

    @GetMapping
    public LinkState state(Authentication auth) {
        return link.state(CurrentUser.id(auth));
    }

    @PutMapping("/ready")
    public LinkState ready(@RequestBody ReadyRequest req, Authentication auth) {
        link.ready(CurrentUser.id(auth), req.kind());
        return state(auth);
    }

    /** 409 when the other player is not ready or either side is busy. */
    @PostMapping("/challenges")
    public LinkState challenge(@RequestBody ChallengeRequest req, Authentication auth) {
        link.challenge(CurrentUser.id(auth), req.userId() == null ? null : req.userId().toLowerCase());
        return state(auth);
    }

    @PostMapping("/challenges/accept")
    public LinkState accept(Authentication auth) {
        link.accept(CurrentUser.id(auth));
        return state(auth);
    }

    @PostMapping("/challenges/decline")
    public LinkState decline(Authentication auth) {
        link.decline(CurrentUser.id(auth));
        return state(auth);
    }

    @DeleteMapping("/session")
    public LinkState unplug(Authentication auth) {
        link.unplug(CurrentUser.id(auth));
        return state(auth);
    }
}
