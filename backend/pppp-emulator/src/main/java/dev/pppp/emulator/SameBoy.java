package dev.pppp.emulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * SameBoy's core (C; https://github.com/LIJI32/SameBoy, MIT) through our JNI glue (native/sameboy_jni.c). The
 * library and the CGB boot ROM are built by native/build.sh into resources/native/ and loaded from the jar. A
 * handle is one Game Boy Color; every call on it belongs to its emulator thread (EmulatorHost).
 */
final class SameBoy {
    private SameBoy() {}

    static final int WIDTH = 160, HEIGHT = 144;
    private static final byte[] BOOT_ROM;

    static {
        String os = System.getProperty("os.name").toLowerCase().contains("mac") ? "darwin" : "linux";
        String arch = switch (System.getProperty("os.arch")) {
            case "aarch64", "arm64" -> "arm64";
            case "x86_64", "amd64" -> "x86_64";
            default -> System.getProperty("os.arch");
        };
        String lib = "native/" + os + "-" + arch + "/libpppp_sameboy." + (os.equals("darwin") ? "dylib" : "so");
        try (InputStream in = SameBoy.class.getClassLoader().getResourceAsStream(lib);
             InputStream boot = SameBoy.class.getClassLoader().getResourceAsStream("native/cgb_boot.bin")) {
            if (in == null || boot == null) {
                throw new IllegalStateException("No SameBoy build for " + os + "-" + arch + ": run pppp-emulator/native/build.sh");
            }
            Path file = Files.createTempFile("pppp_sameboy", lib.substring(lib.lastIndexOf('.')));
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            BOOT_ROM = boot.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A Game Boy Color with this cartridge (battery: its save RAM, may be null), booted from the CGB boot ROM. */
    static long create(byte[] rom, byte[] battery, int sampleRate) {
        long h = create(BOOT_ROM, rom, battery, sampleRate);
        if (h == 0) throw new IllegalStateException("SameBoy could not allocate a console");
        return h;
    }

    private static native long create(byte[] bootRom, byte[] rom, byte[] battery, int sampleRate);

    static native void destroy(long h);

    /** Runs one frame (to the next vblank). */
    static native void runFrame(long h);

    /** Runs two linked consoles in lockstep until both have shown a frame. */
    static native void runLinkedFrame(long a, long b);

    static native void link(long a, long b);

    static native void unlink(long h);

    /** Whether the frames from now on are drawn (off: emulated the same, no pixels). */
    static native void render(long h, boolean on);

    /** Whether samples are made from now on (off: the APU runs, makes none). */
    static native void sound(long h, boolean on);

    /** Link cable bits this console clocked so far (a diagnostic). */
    static native long serialBits(long h);

    /** The last frame, 160x144 0xRRGGBB. */
    static native void pixels(long h, int[] out);

    /** The mono samples since the last drain, into out; returns how many. */
    static native int drainAudio(long h, short[] out);

    static native int read(long h, int address);

    static native void write(long h, int address, int value);

    /** Bit i set: SameBoy key i held (right, left, up, down, A, B, select, start). */
    static native void keys(long h, int keys);

    static native void patchRom(long h, int offset, int value);

    static native void reset(long h);

    /** The cartridge's save RAM (and clock), or null when it has none. */
    static native byte[] battery(long h);

    static native byte[] saveState(long h);

    /** 0 when loaded: SameBoy's own states or BESS (as Coffee GB wrote them). */
    static native int loadState(long h, byte[] state);
}
