package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.model.ScoreRow;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.exception.NotFoundException;
import dev.pppp.service.AllowlistService;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.PlayerSprites;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final PlayerConsoles consoles;

    PublicController(EventPublisher events, ProgressTracker tracker, CartridgeService cartridges,
                     AllowlistService allowlist, PlayerConsoles consoles) {
        this.consoles = consoles;
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
     * map, in map order; moments: the latest catches, badges, achievements and Hall of Fame entries, newest first.
     */
    record Teaser(int online, int players, Map<String, Integer> versions, int caught, int achievements,
                  int trades, int battles, java.util.List<Trainer> trainers, java.util.List<Moment> moments) {}

    /**
     * trainer: index in trainers (who it was, without saying more than their first name); type: CAUGHT, BADGE,
     * ACHIEVEMENT or HALL_OF_FAME; detail: the species, badge or achievement name; at: epoch millis.
     */
    record Moment(int trainer, String type, String detail, long at) {}

    static final java.util.Set<String> MOMENT_TYPES = java.util.Set.of("CAUGHT", "BADGE", "ACHIEVEMENT", "HALL_OF_FAME");

    /**
     * name: the first word of their name; cartridge: for /public/sprites/{cartridge}/...; version: the ROM header
     * title; region, x, y: the town-map spot (x, y in that region's 160x144 screen), region null before their first
     * reading; party: species in order; title, badges (Johto and Kanto), caught, playTime ("H:MM" or null) and
     * location (the landmark, or null) for the card shown over their name; sprite: the trainer they walk as
     * (PlayerSprites key, null for the game's own), for /public/sprites/{cartridge}/player.png?sprite=.
     */
    record Trainer(String name, String cartridge, String version, boolean online, String region, int x, int y,
                   java.util.List<Integer> party, dev.pppp.model.MapSpot spot, String title, int badges, int caught,
                   String playTime, String location, String sprite) {}

    @GetMapping("/teaser")
    Teaser teaser() {
        var rows = tracker.scoreboard();
        Map<String, String> titles = cartridges.list().stream()
                .collect(Collectors.toMap(Cartridge::id, Cartridge::title, (a, b) -> a));
        Map<String, Integer> versions = new TreeMap<>();
        rows.forEach(r -> versions.merge(titles.getOrDefault(r.game(), "OTHER"), 1, Integer::sum));
        var ids = new java.util.ArrayList<String>();
        return new Teaser(events.viewers().size(), rows.size(), versions, tracker.caughtByAll(),
                rows.stream().mapToInt(ScoreRow::achievementsUnlocked).sum(),
                rows.stream().mapToInt(r -> r.link().trades()).sum() / 2,
                rows.stream().mapToInt(r -> r.link().wins() + r.link().losses()).sum() / 2,
                trainers(titles, rows, ids), moments(ids));
    }

    /** User ids in trainers' order, filled by trainers() for moments(); never sent. */
    private java.util.List<Trainer> trainers(Map<String, String> titles, java.util.List<ScoreRow> rows,
                                             java.util.List<String> ids) {
        Map<String, String> names = allowlist.names();
        Map<String, ScoreRow> byId = rows.stream().collect(Collectors.toMap(ScoreRow::userId, r -> r, (a, b) -> a));
        record Placed(String userId, Trainer trainer) {}
        var placed = tracker.onMap().stream()
                .filter(t -> t.game() != null)
                .map(t -> {
                    ScoreRow r = byId.get(t.userId());
                    String location = r == null || r.location() == null ? null : r.location().split(" · ")[0];
                    return new Placed(t.userId(), new Trainer(firstName(names.get(t.userId())), t.game(),
                            titles.getOrDefault(t.game(), "OTHER"), t.online(),
                            t.pin() == null ? null : t.pin().region(), t.pin() == null ? 0 : t.pin().x(),
                            t.pin() == null ? 0 : t.pin().y(), t.party(), t.spot(), r == null ? null : r.title(),
                            r == null ? 0 : r.badges(), r == null ? 0 : r.caught(), r == null ? null : r.playTime(),
                            location, sprite(t.userId())));
                })
                .sorted(java.util.Comparator.comparing((Placed p) -> p.trainer().region() == null ? "~" : p.trainer().region())
                        .thenComparingInt(p -> p.trainer().x()).thenComparingInt(p -> p.trainer().y()))
                .toList();
        placed.forEach(p -> ids.add(p.userId()));
        return placed.stream().map(Placed::trainer).toList();
    }

    private java.util.List<Moment> moments(java.util.List<String> ids) {
        return tracker.activity(100).stream()
                .filter(e -> MOMENT_TYPES.contains(e.type()) && ids.contains(e.user()))
                .limit(12)
                .map(e -> new Moment(ids.indexOf(e.user()), e.type(), e.detail(), e.at()))
                .toList();
    }

    /** "Tomas Sirio" -> "Tomas"; never the email. */
    static String firstName(String name) {
        if (name == null || name.isBlank() || name.contains("@")) return "Pib";
        return name.trim().split("\\s+")[0];
    }

    /** The player sprite sheet of a cartridge (16x96, see PokemonGen2#playerSprite); with sprite, that trainer's. */
    @GetMapping("/sprites/{cartridge}/player.png")
    ResponseEntity<byte[]> player(@PathVariable String cartridge,
                                  @RequestParam(required = false) String sprite) {
        if (!known(cartridge)) return png(null);
        return png(sprite == null || sprite.isBlank() ? tracker.playerSpriteOf(cartridge) : consoles.spriteSheetOf(cartridge, sprite));
    }

    private String sprite(String userId) {
        String key = consoles.sprite(userId);
        return PlayerSprites.DEFAULT.equals(key) ? null : key;
    }

    /** A Pokémon's front pic in a cartridge's game, by Pokédex number. */
    @GetMapping("/sprites/{cartridge}/{n}.png")
    ResponseEntity<byte[]> mon(@PathVariable String cartridge, @PathVariable int n) {
        return png(known(cartridge) && n >= 1 && n <= 251 ? tracker.iconOf(cartridge, n) : null);
    }

    /** Margin of border blocks round a map: fills a 4K window at 3x from any spot, and a pan to a neighbour. */
    static final int MAP_MARGIN = 20;

    /** A map of Crystal as the game draws it, by pret map constant; time: morn, day or nite. */
    @GetMapping("/maps/{map}.png")
    ResponseEntity<byte[]> map(@PathVariable String map,
                               @RequestParam(defaultValue = "day") String time) {
        int t = time(time);
        boolean ok = t >= 0 && map.matches("[A-Z0-9_]{1,40}");
        return png(ok ? tracker.mapArt(map, t, MAP_MARGIN) : null);
    }

    /** The Pokégear town map of a region ("johto" or "kanto"), drawn from Crystal. */
    @GetMapping("/townmap/{region}.png")
    ResponseEntity<byte[]> townMap(@PathVariable String region) {
        boolean ok = region.equals("johto") || region.equals("kanto");
        return png(ok ? tracker.townMap(region.equals("kanto")) : null);
    }

    /** What stands on that map image at a time of day: neighbours, NPCs, tall grass, wild Pokémon (Gen2MapArt.Info). */
    @GetMapping("/maps/{map}.json")
    ResponseEntity<Object> mapInfo(@PathVariable String map,
                                   @RequestParam(defaultValue = "day") String time) {
        int t = time(time);
        Object info = t >= 0 && map.matches("[A-Z0-9_]{1,40}") ? tracker.mapInfo(map, t, MAP_MARGIN) : null;
        if (info == null) throw new NotFoundException("No map " + map);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic()).body(info);
    }

    /** A Crystal overworld sprite's sheet (Gen2MapArt#npcSheet) in palette 0-7. */
    @GetMapping("/npcs/{sprite}/{palette}.png")
    ResponseEntity<byte[]> npc(@PathVariable int sprite, @PathVariable int palette,
                               @RequestParam(defaultValue = "day") String time) {
        int t = time(time);
        return png(t >= 0 ? tracker.npcSheet(sprite, palette, t) : null);
    }

    private static int time(String time) {
        return switch (time) {
            case "morn" -> 0;
            case "day" -> 1;
            case "nite" -> 2;
            default -> -1;
        };
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
