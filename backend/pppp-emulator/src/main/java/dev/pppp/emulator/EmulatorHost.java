package dev.pppp.emulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Game Boy Color (SameBoy's core, through {@link SameBoy}). One dedicated thread owns the machine and runs it
 * frame by frame; every other call is handed to that thread and runs between frames.
 * Invalid console transitions throw {@link IllegalStateException} (HTTP 409); bad arguments throw
 * {@link IllegalArgumentException} (HTTP 400). Plain Java: the app module owns its lifecycle.
 */
public class EmulatorHost {

    public enum Power { ON, OFF }

    /** cartridgeId is null when no cartridge is inserted. */
    public record Status(Power power, String cartridgeId, boolean paused, double speed) {}

    private static final Logger log = LoggerFactory.getLogger(EmulatorHost.class);
    /** One LCD frame: 154 lines x 456 dots. 4194304 / 70224 = 59.73 fps. */
    private static final int TICKS_PER_FRAME = 70224;
    private static final long FRAME_NANOS = 1_000_000_000L * TICKS_PER_FRAME / 4_194_304;
    private static final int PIXELS = SameBoy.WIDTH * SameBoy.HEIGHT;
    /** Mono, so 18 listeners cost ~44 KB/s each; GB audio is near-mono and tops out well below 11 kHz. */
    private static final int AUDIO_SAMPLE_RATE = 22050;

    // Lifecycle fields are guarded by `this`; the machine fields belong to the emulator thread.
    private String cartridgeId;
    private byte[] rom;
    private Path batterySave;
    private Thread thread;
    private volatile boolean paused;
    private volatile double speed = 1.0;
    // Link cable. `peer` is the other end (guarded by the class lock). While linked, the driver's thread
    // runs both machines and drains both task queues; the follower's own thread idles.
    private volatile EmulatorHost peer;
    private volatile EmulatorHost driver;
    private EmulatorHost follower; // emulator thread of the driver

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final List<Consumer<int[]>> frameListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<short[]>> audioListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Bus>> frameHooks = new CopyOnWriteArrayList<>();

    /** SameBoy's console handle; 0 while off. */
    private long h;
    private boolean running;
    private long frame;
    /** The real emulation rate: frames counted over the last second, as a multiple of the Game Boy's 59.7 fps. */
    private volatile double measured;
    private long measureStart = System.nanoTime();
    private long measureFrames;
    /**
     * Where the emulator thread's last second went, in ms: emulating (wall and the thread's own CPU time: far more
     * wall than CPU means the thread was waiting for a core, e.g. a container's CPU limit), frame hooks, frame
     * listeners, queued tasks, and idle (parked until the next frame is due).
     */
    public record Timing(double emulateWallMs, double emulateCpuMs, double hooksMs, double listenersMs, double tasksMs,
                         double idleMs) {}
    private volatile Timing timing;
    private long tEmulate, tEmulateCpu, tHooks, tListeners, tTasks, tIdle;
    private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();
    private final int[] pixels = new int[PIXELS];
    /** What SameBoy was last told: draw this frame, make sound (a fresh machine does both). */
    private boolean drawing = true, hearing = true;
    private Press press;
    /**
     * Buttons held down from outside (a key or a finger on the pad) -> the frame they first counted on, or -1 until
     * the next frame starts. A release waits until the button has counted for MIN_HOLD_FRAMES, so a tap shorter
     * than a frame is still seen by the game.
     */
    private final Map<Button, Long> held = new ConcurrentHashMap<>();
    private final Set<Button> releasing = ConcurrentHashMap.newKeySet();
    private static final int MIN_HOLD_FRAMES = 3;
    private final short[] audio = new short[8192];

    /** Inserts a ROM. Console must be OFF. batterySave is where the cartridge's .sav lives (may not exist yet). */
    public synchronized void insert(String cartridgeId, Path rom, Path batterySave) {
        insert(cartridgeId, rom, batterySave, UnaryOperator.identity());
    }

    /** Like {@link #insert(String, Path, Path)}, with the ROM's bytes changed in memory by patch (the file is untouched). */
    public synchronized void insert(String cartridgeId, Path rom, Path batterySave, UnaryOperator<byte[]> patch) {
        requireOff("insert a cartridge");
        try {
            this.rom = patch.apply(Files.readAllBytes(rom));
        } catch (IOException e) {
            throw new IllegalArgumentException("Unreadable ROM: " + e.getMessage(), e);
        }
        try {
            Files.createDirectories(batterySave.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.batterySave = batterySave;
        this.cartridgeId = cartridgeId;
    }

    /** Removes the cartridge. Console must be OFF. */
    public synchronized void eject() {
        requireOff("eject the cartridge");
        cartridgeId = null;
        rom = null;
        batterySave = null;
    }

    /** Changes bytes of this running cartridge between frames, without touching its file or save. */
    public void patchRom(Map<Integer, Integer> bytes) {
        if (bytes.isEmpty()) return;
        call(() -> {
            bytes.forEach((offset, value) -> {
                rom[offset] = (byte) (int) value; // a reset boots the patched cartridge too
                SameBoy.patchRom(h, offset, value);
            });
            return null;
        });
    }

    /** Needs a cartridge. Loads the battery save, if any. */
    public synchronized void powerOn() {
        requireOff("power on");
        if (rom == null) throw new IllegalStateException("No cartridge inserted");
        paused = false;
        frame = 0;
        startMachine();
        running = true;
        thread = Thread.ofPlatform().name("emulator").daemon().start(this::loop);
    }

    /**
     * Plugs a link cable between two powered-on consoles. Until {@link #unlink}, both run in lockstep at
     * 1x on a's thread (a's pause state applies to both). Each console keeps its own buttons, frames and audio.
     */
    public static synchronized void link(EmulatorHost a, EmulatorHost b) {
        if (a == b) throw new IllegalArgumentException("A console cannot link to itself");
        if (a.peer != null || b.peer != null) throw new IllegalStateException("Console is already linked");
        // b first: once its thread has handed over, a's thread is the only one touching b's machine.
        b.call(() -> {
            b.speed = 1.0;
            b.driver = a;
            return null;
        });
        try {
            a.call(() -> {
                a.speed = 1.0;
                SameBoy.link(a.h, b.h);
                a.follower = b;
                return null;
            });
        } catch (RuntimeException e) { // a is off: give b back to its own thread
            b.driver = null;
            throw e;
        }
        a.peer = b;
        b.peer = a;
    }

    /** Pulls the cable, from either end. No-op when the console is not linked. */
    public static synchronized void unlink(EmulatorHost host) {
        EmulatorHost other = host.peer;
        if (other == null) return;
        EmulatorHost d = host.driver == null ? host : other;
        EmulatorHost f = d == host ? other : host;
        d.call(() -> {
            d.follower = null;
            SameBoy.unlink(d.h);
            f.driver = null; // f's own thread takes its machine back
            return null;
        });
        d.peer = null;
        f.peer = null;
    }

    public boolean isLinked() {
        return peer != null;
    }

    /** Pulls the link cable if plugged, flushes the battery save, then stops the emulator thread. */
    public void powerOff() {
        unlink(this); // before taking the monitor: unlinking runs on the driver's thread
        powerOffUnlinked();
    }

    private synchronized void powerOffUnlinked() {
        requireOn();
        try {
            call(() -> {
                stopMachine();
                running = false;
                return null;
            });
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            thread = null;
            Runnable left;
            while ((left = tasks.poll()) != null) {
                if (left instanceof FutureTask<?> f) f.cancel(false);
            }
        }
    }

    /** Hard reset: flushes the battery and boots the cartridge again. */
    public void reset() {
        call(() -> {
            stopMachine();
            startMachine();
            return null;
        });
    }

    public synchronized void pause() {
        requireOn();
        paused = true;
    }

    public synchronized void resume() {
        requireOn();
        paused = false;
    }

    /** 0.25 to 4.0. */
    public void setSpeed(double multiplier) {
        if (!(multiplier >= 0.25 && multiplier <= 4.0)) {
            throw new IllegalArgumentException("Speed must be between 0.25 and 4.0");
        }
        speed = multiplier;
    }

    /** Where the emulator thread's last second went (null before the first second). */
    public Timing timing() {
        return timing;
    }

    /** How fast the console really runs now (1.0 = a real Game Boy); 0 while paused or off. */
    public double measuredSpeed() {
        return paused || thread == null ? 0 : measured;
    }

    public synchronized Status status() {
        return new Status(thread == null ? Power.OFF : Power.ON, cartridgeId, paused, speed);
    }

    /**
     * Holds a button down until {@link #hold}(button, false) or {@link #releaseAll}, like a finger on it. Any thread;
     * takes effect from the next frame. WAIT is ignored.
     */
    public void hold(Button button, boolean down) {
        if (button == Button.WAIT) return;
        var b = button;
        if (down) {
            releasing.remove(b);
            held.putIfAbsent(b, -1L);
        } else if (held.containsKey(b)) {
            releasing.add(b);
        }
    }

    /** Lets go of every held button (the player left, or their keys lost focus). */
    public void releaseAll() {
        releasing.addAll(held.keySet());
    }

    /** Holds the button for holdFrames, releases for releaseFrames, blocking the caller until done. Console must be ON. */
    public void press(Button button, int holdFrames, int releaseFrames) {
        if (holdFrames < 0 || releaseFrames < 0) throw new IllegalArgumentException("Negative frame count");
        Press p = new Press(button == Button.WAIT ? null : button, holdFrames, releaseFrames);
        call(() -> {
            if (press != null) throw new IllegalStateException("Another press is in progress");
            press = p;
            return null;
        });
        await(p.done);
    }

    /** Snapshot (SameBoy's format, BESS inside) taken on the emulator thread between frames. Console must be ON. */
    public byte[] saveState() {
        return call(() -> SameBoy.saveState(h));
    }

    /**
     * Console must be ON with the same cartridge the state was taken from. SameBoy's own states and BESS (as Coffee GB,
     * the earlier core, parked them) both load.
     */
    public void loadState(byte[] state) {
        call(() -> {
            int error = SameBoy.loadState(h, state);
            if (error != 0) throw new IllegalStateException("Save state does not fit this console (SameBoy error " + error + ")");
            return null;
        });
    }

    /** Writes the cartridge RAM (and its clock) to its .sav file. No-op when off or the cartridge has no battery. */
    public void flushBattery() {
        try {
            call(() -> {
                writeBattery();
                return null;
            });
        } catch (IllegalStateException off) {
            // no-op when off
        }
    }

    /** On the emulator thread: the save RAM to the .sav file, atomically. */
    private void writeBattery() {
        byte[] ram = SameBoy.battery(h);
        if (ram == null) return;
        try {
            Path tmp = batterySave.resolveSibling(batterySave.getFileName() + ".tmp");
            Files.write(tmp, ram);
            Files.move(tmp, batterySave, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Battery save failed for {}: {}", batterySave, e.getMessage());
        }
    }

    /**
     * Reads one byte of the bus (ROM, VRAM, cartridge RAM, WRAM, OAM, HRAM) between frames.
     * I/O registers are refused because some reads have side effects. Console must be ON.
     */
    public int readByte(int address) {
        if (address < 0 || address > 0xFFFE || (address >= 0xFF00 && address < 0xFF80)) {
            throw new IllegalArgumentException("Address not readable: " + Integer.toHexString(address));
        }
        byte[] b = readBytes(address, 1);
        if (b == null) throw new IllegalStateException("Another WRAM bank is switched in");
        return b[0] & 0xFF;
    }

    /**
     * Like {@link #readByte} for {@code length} consecutive bytes, all from the same frame. A range touching
     * D000-DFFF reads WRAM bank 1 or nothing: null while a Game Boy Color game has another bank switched in.
     */
    public byte[] readBytes(int address, int length) {
        int end = address + length - 1;
        if (length < 1 || address < 0 || end > 0xFFFE || (address < 0xFF80 && end >= 0xFF00)) {
            throw new IllegalArgumentException("Range not readable: " + Integer.toHexString(address) + "+" + length);
        }
        return call(() -> {
            // SVBK (FF70) reads back the switched-in bank (0 means 1) without side effects; FF in DMG mode,
            // which has no banks.
            int svbk = SameBoy.read(h, 0xFF70);
            if (address <= 0xDFFF && end >= 0xD000 && svbk != 0xFF && (svbk & 7) > 1) return null;
            byte[] out = new byte[length];
            for (int i = 0; i < length; i++) out[i] = (byte) SameBoy.read(h, address + i);
            return out;
        });
    }

    /**
     * Registers a listener for every second emulated frame: 160x144 pixels as 0xRRGGBB, a fresh array
     * each call. Runs on the emulator thread, so it must hand off and return immediately.
     */
    public void onFrame(Consumer<int[]> listener) {
        frameListeners.add(listener);
    }

    /**
     * Runs hook on the emulator thread after every frame with the bus, to read and write memory. A Game Boy Color
     * game may have any WRAM bank in at D000-DFFF (SVBK, FF70): check it before touching that range.
     */
    public void afterFrame(Consumer<Bus> hook) {
        frameHooks.add(hook);
    }

    /**
     * Runs task once on the emulator thread between frames with the bus, to read and write memory in one go (the same
     * WRAM bank caveat as {@link #afterFrame}). Console must be ON.
     */
    public <T> T withBus(Function<Bus, T> task) {
        return call(() -> task.apply(bus()));
    }

    /**
     * Registers a listener for the console's sound: mono signed 16-bit samples at {@link #audioSampleRate()},
     * about one frame's worth (~369) per call, in a fresh array shared by all listeners (don't modify it).
     * Silent while paused or at any speed but 1.0. Runs on the emulator thread: hand off and return.
     */
    public void onAudio(Consumer<short[]> listener) {
        audioListeners.add(listener);
    }

    public int audioSampleRate() {
        return AUDIO_SAMPLE_RATE;
    }

    /** Stops the emulator if it is running. Call on application shutdown. */
    public void shutdown() {
        unlink(this);
        synchronized (this) {
            if (thread != null) powerOffUnlinked();
        }
    }

    // ---- emulator thread ----

    private void loop() {
        long deadline = System.nanoTime();
        while (running) {
            try {
                if (driver != null) { // linked follower: the driver's thread owns this machine and its tasks
                    LockSupport.parkNanos(5_000_000L);
                    deadline = System.nanoTime();
                    continue;
                }
                Runnable task;
                long t0 = System.nanoTime();
                while (running && driver == null && (task = tasks.poll()) != null) task.run();
                tTasks += System.nanoTime() - t0;
                EmulatorHost f = follower;
                while (running && f != null && f == follower && (task = f.tasks.poll()) != null) task.run();
                if (!running) break;
                if (driver != null) continue;
                if (paused) {
                    task = tasks.poll(20, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                    deadline = System.nanoTime();
                    continue;
                }
                step();
                deadline += (long) (FRAME_NANOS / speed);
                long wait = deadline - System.nanoTime();
                if (wait > 0) {
                    long p0 = System.nanoTime();
                    LockSupport.parkNanos(wait);
                    tIdle += System.nanoTime() - p0;
                }
                else if (wait < -100_000_000L) deadline = System.nanoTime(); // too far behind: don't catch up in a burst
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Emulator frame failed", e);
            }
        }
    }

    private void step() {
        EmulatorHost f = follower;
        long w0 = System.nanoTime(), c0 = CPU.getCurrentThreadCpuTime();
        if (f == null) {
            beginFrame();
            SameBoy.runFrame(h);
        } else { // linked: both machines in lockstep, the cable between them (sameboy_jni.c)
            beginFrame();
            f.beginFrame();
            SameBoy.runLinkedFrame(h, f.h);
        }
        tEmulateCpu += CPU.getCurrentThreadCpuTime() - c0;
        tEmulate += System.nanoTime() - w0;
        endFrame();
        if (f != null) f.endFrame();
    }

    private void beginFrame() {
        held.replaceAll((b, since) -> since < 0 ? frame : since);
        for (var b : releasing) {
            Long since = held.get(b);
            if (since == null || frame - since >= MIN_HOLD_FRAMES) {
                held.remove(b);
                releasing.remove(b);
            }
        }
        Press p = press;
        int down = 0;
        for (Button b : held.keySet()) down |= key(b);
        if (p != null && p.button != null && p.frames < p.hold) down |= key(p.button);
        if (down != pressed) {
            SameBoy.keys(h, down);
            pressed = down;
        }
        // Only the frames listeners get are drawn, and sound made only when someone hears it (1x): ~5% less work.
        boolean draw = (frame + 1) % 2 == 0 && !frameListeners.isEmpty(), hear = speed == 1.0 && !audioListeners.isEmpty();
        if (draw != drawing) SameBoy.render(h, drawing = draw);
        if (hear != hearing) SameBoy.sound(h, hearing = hear);
    }

    private int pressed;

    /** SameBoy's key bit: right, left, up, down, A, B, select, start. */
    private static int key(Button b) {
        return switch (b) {
            case RIGHT -> 1;
            case LEFT -> 1 << 1;
            case UP -> 1 << 2;
            case DOWN -> 1 << 3;
            case A -> 1 << 4;
            case B -> 1 << 5;
            case SELECT -> 1 << 6;
            case START -> 1 << 7;
            case WAIT -> 0;
        };
    }

    private void endFrame() {
        Press p = press;
        frame++;
        measureFrames++;
        long now = System.nanoTime();
        if (now - measureStart >= 1_000_000_000L) {
            measured = measureFrames * (double) FRAME_NANOS / (now - measureStart);
            timing = new Timing(tEmulate / 1e6, tEmulateCpu / 1e6, tHooks / 1e6, tListeners / 1e6, tTasks / 1e6, tIdle / 1e6);
            tEmulate = tEmulateCpu = tHooks = tListeners = tTasks = tIdle = 0;
            measureStart = now;
            measureFrames = 0;
        }
        long h0 = System.nanoTime();
        Bus b = bus();
        for (Consumer<Bus> h : frameHooks) {
            try {
                h.accept(b);
            } catch (RuntimeException e) {
                log.warn("Frame hook failed", e);
            }
        }
        long l0 = System.nanoTime();
        tHooks += l0 - h0;
        if (frame % 2 == 0 && !frameListeners.isEmpty()) {
            SameBoy.pixels(h, pixels);
            for (Consumer<int[]> l : frameListeners) {
                try {
                    l.accept(pixels.clone());
                } catch (RuntimeException e) {
                    log.warn("Frame listener failed", e);
                }
            }
        }
        sound();
        tListeners += System.nanoTime() - l0;
        if (p != null) {
            p.frames++;
            if (p.frames >= p.hold + p.release) {
                press = null;
                p.done.complete(null);
            }
        }
    }

    private void startMachine() {
        pressed = 0; // a new machine starts with nothing pressed; the next frame applies what is held
        drawing = hearing = true;
        byte[] battery = null;
        try {
            if (Files.isRegularFile(batterySave)) battery = Files.readAllBytes(batterySave);
        } catch (IOException e) {
            log.error("Battery load failed for {}: {}", batterySave, e.getMessage());
        }
        // Always a Game Boy Color: original Game Boy carts get the CGB boot ROM's colour palette (Pokémon Red: reds).
        h = SameBoy.create(rom, battery, AUDIO_SAMPLE_RATE);
    }

    /** The frame's sound to the listeners: mono at AUDIO_SAMPLE_RATE, ~369 samples. */
    private void sound() {
        int n = SameBoy.drainAudio(h, audio);
        // ponytail: other speeds are dropped, not pitch-corrected; resample by speed here if friends miss the music at 2x.
        if (n == 0 || audioListeners.isEmpty() || speed != 1.0) return;
        short[] mono = java.util.Arrays.copyOf(audio, n);
        for (Consumer<short[]> l : audioListeners) {
            try {
                l.accept(mono);
            } catch (RuntimeException ex) {
                log.warn("Audio listener failed", ex);
            }
        }
    }

    /** Flushes the battery and releases the machine; fails a running press so its caller wakes up. */
    private void stopMachine() {
        if (press != null) {
            press.done.completeExceptionally(new IllegalStateException("Console was reset or powered off"));
            press = null;
        }
        writeBattery();
        SameBoy.destroy(h);
        h = 0;
    }

    /** The machine's memory as the CPU sees it. */
    private Bus bus() {
        long machine = h;
        return new Bus() {
            public int getByte(int address) {
                return SameBoy.read(machine, address);
            }

            public void setByte(int address, int value) {
                SameBoy.write(machine, address, value);
            }
        };
    }

    // ---- plumbing ----

    private static final class Press {
        final Button button;
        final int hold, release;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        int frames;

        Press(Button button, int hold, int release) {
            this.button = button;
            this.hold = hold;
            this.release = release;
            if (hold + release == 0) done.complete(null);
        }
    }

    /** Runs the task on the emulator thread between frames and waits for it. Console must be ON. */
    private <T> T call(Callable<T> task) {
        FutureTask<T> f = new FutureTask<>(task);
        // On the machine's own thread (a frame listener reading memory) run it here and take no lock: a power off
        // holds the lock while it waits for this very thread, so locking would deadlock. The thread is on by
        // definition; it reads its own `thread` field, set before it started.
        EmulatorHost d = driver;
        if (Thread.currentThread() == thread || (d != null && Thread.currentThread() == d.thread)) {
            f.run();
            return await(f);
        }
        synchronized (this) {
            requireOn();
            tasks.add(f);
        }
        return await(f);
    }

    private static <T> T await(java.util.concurrent.Future<T> f) {
        try {
            return f.get();
        } catch (CancellationException e) {
            throw new IllegalStateException("Console was powered off");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        }
    }

    private void requireOn() {
        if (thread == null) throw new IllegalStateException("Console is off");
    }

    private void requireOff(String action) {
        if (thread != null) throw new IllegalStateException("Power off the console to " + action);
    }
}
