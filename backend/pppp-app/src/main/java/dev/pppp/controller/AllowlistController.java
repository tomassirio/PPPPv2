package dev.pppp.controller;

import dev.pppp.model.AccessRequest;
import dev.pppp.model.Role;
import dev.pppp.model.User;
import dev.pppp.security.CurrentUser;
import dev.pppp.exception.NotFoundException;
import dev.pppp.service.AllowlistService;
import dev.pppp.service.PlayerResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /me plus the admin allowlist and user endpoints. User id = email, so both share AllowlistService. */
@RestController
@RequestMapping("/api/v1")
public class AllowlistController {
    public record Entry(@NotBlank @Email String email, @NotNull Role role) {}
    public record RoleChange(@NotNull Role role) {}
    /**
     * picture: Google profile photo URL, null for non-OIDC logins or accounts without one. name: their Google name;
     * nickname: what others see them as (their nickname, else that name); username: "@" and the e-mail's local part.
     */
    public record Me(String id, String name, String picture, List<String> roles, String nickname, String username) {}

    public record NicknameRequest(String nickname) {}

    private final AllowlistService allowlist;
    private final PlayerResetService resets;

    public AllowlistController(AllowlistService allowlist, PlayerResetService resets) {
        this.allowlist = allowlist;
        this.resets = resets;
    }

    /** The signed-in player, or 204 No Content for a visitor who is not signed in (a player only: anything else is 204 too). */
    @GetMapping("/me")
    public org.springframework.http.ResponseEntity<Me> me(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_PLAYER".equals(a.getAuthority()))) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
        var v = CurrentUser.viewer(auth);
        allowlist.rememberProfile(v.id(), v.name(), v.picture());
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        return org.springframework.http.ResponseEntity.ok(
                new Me(v.id(), v.name(), v.picture(), roles, allowlist.displayName(v.id()), AllowlistService.username(v.id())));
    }

    /** Sets the caller's nickname (blank: back to their Google name); answers the name they are now shown as. */
    @org.springframework.web.bind.annotation.PutMapping("/me/nickname")
    public java.util.Map<String, String> nickname(@org.springframework.web.bind.annotation.RequestBody NicknameRequest req,
                                                  Authentication auth) {
        String id = CurrentUser.id(auth);
        return java.util.Map.of("nickname", allowlist.setNickname(id, req == null ? null : req.nickname()),
                "username", AllowlistService.username(id));
    }

    /** Player id -> Google profile photo URL, for wherever players are listed. */
    @GetMapping("/players/pictures")
    public java.util.Map<String, String> pictures() {
        return allowlist.pictures();
    }

    /** Player id -> Google full name, for players who signed in since names were kept. */
    @GetMapping("/players/names")
    public java.util.Map<String, String> names() {
        return allowlist.names();
    }

    @GetMapping("/admin/allowlist")
    public List<Entry> allowlist() {
        return allowlist.list().stream().map(u -> new Entry(u.id(), u.role())).toList();
    }

    @PostMapping("/admin/allowlist")
    @ResponseStatus(HttpStatus.CREATED)
    public Entry add(@Valid @RequestBody Entry e) {
        allowlist.put(e.email(), e.role());
        return new Entry(AllowlistService.normalize(e.email()), e.role());
    }

    @PutMapping({"/admin/allowlist/{email}", "/admin/users/{email}/role"})
    public Entry changeRole(@PathVariable String email, @Valid @RequestBody RoleChange c) {
        allowlist.changeRole(email, c.role());
        return new Entry(AllowlistService.normalize(email), c.role());
    }

    @DeleteMapping("/admin/allowlist/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String email, Authentication auth) {
        allowlist.remove(email);
        // Removed means gone: their run goes too (a ban keeps it, since a ban can be lifted).
        try {
            resets.reset(CurrentUser.id(auth), AllowlistService.normalize(email));
        } catch (NotFoundException noRun) {
            // never picked a game
        }
    }

    @GetMapping("/admin/access-requests")
    public List<AccessRequest> accessRequests() {
        return allowlist.accessRequests();
    }

    @PostMapping("/admin/access-requests/{email}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable String email, @Valid @RequestBody RoleChange c) {
        allowlist.accept(email, c.role());
    }

    @DeleteMapping("/admin/access-requests/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable String email) {
        allowlist.reject(email);
    }

    @GetMapping("/admin/users")
    public List<User> users() {
        return allowlist.list();
    }

    @PostMapping("/admin/users/{email}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable String email) {
        allowlist.ban(email);
    }

    @DeleteMapping("/admin/users/{email}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable String email) {
        allowlist.unban(email);
    }
}
