package dev.pppp.service;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.Settings;
import dev.pppp.repository.CartridgeRepository;
import dev.pppp.repository.PlayerRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * One private Game Boy per player. A console boots the first time its player opens it and then stays in
 * memory: paused, with its state parked on disk, whenever the player is away. The game is picked once.
 */
@Service
public class PlayerConsoles {
    private static final Logger log = LoggerFactory.getLogger(PlayerConsoles.class);
    private static final Set<Button> DIRECTIONS = EnumSet.of(Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT);
    /** Commands waiting behind the one being pressed; more are dropped, so a held key cannot build a backlog. */
    private static final int MAX_PENDING = 3;

    private record Console(EmulatorHost host, String cartridgeId, ExecutorService presses, AtomicInteger pending) {}

    private final Map<String, Console> consoles = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, int[]>> frameListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, short[]>> audioListeners = new CopyOnWriteArrayList<>();
    private final PlayerRepository players;
    private final CartridgeService cartridges;
    private final CartridgeRepository files;
    private final SettingsService settings;

    public PlayerConsoles(PlayerRepository players, CartridgeService cartridges, CartridgeRepository files,
                          SettingsService settings) {
        this.players = players;
        this.cartridges = cartridges;
        this.files = files;
        this.settings = settings;
    }

    /** The player's cartridge id, or null before they picked. */
    public String game(String userId) {
        return players.game(userId);
    }

    /** Picks the player's game, once. NotFoundException for an unknown cartridge, IllegalStateException on a second pick. */
    public void pick(String userId, String cartridgeId) {
        cartridges.require(cartridgeId);
        if (players.pickGame(userId, cartridgeId)) return;
        String game = players.game(userId);
        if (!cartridgeId.equals(game)) throw new IllegalStateException("You already picked " + game);
    }

    /** Boots or wakes the player's console. IllegalStateException when they have not picked a game. */
    public void open(String userId) {
        consoles.compute(userId, (id, c) -> {
            if (c == null) c = start(id);
            c.host().resume();
            return c;
        });
    }

    /** The player left: pause and park the state on disk. */
    public void idle(String userId) {
        consoles.computeIfPresent(userId, (id, c) -> {
            c.host().pause();
            park(id, c);
            return c;
        });
    }

    /**
     * Presses buttons on the player's own console, after whatever they pressed before. command and repeat as in
     * CommandParser; walk holds a single direction long enough to chain steps.
     */
    public void press(String userId, String command, int repeat, boolean walk) {
        Console c = consoles.get(userId);
        if (c == null) throw new IllegalStateException("Your console is not running");
        List<Button> steps = CommandParser.parse(command, repeat, ModeService.ANARCHY);
        if (walk && (steps.size() != 1 || !DIRECTIONS.contains(steps.getFirst()))) {
            throw new IllegalArgumentException("walk is only for a single direction");
        }
        if (c.pending().get() > MAX_PENDING) return;
        c.pending().incrementAndGet();
        c.presses().execute(() -> {
            try {
                Settings.Input s = settings.input();
                if (walk) c.host().press(steps.getFirst(), InputScheduler.WALK_FRAMES, 0);
                else for (Button b : steps) c.host().press(b, s.holdFrames(), s.releaseFrames());
            } catch (RuntimeException e) {
                log.debug("Press dropped for {}: {}", userId, e.getMessage());
            } finally {
                c.pending().decrementAndGet();
            }
        });
    }

    /** Players whose console is in memory (running or paused). */
    public Set<String> running() {
        return Set.copyOf(consoles.keySet());
    }

    /** Bytes of the player's console memory, or null when their console is not in memory. */
    public byte[] readBytes(String userId, int address, int length) {
        Console c = consoles.get(userId);
        return c == null ? null : c.host().readBytes(address, length);
    }

    /** Every console's frames (every second one, RGB) and audio chunks, tagged with the player's id. */
    public void onFrame(BiConsumer<String, int[]> listener) {
        frameListeners.add(listener);
    }

    public void onAudio(BiConsumer<String, short[]> listener) {
        audioListeners.add(listener);
    }

    public int audioSampleRate() {
        return new EmulatorHost().audioSampleRate();
    }

    @EventListener(ContextClosedEvent.class)
    public void shutdown() {
        consoles.forEach((userId, c) -> {
            c.presses().shutdownNow();
            park(userId, c);
            c.host().shutdown();
        });
        consoles.clear();
    }

    private Console start(String userId) {
        String game = players.game(userId);
        if (game == null) throw new IllegalStateException("Pick a game first");
        EmulatorHost host = new EmulatorHost();
        host.insert(game, cartridges.require(game), files.playerBatterySave(userId, game));
        host.onFrame(pixels -> frameListeners.forEach(l -> l.accept(userId, pixels)));
        host.onAudio(samples -> audioListeners.forEach(l -> l.accept(userId, samples)));
        host.powerOn();
        Path state = files.playerState(userId, game);
        if (Files.isRegularFile(state)) {
            try {
                host.loadState(Files.readAllBytes(state));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not resume {} for {}; booting from the battery save", game, userId, e);
            }
        }
        log.info("Console started: {} plays {}", userId, game);
        return new Console(host, game, Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()), new AtomicInteger());
    }

    private void park(String userId, Console c) {
        try {
            Path state = files.playerState(userId, c.cartridgeId());
            Path tmp = state.resolveSibling(state.getFileName() + ".tmp");
            Files.createDirectories(state.getParent());
            Files.write(tmp, c.host().saveState());
            Files.move(tmp, state, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not park the console of {}", userId, e);
        }
    }
}
