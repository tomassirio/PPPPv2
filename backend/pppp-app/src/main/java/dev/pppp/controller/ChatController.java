package dev.pppp.controller;

import dev.pppp.model.ChatMessage;
import dev.pppp.service.ChatService;

import dev.pppp.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ChatController {
    record SendRequest(String text) {}
    record ReactRequest(String reaction) {}
    record MuteRequest(@Min(1) @Max(525600) int minutes) {}

    record Cleared(int removed) {}

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** Fresh start: all system and admin lines out of chat. */
    @DeleteMapping("/admin/chat/system-messages")
    Cleared clearSystem(Authentication auth) {
        return new Cleared(chat.clearSystem(CurrentUser.id(auth)));
    }

    @PostMapping("/chat/messages")
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessage send(@RequestBody SendRequest body, Authentication auth) {
        String id = CurrentUser.id(auth);
        String name = auth.getPrincipal() instanceof OidcUser oidc && oidc.getFullName() != null ? oidc.getFullName() : id;
        return chat.send(id, name, body.text());
    }

    @GetMapping("/chat/messages")
    List<ChatMessage> history(@RequestParam(required = false) String before,
                              @RequestParam(defaultValue = "50") int limit, Authentication auth) {
        return chat.history(before, limit, CurrentUser.isAdmin(auth));
    }

    /** Your one reaction to a line; returns the line's reactions (reaction -> user ids). */
    @PutMapping("/chat/messages/{id}/reaction")
    Map<String, List<String>> react(@PathVariable String id, @RequestBody ReactRequest body, Authentication auth) {
        return chat.react(CurrentUser.id(auth), id, body.reaction());
    }

    /** A reaction floating over a linked player's screen for everyone watching on /watch. */
    @PostMapping("/watch/{userId}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cheer(@PathVariable String userId, @RequestBody ReactRequest body, Authentication auth) {
        chat.cheer(CurrentUser.id(auth), userId, body.reaction());
    }

    @DeleteMapping("/chat/messages/{id}/reaction")
    Map<String, List<String>> unreact(@PathVariable String id, Authentication auth) {
        return chat.unreact(CurrentUser.id(auth), id);
    }

    @DeleteMapping("/admin/chat/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { chat.delete(id); }

    @PostMapping("/admin/users/{id}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mute(@PathVariable String id, @Valid @RequestBody MuteRequest body) {
        chat.mute(id.toLowerCase(Locale.ROOT), body.minutes());
    }

    @DeleteMapping("/admin/users/{id}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unmute(@PathVariable String id) {
        chat.unmute(id.toLowerCase(Locale.ROOT));
    }
}
