package dev.pppp.service;

import dev.pppp.emulator.Button;
import eu.rekawek.coffeegb.core.AddressSpace;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.repository.CartridgeRepository;
import dev.pppp.repository.PlayerRepository;
import dev.pppp.service.game.Ghosts;
import dev.pppp.service.game.PlayerSprites;
import dev.pppp.service.game.PokecenterDig;
import dev.pppp.service.game.V1Red;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * One private Game Boy per player. A console boots when its player opens it (from the state parked on disk, if
 * any) and only lives in memory while they play: once they leave (idle: paused, its state parked) it is shut down
 * after a grace period ({@link #grace}, so a reload or a quick page change finds it still there), and one nobody
 * touched for {@link #inactivity} is parked and shut down even with its tab open (its screen is told why, see
 * {@link #onAway}). A linked console stays: its link session has its own rules (LinkService). The game is picked once.
 */
@Service
public class PlayerConsoles {
    private static final Logger log = LoggerFactory.getLogger(PlayerConsoles.class);
    /** Commands waiting behind the one being pressed; more are dropped, so a held key cannot build a backlog. */
    private static final int MAX_PENDING = 3;
    /** A tap (a click, or Enter on a focused pad button): held this long, then released this long, in frames. */
    private static final int TAP_HOLD = 6, TAP_RELEASE = 2;

    private record Console(EmulatorHost host, String cartridgeId, ExecutorService presses, AtomicInteger pending) {}

    private final Map<String, Console> consoles = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, int[]>> frameListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, short[]>> audioListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> idleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> awayListeners = new CopyOnWriteArrayList<>();
    /** When each player last did something (a button, or the browser's activity ping): userId -> epoch ms. */
    private final Map<String, Long> lastActive = new ConcurrentHashMap<>();
    /** Consoles parked since: userId -> epoch ms; set and cleared inside the consoles map's compute for that key. */
    private final Map<String, Long> idleSince = new ConcurrentHashMap<>();
    /** How long a parked console waits for its player before it is shut down. */
    private volatile Duration grace = Duration.ofSeconds(60);
    /** How long a running console may go untouched before it is parked and shut down. */
    private volatile Duration inactivity = Duration.ofMinutes(15);
    /** Players whose arrow keys do nothing: linked, standing at the receptionist until they are in the link room. */
    private final Set<String> pinned = ConcurrentHashMap.newKeySet();
    private static final Set<Button> ARROWS = Set.of(Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT);
    /** Link cables, one entry per end: userId -> the player at the other end. Guarded by itself. */
    private final Map<String, String> links = new HashMap<>();
    /** The speed each player picked, put back on a console that boots again (after going away, e.g. a phone's
     * screen locking) so it matches what their speed chip shows. Plugging a cable in clears it (back to 1x).
     * ponytail: memory only, a backend restart boots everyone at 1x; a field in pibs:player:{userId} if that matters. */
    private final Map<String, Double> speeds = new ConcurrentHashMap<>();
    private final PlayerRepository players;
    private final CartridgeService cartridges;
    private final CartridgeRepository files;
    private final Ghosts ghosts;

    public PlayerConsoles(PlayerRepository players, CartridgeService cartridges, CartridgeRepository files, Ghosts ghosts) {
        this.players = players;
        this.cartridges = cartridges;
        this.files = files;
        this.ghosts = ghosts;
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
            idleSince.remove(id);
            lastActive.put(id, System.currentTimeMillis());
            return c;
        });
    }

    /** The player did something on the site (the browser's activity ping): their console is not abandoned. */
    public void touch(String userId) {
        if (consoles.containsKey(userId)) lastActive.put(userId, System.currentTimeMillis());
    }

    /** The player left: tell the listeners, pull their link cable, then pause and park the state on disk. */
    public void idle(String userId) {
        left(userId);
        releaseAll(userId);
        ghosts.leave(userId);
        unlink(userId); // before pausing: a paused driver would freeze the other player's console too
        boolean[] paused = {false};
        consoles.computeIfPresent(userId, (id, c) -> {
            paused[0] = true;
            c.host().pause();
            c.host().flushBattery(); // the game's own save on disk too, not only inside the parked state
            // Only a console whose state is safely on disk may be shut down; one that could not park stays paused
            // in memory (and tries again the next time it goes idle).
            if (park(id, c)) idleSince.putIfAbsent(id, System.currentTimeMillis());
            return c;
        });
        // Now that it is paused: any screen or watcher that attached meanwhile is told (4408 / 4410) instead of
        // staring at a frozen frame.
        if (paused[0]) away(userId);
    }

    /**
     * Shuts down what nobody plays: a parked console past its grace period, and a running one untouched for the
     * inactivity limit (its screens are told first, see onAway, then it is parked). Linked consoles are left to
     * LinkService. Both checks are repeated inside the map's compute, so an open() in between wins.
     */
    @Scheduled(fixedDelay = 5000)
    public void sweep() {
        long now = System.currentTimeMillis();
        for (String id : List.copyOf(consoles.keySet())) {
            if (linkedWith(id) != null) continue;
            Long since = idleSince.get(id);
            if (since != null) {
                // The same idle spell still (a reopen and leave in between starts a new grace period).
                if (now - since >= grace.toMillis()) shutDown(id, c -> since.equals(idleSince.get(id)), "left");
                continue;
            }
            Long last = lastActive.get(id);
            if (last == null || now - last < inactivity.toMillis()) continue;
            // Untouched for too long. Anything they do meanwhile (open, a button, the activity ping, a link)
            // writes lastActive or links them, so each step checks it is still the same stale moment.
            if (!last.equals(lastActive.get(id)) || linkedWith(id) != null) continue;
            idle(id); // its screens are told why once it is paused (onAway)
            shutDown(id, c -> idleSince.containsKey(id) && last.equals(lastActive.get(id)), "inactive");
        }
    }

    /**
     * Called with the player's id once their console is paused and parked (they left, nobody touched it for too
     * long, or an admin closed it): its screens and watchers are told it stopped.
     */
    public void onAway(Consumer<String> listener) {
        awayListeners.add(listener);
    }

    /**
     * Parks the player's console and shuts it down now (an admin's Close; a link is pulled first). False without one,
     * or when its state could not be parked (it stays, paused).
     */
    public boolean close(String userId) {
        if (!consoles.containsKey(userId)) return false;
        idle(userId);
        return shutDown(userId, c -> idleSince.containsKey(userId), "closed by an admin");
    }

    private void away(String userId) {
        for (Consumer<String> l : awayListeners) {
            try {
                l.accept(userId);
            } catch (RuntimeException e) {
                log.warn("Away listener failed for {}", userId, e);
            }
        }
    }

    /** For tests: the grace period after leaving and the inactivity limit. */
    public void limits(Duration grace, Duration inactivity) {
        this.grace = grace;
        this.inactivity = inactivity;
    }

    /**
     * Stops and removes the console if still(c) holds. Its state is parked already. All of it happens inside the
     * map's compute for that player, so an open() waits until the old console has flushed its battery save and
     * stopped, and never boots a second one beside it.
     */
    private boolean shutDown(String userId, Predicate<Console> still, String why) {
        boolean[] gone = {false};
        consoles.computeIfPresent(userId, (id, c) -> {
            if (linkedWith(id) != null || !still.test(c)) return c;
            c.presses().shutdownNow();
            c.host().shutdown(); // flushes the battery save
            idleSince.remove(id);
            lastActive.remove(id);
            gone[0] = true;
            return null;
        });
        if (gone[0]) log.info("Console shut down ({}): {}", why, userId);
        return gone[0];
    }

    /**
     * A console in memory as the admin overview shows it. state: RUNNING, IDLE (parked, waiting to shut down) or
     * PAUSED (its state could not be parked, so it stays).
     */
    public record Session(String userId, String cartridgeId, String state, String linkedWith, Long lastActive,
                          Long closesAt) {}

    /** Every console in memory now. */
    public List<Session> sessions() {
        List<Session> out = new ArrayList<>();
        consoles.forEach((id, c) -> {
            String peer = linkedWith(id);
            Long since = idleSince.get(id), last = lastActive.get(id);
            Long closes = peer != null ? null
                    : since != null ? since + grace.toMillis()
                    : last != null ? last + inactivity.toMillis() : null;
            // PAUSED: paused but not parked (writing its state failed): it stays in memory, not shut down.
            String state = since != null ? "IDLE" : c.host().status().paused() ? "PAUSED" : "RUNNING";
            out.add(new Session(id, c.cartridgeId(), state, peer, last, "PAUSED".equals(state) ? null : closes));
        });
        return out;
    }

    /**
     * Presses the reset button: the listeners hear the player left (their link session ends), the cable comes out,
     * and the console boots the cartridge again from its battery save, as a real one does: whatever was not saved in
     * the game is gone. IllegalStateException when their console is not running.
     */
    public void reset(String userId) {
        Console c = consoles.get(userId);
        if (c == null) throw new IllegalStateException("Your console is not running");
        touch(userId);
        left(userId);
        unlink(userId);
        releaseAll(userId);
        c.host().reset();
        log.info("Console reset: {}", userId);
    }

    private void left(String userId) {
        for (Consumer<String> l : idleListeners) {
            try {
                l.accept(userId);
            } catch (RuntimeException e) {
                log.warn("Idle listener failed for {}", userId, e);
            }
        }
    }

    /** Leaves (listeners, link cable, park) and then shuts the player's console down for good. No-op without one. */
    public void discard(String userId) {
        idle(userId);
        Console c = consoles.remove(userId);
        idleSince.remove(userId);
        lastActive.remove(userId);
        if (c == null) return;
        c.presses().shutdownNow();
        c.host().shutdown();
    }

    /**
     * Taps buttons on the player's own console (each held TAP_HOLD frames), after whatever they tapped before.
     * command and repeat as in CommandParser. Held keys go through {@link #hold} instead.
     */
    public void press(String userId, String command, int repeat) {
        Console c = consoles.get(userId);
        if (c == null) throw new IllegalStateException("Your console is not running");
        touch(userId);
        List<Button> parsed = CommandParser.parse(command, repeat);
        List<Button> steps = pinned.contains(userId) ? parsed.stream().filter(b -> !ARROWS.contains(b)).toList() : parsed;
        if (steps.isEmpty() || c.pending().get() > MAX_PENDING) return;
        c.pending().incrementAndGet();
        c.presses().execute(() -> {
            try {
                for (Button b : steps) c.host().press(b, TAP_HOLD, TAP_RELEASE);
            } catch (RuntimeException e) {
                log.debug("Press dropped for {}: {}", userId, e.getMessage());
            } finally {
                c.pending().decrementAndGet();
            }
        });
    }

    /** Holds or lets go of a button on the player's own console, like a finger on it. No-op without a console. */
    public void hold(String userId, Button button, boolean down) {
        if (down && pinned.contains(userId) && ARROWS.contains(button)) return;
        Console c = consoles.get(userId);
        if (c == null) return;
        if (down) touch(userId);
        c.host().hold(button, down);
    }

    /**
     * Locks or unlocks the player's arrow keys (A, B, Start, Select still work), e.g. while the link receptionist
     * talks to them. Locking lets go of any arrow they hold.
     */
    public void pin(String userId, boolean on) {
        if (!on) {
            pinned.remove(userId);
            return;
        }
        pinned.add(userId);
        Console c = consoles.get(userId);
        if (c != null) for (Button b : ARROWS) c.host().hold(b, false);
    }

    /** Whether the player's arrow keys are locked ({@link #pin}). */
    public boolean pinned(String userId) {
        return pinned.contains(userId);
    }

    /** Lets go of every button the player holds (their input socket closed or their window lost focus). */
    public void releaseAll(String userId) {
        Console c = consoles.get(userId);
        if (c != null) c.host().releaseAll();
    }


    /** Called with the player's id when they leave (before their console pauses) or reset it. */
    public void onIdle(Consumer<String> listener) {
        idleListeners.add(listener);
    }

    /** True while the player's console is in memory and not paused, i.e. they have it open. */
    public boolean isAwake(String userId) {
        Console c = consoles.get(userId);
        return c != null && !c.host().status().paused();
    }

    /** Plugs a link cable between the two players' consoles. IllegalStateException when either is not open or already linked. */
    public void link(String userA, String userB) {
        synchronized (links) {
            if (!isAwake(userA) || !isAwake(userB)) throw new IllegalStateException("Both consoles must be open to link");
            Console a = consoles.get(userA), b = consoles.get(userB);
            var friendA = PlayerSprites.cableFriend(rom(a.cartridgeId()), players.sprite(userB));
            var friendB = PlayerSprites.cableFriend(rom(b.cartridgeId()), players.sprite(userA));
            EmulatorHost.link(a.host(), b.host());
            a.host().patchRom(friendA);
            b.host().patchRom(friendB);
            links.put(userA, userB);
            links.put(userB, userA);
            speeds.remove(userA); // EmulatorHost.link set both to 1x
            speeds.remove(userB);
        }
        log.info("Link cable plugged: {} and {}", userA, userB);
    }

    /** The trainers the player can walk as (their game's walking sprites); empty before they pick a game. */
    public List<PlayerSprites.Choice> spriteChoices(String userId) {
        String game = players.game(userId);
        return game == null ? List.of() : PlayerSprites.choices(rom(game));
    }

    /** The trainer the player walks as (PlayerSprites.DEFAULT when they never picked one). */
    public String sprite(String userId) {
        String key = players.sprite(userId);
        return key == null ? PlayerSprites.DEFAULT : key;
    }

    /** The trainer as a sprite sheet, drawn from the player's ROM; null when their game cannot draw it. */
    public byte[] spriteSheet(String userId, String key) {
        String game = players.game(userId);
        return game == null ? null : PlayerSprites.sheet(rom(game), key);
    }

    /**
     * Walks the player as key's trainer from now on: saved, and a console in memory is rebuilt with the patched ROM
     * from its parked state (nothing is lost; a running one keeps running). The game draws the new trainer on the
     * next map it loads. IllegalArgumentException for a trainer their game has not; IllegalStateException without a
     * game or while a link cable is plugged in (rebuilding would pull it).
     */
    public void pickSprite(String userId, String key) {
        if (players.game(userId) == null) throw new IllegalStateException("Pick a game first");
        if (spriteChoices(userId).stream().noneMatch(c -> c.key().equals(key))) {
            throw new IllegalArgumentException("No trainer " + key + " in your game");
        }
        if (linkedWith(userId) != null) throw new IllegalStateException("Unplug the link cable first");
        players.saveSprite(userId, key);
        Console c = consoles.remove(userId);
        idleSince.remove(userId);
        lastActive.remove(userId); // open() below sets it again for a running one
        if (c == null) return;
        boolean awake = !c.host().status().paused();
        releaseAll(userId);
        c.presses().shutdownNow();
        park(userId, c);
        c.host().shutdown();
        if (awake) open(userId);
    }

    /** A trainer of a cartridge as a sprite sheet (the sign-in page's town map), or null. */
    public byte[] spriteSheetOf(String cartridgeId, String key) {
        return PlayerSprites.sheet(rom(cartridgeId), key);
    }

    /** The cartridge's ROM bytes, read once. */
    private byte[] rom(String cartridgeId) {
        return roms.computeIfAbsent(cartridgeId, id -> {
            try {
                return Files.readAllBytes(cartridges.require(id));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private final Map<String, byte[]> roms = new ConcurrentHashMap<>();

    /** Speeds a player has to pick from. Audio only plays at 1x (EmulatorHost drops it otherwise). */
    public static final java.util.Set<Double> SPEEDS = java.util.Set.of(1.0, 2.0, 4.0);

    /**
     * Sets the player's own emulation speed. IllegalArgumentException for a speed not in {@link #SPEEDS};
     * IllegalStateException when their console is not running or a link cable is plugged in (linked consoles run in
     * step at 1x, see EmulatorHost.link).
     */
    public void setSpeed(String userId, double speed) {
        if (!SPEEDS.contains(speed)) throw new IllegalArgumentException("Speed must be 1, 2 or 4");
        synchronized (links) {
            if (links.containsKey(userId)) throw new IllegalStateException("Speed is locked to 1x while the link cable is plugged in");
            Console c = consoles.get(userId);
            if (c == null) throw new IllegalStateException("Your console is not running");
            c.host().setSpeed(speed);
            speeds.put(userId, speed);
        }
    }

    /** The player's emulation speed, or null when their console is not in memory. */
    public Double speed(String userId) {
        Console c = consoles.get(userId);
        return c == null ? null : c.host().status().speed();
    }

    /** Pulls the player's link cable and parks both consoles. Returns who was at the other end, or null when not linked. */
    public String unlink(String userId) {
        String peer;
        synchronized (links) {
            peer = links.remove(userId);
            if (peer == null) return null;
            links.remove(peer);
            Console c = consoles.get(userId);
            if (c != null) EmulatorHost.unlink(c.host());
            for (String id : List.of(userId, peer)) {
                Console end = consoles.get(id);
                if (end != null) end.host().patchRom(PlayerSprites.cableFriend(rom(end.cartridgeId()), null));
            }
        }
        for (String id : List.of(userId, peer)) {
            Console c = consoles.get(id);
            if (c != null) park(id, c);
        }
        log.info("Link cable pulled: {} and {}", userId, peer);
        return peer;
    }

    /** The player at the other end of the cable, or null. */
    public String linkedWith(String userId) {
        synchronized (links) {
            return links.get(userId);
        }
    }

    /** Players whose console is in memory (running or paused). */
    public Set<String> running() {
        return Set.copyOf(consoles.keySet());
    }

    /** Bytes of the player's console memory, or null when their console is not in memory or WRAM bank 1 is not switched in. */
    public byte[] readBytes(String userId, int address, int length) {
        Console c = consoles.get(userId);
        try {
            return c == null ? null : c.host().readBytes(address, length);
        } catch (IllegalStateException e) {
            return null; // powered off meanwhile (shut down by the sweep)
        }
    }

    /** Runs task on the player's console between frames with its bus, to read and write. Null when their console is not in memory. */
    public <T> T withBus(String userId, Function<AddressSpace, T> task) {
        Console c = consoles.get(userId);
        try {
            return c == null ? null : c.host().withBus(task);
        } catch (IllegalStateException e) {
            return null; // powered off meanwhile (shut down by the sweep)
        }
    }

    /**
     * The cartridge RAM as the game has it now: Coffee GB writes the .sav only when asked (power off, reset, this),
     * so this flushes the battery to {dataDir}/saves/{user}/{cartridge}.sav and reads it back. Null when the
     * player's console is not in memory or the file cannot be read.
     */
    public byte[] batterySave(String userId) {
        Console c = consoles.get(userId);
        if (c == null) return null;
        try {
            c.host().flushBattery();
            return Files.readAllBytes(files.playerBatterySave(userId, c.cartridgeId()));
        } catch (IOException | IllegalStateException e) {
            return null; // unreadable, or powered off meanwhile
        }
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
        synchronized (links) {
            links.clear();
        }
    }

    private Console start(String userId) {
        String game = players.game(userId);
        if (game == null) throw new IllegalStateException("Pick a game first");
        EmulatorHost host = new EmulatorHost();
        V1Red red = new V1Red();
        host.insert(game, cartridges.require(game), files.playerBatterySave(userId, game),
                rom -> PlayerSprites.patch(PokecenterDig.patch(red.patch(rom)), players.sprite(userId)));
        host.afterFrame(red::nicknames);
        host.afterFrame(PokecenterDig.patientReceptionist(host::isLinked));
        host.onFrame(pixels -> {
            ghosts.frame(userId, game, host, pixels); // before the listeners: they send this array to the browser
            frameListeners.forEach(l -> l.accept(userId, pixels));
        });
        host.onAudio(samples -> audioListeners.forEach(l -> l.accept(userId, samples)));
        host.setSpeed(speeds.getOrDefault(userId, 1.0));
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

    /** Writes the console's state to its .state file (atomically). False when it could not. */
    private boolean park(String userId, Console c) {
        try {
            Path state = files.playerState(userId, c.cartridgeId());
            Path tmp = state.resolveSibling(state.getFileName() + ".tmp");
            Files.createDirectories(state.getParent());
            Files.write(tmp, c.host().saveState());
            Files.move(tmp, state, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not park the console of {}", userId, e);
            return false;
        }
    }
}
