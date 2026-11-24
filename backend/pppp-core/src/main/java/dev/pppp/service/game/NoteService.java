package dev.pppp.service.game;

import dev.pppp.model.Note;
import dev.pppp.repository.NoteRepository;
import dev.pppp.service.CartridgeService;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * Notes left on Johto's tiles, Dark Souls style: a phrase from PHRASES with one word from WORDS (the 251 Pokémon as
 * the ROM names them, items, things), so they need no moderation. A player leaves one where they stand, free in the
 * overworld (Ghosts.where); they show on every Gold, Silver and Crystal screen that has the tile in view (visible,
 * sent with the screen's name tags) and read out to whoever stands on it, who can rate it Good. A player keeps their
 * MAX_PER_PLAYER newest, one per tile. All notes stay in memory (a few hundred at most); Redis keeps them.
 */
@Service
public class NoteService {
    public static final List<String> PHRASES = List.of("____ ahead", "Beware of ____", "Try ____", "Need ____",
            "Could this be ____?", "If only I had ____...", "Visions of ____...", "Praise ____!", "No ____ ahead",
            "Time for ____");
    static final List<String> ITEMS = List.of("Potion", "Super Potion", "Hyper Potion", "Revive", "Full Heal",
            "Antidote", "Awakening", "Paralyze Heal", "Escape Rope", "Repel", "Super Repel", "Poké Ball", "Great Ball",
            "Ultra Ball", "Rare Candy", "Fire Stone", "Water Stone", "Thunderstone", "Leaf Stone", "Moon Stone",
            "Sun Stone", "Berry", "Bicycle", "Itemfinder", "Squirtbottle", "Old Rod", "Good Rod", "Super Rod", "Cut",
            "Fly", "Surf", "Strength", "Flash", "Whirlpool", "Waterfall", "Rock Smash", "Headbutt");
    static final List<String> THINGS = List.of("a hidden item", "an item", "tall grass", "a trainer", "a strong foe",
            "an ambush", "a dead end", "a shortcut", "a secret", "a ledge", "a puzzle", "darkness", "water", "a cave",
            "the Gym Leader", "the rival", "Team Rocket", "a shiny", "a legend", "treasure", "healing", "saving",
            "patience", "running away", "grinding", "fishing", "a trade", "a link battle", "Mt. Silver", "AAAA",
            "friendship", "despair", "victory", "good luck");
    static final int MAX_PER_PLAYER = 10;
    private static final int STEP = 16, SCREEN_W = 160, SCREEN_H = 144;

    /** The composer's lists: phrases ("____" is the word) and the words by category. */
    public record Words(List<String> phrases, Map<String, List<String>> words) {}

    /** A note on the player's screen: its sprite-sized tile's top left in screen pixels; here: they stand on it. */
    public record Shown(String id, String user, int x, int y, String text, int good, boolean mine, boolean rated, boolean here) {}

    private final NoteRepository repo;
    private final Ghosts ghosts;
    private final CartridgeService cartridges;
    private final List<Note> notes = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> raters = new ConcurrentHashMap<>();
    private volatile Words words;
    private volatile boolean loaded;

    public NoteService(NoteRepository repo, Ghosts ghosts, CartridgeService cartridges) {
        this.repo = repo;
        this.ghosts = ghosts;
        this.cartridges = cartridges;
    }

    public Words words() {
        Words w = words;
        if (w == null) {
            Map<String, List<String>> lists = new LinkedHashMap<>();
            lists.put("Pokémon", pokemon());
            lists.put("Items", ITEMS);
            lists.put("Things", THINGS);
            w = new Words(PHRASES, lists);
            if (!lists.get("Pokémon").isEmpty()) words = w; // no cartridge yet: ask again next time
        }
        return w;
    }

    /** The 251 names from the first Gold, Silver or Crystal cartridge uploaded (all three name them alike). */
    private List<String> pokemon() {
        for (var c : cartridges.list()) {
            try {
                PokemonGen2 g = PokemonGen2.forRom(Files.readAllBytes(cartridges.require(c.id())));
                if (g != null) return g.speciesNames();
            } catch (Exception ignored) {
                // next cartridge
            }
        }
        return List.of();
    }

    /** The tile the player stands still on, free in the overworld; IllegalStateException (409) otherwise. */
    public Ghosts.Where spot(String userId) {
        Ghosts.Where w = ghosts.where(userId);
        if (w == null || w.x() % STEP != 0 || w.y() % STEP != 0) {
            throw new IllegalStateException("Stand still somewhere in the overworld to leave a note");
        }
        return w;
    }

    /** Leaves "phrase with word" where the player stands, replacing their own note on that tile. */
    public Note leave(String userId, int phrase, String word) {
        if (phrase < 0 || phrase >= PHRASES.size()) throw new IllegalArgumentException("No such phrase");
        if (word == null || words().words().values().stream().noneMatch(l -> l.contains(word))) {
            throw new IllegalArgumentException("No such word");
        }
        Ghosts.Where w = spot(userId);
        int x = w.x() / STEP, y = w.y() / STEP;
        load();
        List<Note> mine = new ArrayList<>(notes.stream().filter(n -> n.user().equals(userId)).toList());
        for (Note n : mine) {
            if (n.map().equals(w.map()) && n.x() == x && n.y() == y) remove(n.id());
        }
        mine.removeIf(n -> n.map().equals(w.map()) && n.x() == x && n.y() == y);
        mine.sort(Comparator.comparingLong(Note::at));
        while (mine.size() >= MAX_PER_PLAYER) remove(mine.removeFirst().id());
        Note note = new Note(UUID.randomUUID().toString(), userId, w.map(), x, y,
                PHRASES.get(phrase).replace("____", word), System.currentTimeMillis());
        repo.save(note);
        notes.add(note);
        return note;
    }

    /** Rates someone else's note Good, once. */
    public void rate(String userId, String id) {
        Note n = find(id);
        if (n.user().equals(userId)) throw new IllegalArgumentException("That's your own note");
        repo.good(id, userId);
        raters(id).add(userId);
    }

    /** The author or an admin removes a note. */
    public void delete(String userId, boolean admin, String id) {
        Note n = find(id);
        if (!admin && !n.user().equals(userId)) throw new dev.pppp.exception.ForbiddenException("Not your note");
        remove(id);
    }

    /** The notes in view on the player's screen this frame (none in a menu, a battle, a link room). */
    public List<Shown> visible(String userId) {
        Ghosts.Where w = ghosts.where(userId);
        if (w == null) return List.of();
        load();
        List<Shown> out = new ArrayList<>();
        for (Note n : notes) {
            if (!n.map().equals(w.map())) continue;
            int sx = Ghosts.SCREEN_X + n.x() * STEP - w.x(), sy = Ghosts.SCREEN_Y + n.y() * STEP - w.y();
            if (sx <= -STEP || sx >= SCREEN_W || sy <= -STEP || sy >= SCREEN_H) continue;
            Set<String> r = raters(n.id());
            out.add(new Shown(n.id(), n.user(), sx, sy, n.text(), r.size(), n.user().equals(userId), r.contains(userId),
                    n.x() * STEP == w.x() && n.y() * STEP == w.y()));
        }
        return out;
    }

    private Note find(String id) {
        load();
        return notes.stream().filter(n -> n.id().equals(id)).findFirst()
                .orElseThrow(() -> new dev.pppp.exception.NotFoundException("No such note"));
    }

    private void remove(String id) {
        repo.delete(id);
        notes.removeIf(n -> n.id().equals(id));
        raters.remove(id);
    }

    private Set<String> raters(String id) {
        return raters.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    }

    private synchronized void load() {
        if (loaded) return;
        for (Note n : repo.all()) {
            notes.add(n);
            raters(n.id()).addAll(repo.raters(n.id()));
        }
        loaded = true;
    }
}
