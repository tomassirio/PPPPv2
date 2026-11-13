package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in player's own game: the one-time pick and the buttons of their private console. */
@RestController
public class PlayerController {
    /** game is null until picked; available is what the admin has uploaded. */
    public record MyGame(String game, List<Cartridge> available) {}
    public record Pick(String cartridgeId) {}
    public record InputRequest(String command, Integer repeat, Boolean walk) {}

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;

    public PlayerController(PlayerConsoles consoles, CartridgeService cartridges) {
        this.consoles = consoles;
        this.cartridges = cartridges;
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
}
