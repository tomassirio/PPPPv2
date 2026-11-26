package dev.pppp.controller;

import dev.pppp.exception.NotFoundException;
import dev.pppp.model.Cartridge;
import dev.pppp.model.KeyBindings;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.ChatService;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.KeyBindingService;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.ProgressTracker;
import dev.pppp.service.PlayerResetService;
import dev.pppp.service.game.PlayerSprites;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in player's own game (the one-time pick, their console's buttons, their key bindings) and the admin's reset of a player. */
@RestController
public class PlayerController {
    /** game is null until picked; available is what the admin has uploaded; speed is null while the console is off. */
    public record MyGame(String game, List<Cartridge> available, Double speed) {}
    public record Speed(double speed) {}
    /** speed: picked; actual: measured over the last second; timing: where that second went (null while off). */
    public record SpeedNow(Double speed, Double actual, dev.pppp.emulator.EmulatorHost.Timing timing) {}
    public record Pick(String cartridgeId) {}
    public record InputRequest(String command, Integer repeat) {}
    public record Konami(boolean gift) {}

    private final PlayerConsoles consoles;
    private final CartridgeService cartridges;
    private final PlayerResetService resets;
    private final KeyBindingService keys;
    private final EventPublisher events;
    private final ChatService chat;
    private final dev.pppp.service.game.PokecenterDig dig;
    private final ProgressTracker tracker;
    private final dev.pppp.service.LinkService link;
    private final dev.pppp.service.game.MysteryGifts gifts;

    public PlayerController(PlayerConsoles consoles, CartridgeService cartridges, PlayerResetService resets, KeyBindingService keys,
                            EventPublisher events, ChatService chat, dev.pppp.service.game.PokecenterDig dig,
                            ProgressTracker tracker, dev.pppp.service.LinkService link, dev.pppp.service.game.MysteryGifts gifts) {
        this.gifts = gifts;
        this.dig = dig;
        this.tracker = tracker;
        this.link = link;
        this.keys = keys;
        this.events = events;
        this.chat = chat;
        this.consoles = consoles;
        this.cartridges = cartridges;
        this.resets = resets;
    }

    @GetMapping("/api/v1/me/game")
    public MyGame game(Authentication auth) {
        String id = CurrentUser.id(auth);
        return new MyGame(consoles.game(id), cartridges.list(), consoles.speed(id));
    }

    /** 409 when the player already picked another game. */
    @PutMapping("/api/v1/me/game")
    public MyGame pick(@RequestBody Pick req, Authentication auth) {
        consoles.pick(CurrentUser.id(auth), req.cartridgeId());
        return game(auth);
    }

    /** 1, 2 or 4 (400 otherwise); 409 while the console is off or a link cable is plugged in. */
    /** The Konami code was entered on /play: gift true when this was the first time and the prima donna is on her way. */
    @PostMapping("/api/v1/me/konami")
    public Konami konami(Authentication auth) {
        return new Konami(gifts.konami(CurrentUser.id(auth)));
    }

    @GetMapping("/api/v1/me/speed")
    public SpeedNow speedNow(Authentication auth) {
        String id = CurrentUser.id(auth);
        return new SpeedNow(consoles.speed(id), consoles.measuredSpeed(id), consoles.timing(id));
    }

    @PutMapping("/api/v1/me/speed")
    public Speed speed(@RequestBody Speed req, Authentication auth) {
        consoles.setSpeed(CurrentUser.id(auth), req.speed());
        return new Speed(req.speed());
    }

    /** The player's key bindings (the arrows layout until they change it). */
    @GetMapping("/api/v1/me/keys")
    public KeyBindings keys(Authentication auth) {
        return keys.get(CurrentUser.id(auth));
    }

    /** All eight buttons, a different key each; 400 for a missing, repeated or reserved (Escape, Tab) key. */
    @PutMapping("/api/v1/me/keys")
    public KeyBindings setKeys(@RequestBody KeyBindings req, Authentication auth) {
        return keys.set(CurrentUser.id(auth), req);
    }

    @PostMapping("/api/v1/me/inputs")
    public ResponseEntity<Void> press(@RequestBody InputRequest req, Authentication auth) {
        consoles.press(CurrentUser.id(auth), req.command(), req.repeat() == null ? 1 : req.repeat());
        return ResponseEntity.accepted().build();
    }

    /** Admin: wipes the player's pick, saves, progress, points and activity, so they pick a game again. */
    /** The trainer the player walks as and its colour (null: its own), the trainers their game has, the colours. */
    public record SpriteState(String current, String color, List<PlayerSprites.Choice> choices, List<String> colors) {}

    /** color absent or null: the trainer's own colours. */
    public record SpriteRequest(String key, String color) {}

    @GetMapping("/api/v1/me/sprite")
    public SpriteState sprite(Authentication auth) {
        String me = CurrentUser.id(auth);
        return state(me);
    }

    private SpriteState state(String me) {
        return new SpriteState(consoles.sprite(me), consoles.spriteColor(me), consoles.spriteChoices(me), PlayerSprites.COLORS);
    }

    /** Walks as that trainer from now on (refused while linked); the game shows it from the next map it loads. */
    @PutMapping("/api/v1/me/sprite")
    public SpriteState pickSprite(@RequestBody SpriteRequest req, Authentication auth) {
        String me = CurrentUser.id(auth);
        consoles.pickSprite(me, req == null ? null : req.key(), req == null ? null : req.color());
        return state(me);
    }

    /** A trainer of the caller's game as a 16x96 sprite sheet (six 16x16 frames: down, up, left, then walking). */
    @GetMapping("/api/v1/me/sprites/{key}.png")
    public ResponseEntity<byte[]> spriteSheet(@PathVariable String key, @RequestParam(required = false) String color,
                                              Authentication auth) {
        byte[] png = consoles.spriteSheet(CurrentUser.id(auth), key, color);
        if (png == null) throw new NotFoundException("No trainer " + key);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate()).body(png);
    }

    /** The reset button on the player's own Game Boy: boots it again from the game's own save (unplugs a link). */
    @PostMapping("/api/v1/me/reset")
    public ResponseEntity<Void> reset(Authentication auth) {
        consoles.reset(CurrentUser.id(auth));
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/api/v1/admin/players/{userId}")
    public ResponseEntity<Void> reset(@PathVariable String userId, Authentication auth) {
        resets.reset(CurrentUser.id(auth), userId.toLowerCase(java.util.Locale.ROOT));
        return ResponseEntity.noContent().build();
    }

    /**
     * A player's link cable, for admins. mode: AUTO (their game decides: Elm got the Mystery Egg), OPEN or LOCKED (an
     * admin's setting); gameStep: their game's way to the Cable Club (0-3, 3 open; -1 no such game); open: whether
     * they can link now; linkedWith / ready: their session and lobby. note: why OPEN could not set the game's flag.
     */
    public record CableClub(String mode, int gameStep, boolean open, String linkedWith, String ready, String note) {}

    public record CableClubRequest(String mode) {}

    @GetMapping("/api/v1/admin/players/{userId}/cable-club")
    public CableClub cableClub(@PathVariable String userId) {
        return cableClubOf(userId.toLowerCase(java.util.Locale.ROOT), null);
    }

    /** Sets AUTO, OPEN or LOCKED; OPEN also sets the game's own flag when their console is free (they save to keep it). */
    @PutMapping("/api/v1/admin/players/{userId}/cable-club")
    public CableClub setCableClub(@PathVariable String userId, @RequestBody CableClubRequest body, Authentication auth) {
        String id = userId.toLowerCase(java.util.Locale.ROOT);
        String mode = body == null || body.mode() == null ? "AUTO" : body.mode().toUpperCase(java.util.Locale.ROOT);
        tracker.setCableClubMode(id, mode.equals("AUTO") ? null : mode);
        String note = null;
        if (mode.equals(ProgressTracker.OPEN) && tracker.gameCableClub(id) >= 0 && tracker.gameCableClub(id) < 3) {
            note = consoles.isAwake(id) ? dig.openCableClub(id) : "their Game Boy is not running";
            if (note != null) note = "Their game's own Cable Club flag is not set yet (" + note + "): set Open again later";
        }
        chat.postAdmin(CurrentUser.id(auth) + " set " + id + "'s Cable Club to " + mode.toLowerCase(java.util.Locale.ROOT));
        return cableClubOf(id, note);
    }

    private CableClub cableClubOf(String id, String note) {
        String mode = tracker.cableClubMode(id);
        return new CableClub(mode == null ? "AUTO" : mode, tracker.gameCableClub(id), tracker.cableClubOpen(id),
                consoles.linkedWith(id), link.state(id).ready(), note);
    }

    public record GodMessage(String text) {}

    /**
     * An admin's message shown only to that player, over whatever page they have open (GOD_MESSAGE {text, at, user}); admins get a copy, shown
     * only on that player's screen in the admin view.
     * Not stored: 409 when they have no tab open. Other admins see a line in the admin chat.
     */
    @PostMapping("/api/v1/admin/players/{userId}/god-message")
    public ResponseEntity<Void> godMessage(@PathVariable String userId, @RequestBody GodMessage body, Authentication auth) {
        String text = body == null || body.text() == null ? "" : body.text().strip();
        if (text.isEmpty() || text.length() > 200) throw new IllegalArgumentException("A message of 1 to 200 characters");
        String to = userId.toLowerCase(java.util.Locale.ROOT);
        var message = java.util.Map.of("text", text, "at", System.currentTimeMillis(), "user", to);
        if (!events.publishTo(to, "GOD_MESSAGE", message)) {
            throw new IllegalStateException(to + " has no tab open");
        }
        events.publishToAdmins("GOD_MESSAGE", message); // admins watching that player's screen see it there too
        chat.postAdmin(CurrentUser.id(auth) + " sent a god message to " + to + ": " + text);
        return ResponseEntity.noContent().build();
    }
}
