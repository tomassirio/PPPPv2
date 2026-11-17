package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.model.ScoreRow;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.exception.NotFoundException;
import dev.pppp.service.AllowlistService;
import dev.pppp.service.CartridgeService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import dev.pppp.service.game.ProgressTracker;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous teaser for the sign-in page: totals about the group, and the trainers on the town map, each with their
 * first name only (no email, no full name, no scores); and the sprites to draw them with.
 */
@RestController
@RequestMapping("/api/v1/public")
class PublicController {
    private final EventPublisher events;
    private final ProgressTracker tracker;
    private final CartridgeService cartridges;
    private final AllowlistService allowlist;

    PublicController(EventPublisher events, ProgressTracker tracker, CartridgeService cartridges,
                     AllowlistService allowlist) {
        this.events = events;
        this.tracker = tracker;
        this.cartridges = cartridges;
        this.allowlist = allowlist;
    }

    /**
     * players: everyone who picked a game; versions: how many play each, by ROM header title ("POKEMON_GLD");
     * caught: Pokémon caught, every Pib's added up; achievements: unlocked, all Pibs together; trades: link
     * trades (each one is a Pokémon received on both ends, so half the received); battles: link battles played (each
     * is a win on one end and a loss on the other, so half of all wins and losses); trainers: everyone on the town
     * map, in map order.
     */
    record Teaser(int online, int players, Map<String, Integer> versions, int caught, int achievements,
                  int trades, int battles, java.util.List<Trainer> trainers) {}

    /**
     * name: the first word of their name; cartridge: for /public/sprites/{cartridge}/...; version: the ROM header
     * title; region, x, y: the town-map spot (x, y in that region's 160x144 screen), region null before their first
     * reading; party: species in order.
     */
    record Trainer(String name, String cartridge, String version, boolean online, String region, int x, int y,
                   java.util.List<Integer> party, dev.pppp.model.MapSpot spot) {}

    @GetMapping("/teaser")
    Teaser teaser() {
        var rows = tracker.scoreboard();
        Map<String, String> titles = cartridges.list().stream()
                .collect(Collectors.toMap(Cartridge::id, Cartridge::title, (a, b) -> a));
        Map<String, Integer> versions = new TreeMap<>();
        rows.forEach(r -> versions.merge(titles.getOrDefault(r.game(), "OTHER"), 1, Integer::sum));
        return new Teaser(events.viewers().size(), rows.size(), versions, tracker.caughtByAll(),
                rows.stream().mapToInt(ScoreRow::achievementsUnlocked).sum(),
                rows.stream().mapToInt(r -> r.link().trades()).sum() / 2,
                rows.stream().mapToInt(r -> r.link().wins() + r.link().losses()).sum() / 2,
                trainers(titles));
    }

    private java.util.List<Trainer> trainers(Map<String, String> titles) {
        Map<String, String> names = allowlist.names();
        return tracker.onMap().stream()
                .filter(t -> t.game() != null)
                .map(t -> new Trainer(firstName(names.get(t.userId())), t.game(), titles.getOrDefault(t.game(), "OTHER"),
                        t.online(), t.pin() == null ? null : t.pin().region(), t.pin() == null ? 0 : t.pin().x(),
                        t.pin() == null ? 0 : t.pin().y(), t.party(), t.spot()))
                .sorted(java.util.Comparator.comparing((Trainer t) -> t.region() == null ? "~" : t.region())
                        .thenComparingInt(Trainer::x).thenComparingInt(Trainer::y))
                .toList();
    }

    /** "Tomas Sirio" -> "Tomas"; never the email. */
    static String firstName(String name) {
        if (name == null || name.isBlank() || name.contains("@")) return "Pib";
        return name.trim().split("\\s+")[0];
    }

    /** The player sprite sheet of a cartridge (16x96, see PokemonGen2#playerSprite). */
    @GetMapping("/sprites/{cartridge}/player.png")
    ResponseEntity<byte[]> player(@PathVariable String cartridge) {
        return png(known(cartridge) ? tracker.playerSpriteOf(cartridge) : null);
    }

    /** A Pokémon's front pic in a cartridge's game, by Pokédex number. */
    @GetMapping("/sprites/{cartridge}/{n}.png")
    ResponseEntity<byte[]> mon(@PathVariable String cartridge, @PathVariable int n) {
        return png(known(cartridge) && n >= 1 && n <= 251 ? tracker.iconOf(cartridge, n) : null);
    }

    /** Margin of border blocks round a map: enough to fill a 4K window at the camera's 3x from any spot on it. */
    static final int MAP_MARGIN = 14;

    /** A map of Crystal as the game draws it, by pret map constant; time: morn, day or nite. */
    @GetMapping("/maps/{map}.png")
    ResponseEntity<byte[]> map(@PathVariable String map,
                               @org.springframework.web.bind.annotation.RequestParam(defaultValue = "day") String time) {
        int t = switch (time) {
            case "morn" -> 0;
            case "day" -> 1;
            case "nite" -> 2;
            default -> -1;
        };
        boolean ok = t >= 0 && map.matches("[A-Z0-9_]{1,40}");
        return png(ok ? tracker.mapArt(map, t, MAP_MARGIN) : null);
    }

    /** Only uploaded cartridges, so made-up ids never reach the profile cache. */
    private boolean known(String cartridge) {
        return cartridges.list().stream().anyMatch(c -> c.id().equals(cartridge));
    }

    private static ResponseEntity<byte[]> png(byte[] png) {
        if (png == null) throw new NotFoundException("No sprite");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(png);
    }
}
