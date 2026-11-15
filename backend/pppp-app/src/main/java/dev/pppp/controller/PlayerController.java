package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.PlayerResetService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in player's own game (the one-time pick, their console's buttons) and the admin's reset of a player. */
@RestController
public class PlayerController {
    /** game is null until picked; available is what the admin has uploaded. */
    public record MyGame(String game, List<Cartridge> available) {}
    public record Pick(String cartridgeId) {}
    public record InputRequest(String command, Integer repeat, Boolean walk) {}

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final PlayerResetService resets;

    public PlayerController(PlayerConsoles consoles, CartridgeService cartridges, PlayerResetService resets) {
        this.consoles = consoles;
        this.cartridges = cartridges;
        this.resets = resets;
    }

    @GetMapping("/api/v1/me/game")
    public MyGame game(Authentication auth) {
        return new MyGame(consoles.game(CurrentUser.id(auth)), cartridges.list());
    }

    /** 409 when the player already picked another game. */
    @PutMapping("/api/v1/me/game")
    public MyGame pick(@RequestBody Pick req, Authentication auth) {
        consoles.pick(CurrentUser.id(auth), req.cartridgeId());
        return game(auth);
    }

    @PostMapping("/api/v1/me/inputs")
    public ResponseEntity<Void> press(@RequestBody InputRequest req, Authentication auth) {
        consoles.press(CurrentUser.id(auth), req.command(), req.repeat() == null ? 1 : req.repeat(),
                Boolean.TRUE.equals(req.walk()));
        return ResponseEntity.accepted().build();
    }

    /** Admin: wipes the player's pick, saves, progress, points and activity, so they pick a game again. */
    @DeleteMapping("/api/v1/admin/players/{userId}")
    public ResponseEntity<Void> reset(@PathVariable String userId, Authentication auth) {
        resets.reset(CurrentUser.id(auth), userId.toLowerCase(java.util.Locale.ROOT));
        return ResponseEntity.noContent().build();
    }
}
