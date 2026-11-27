package dev.pppp.service.game;

import dev.pppp.config.PpppProperties;
import dev.pppp.service.PlayerConsoles;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The last seconds before a blackout, as a GIF (played once) beside its bloodstain. Every running console's last
 * {@link #SECONDS} s of game time are kept in memory at 15 fps (every second frame PlayerConsoles hands out), each
 * picture as palette indexes; a picture that does not change (a text waiting for A) counts {@link #MAX_HOLD} at most,
 * so a long wait does not fill the clip. Bloodstains calls {@link #save} as it leaves a stain: the clip is written to
 * data/replays/{stainId}.gif off the poll thread, and deleted with its stain.
 */
@Service
public class Replays {
    private static final Logger log = LoggerFactory.getLogger(Replays.class);

    static final int SECONDS = 5;
    /** Time in 1/30 s (one frame PlayerConsoles hands out); a kept picture stands for two of them. */
    private static final int STEP = 2, CLIP = SECONDS * 30, MAX_HOLD = 30, W = 160, H = 144;

    /** One picture: palette indexes, its colours (RGB), and how long it stays on screen in 1/30 s. */
    record Shot(byte[] index, int[] palette, int ticks) {}

    private static final class Buffer {
        final ArrayDeque<Shot> shots = new ArrayDeque<>();
        int total, frames;
    }

    private final Map<String, Buffer> buffers = new ConcurrentHashMap<>();
    /** Stain id -> the replay's length in ms. */
    private final Map<String, Integer> saved = new ConcurrentHashMap<>();
    private final Path dir;
    private volatile boolean loaded;

    public Replays(PlayerConsoles consoles, PpppProperties props) {
        this.dir = props.dataDir().resolve("replays");
        consoles.onFrame(this::frame);
    }

    /** On the emulator thread: keeps every second picture, or makes the last one last longer when nothing changed. */
    void frame(String userId, int[] pixels) {
        Buffer b = buffers.computeIfAbsent(userId, k -> new Buffer());
        synchronized (b) {
            if (b.frames++ % STEP != 0) return;
            Shot shot = index(pixels);
            if (shot == null) return; // ponytail: >256 colours is skipped, CGB games almost never draw that many
            Shot last = b.shots.peekLast();
            if (last != null && Arrays.equals(last.index, shot.index) && Arrays.equals(last.palette, shot.palette)) {
                int grown = Math.min(last.ticks + STEP, MAX_HOLD);
                b.total += grown - last.ticks;
                b.shots.pollLast();
                b.shots.addLast(new Shot(last.index, last.palette, grown));
            } else {
                b.shots.addLast(shot);
                b.total += shot.ticks;
            }
            while (b.total - b.shots.peekFirst().ticks >= CLIP) b.total -= b.shots.pollFirst().ticks;
        }
    }

    /** Forgets the buffers of consoles no longer running. */
    void retain(Set<String> running) {
        buffers.keySet().retainAll(running);
    }

    /** Writes the player's last seconds as the stain's replay, in the background. */
    void save(String userId, String stainId) {
        Buffer b = buffers.get(userId);
        if (b == null) return;
        List<Shot> shots;
        synchronized (b) {
            shots = List.copyOf(b.shots);
        }
        if (shots.isEmpty()) return;
        Thread.ofVirtual().start(() -> {
            try {
                Files.createDirectories(dir);
                Path tmp = dir.resolve(stainId + ".tmp");
                Files.write(tmp, gif(shots));
                Files.move(tmp, file(stainId), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                saved.put(stainId, millis(shots));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not write the replay of {}", stainId, e);
            }
        });
    }

    /** The replay's length in ms, 0 when the stain has none. */
    public int millis(String stainId) {
        load();
        return saved.getOrDefault(stainId, 0);
    }

    /** The replay's file, or null when the id is not a stain's or it has none. */
    public Path find(String stainId) {
        try {
            UUID.fromString(stainId); // a stain id, nothing else: no paths
        } catch (IllegalArgumentException e) {
            return null;
        }
        Path f = file(stainId);
        return Files.isRegularFile(f) ? f : null;
    }

    void delete(String stainId) {
        saved.remove(stainId);
        try {
            Files.deleteIfExists(file(stainId));
        } catch (IOException e) {
            log.warn("Could not delete the replay of {}", stainId, e);
        }
    }

    private Path file(String stainId) {
        return dir.resolve(stainId + ".gif");
    }

    private synchronized void load() {
        if (loaded) return;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".gif"))
                        .forEach(n -> saved.put(n.substring(0, n.length() - 4), millis(dir.resolve(n))));
            } catch (IOException e) {
                log.warn("Could not list the replays", e);
            }
        }
        loaded = true;
    }

    /** How long the GIF plays: its delays (in 1/100 s) added up, as written by {@link #gif}. */
    static int millis(List<Shot> shots) {
        return Arrays.stream(delays(shots)).sum() * 10;
    }

    /** Each picture's delay in 1/100 s, rounded as a running total so the clip lasts its game time (2/30 s is no 7). */
    private static int[] delays(List<Shot> shots) {
        int[] out = new int[shots.size()];
        int ticks = 0;
        for (int i = 0; i < out.length; i++) {
            int was = Math.round(ticks * 100 / 30f);
            ticks += shots.get(i).ticks;
            out[i] = Math.round(ticks * 100 / 30f) - was;
        }
        return out;
    }

    /** A written replay's length, read back from its frames' delays (replays from before a restart). */
    private static int millis(Path gif) {
        var reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (var in = ImageIO.createImageInputStream(gif.toFile())) {
            reader.setInput(in);
            int ms = 0;
            for (int i = 0, n = reader.getNumImages(true); i < n; i++) {
                var meta = reader.getImageMetadata(i);
                var root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
                var control = root.getElementsByTagName("GraphicControlExtension");
                if (control.getLength() > 0) ms += 10 * Integer.parseInt(((IIOMetadataNode) control.item(0)).getAttribute("delayTime"));
            }
            return ms;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the replay {}", gif, e);
            return SECONDS * 1000;
        } finally {
            reader.dispose();
        }
    }

    /** The picture as palette indexes, or null past 256 colours. */
    static Shot index(int[] pixels) {
        int[] palette = new int[256];
        int colours = 0, lastColour = -1, lastIndex = 0;
        byte[] index = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i] & 0xFFFFFF;
            if (c != lastColour) {
                int k = 0;
                while (k < colours && palette[k] != c) k++;
                if (k == colours) {
                    if (colours == 256) return null;
                    palette[colours++] = c;
                }
                lastColour = c;
                lastIndex = k;
            }
            index[i] = (byte) lastIndex;
        }
        return new Shot(index, Arrays.copyOf(palette, colours), STEP);
    }

    /** A GIF that plays once (no NETSCAPE loop block), each picture with its own colour table and delay. */
    static byte[] gif(List<Shot> shots) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        var bytes = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            int[] delays = delays(shots);
            for (int f = 0; f < shots.size(); f++) {
                Shot s = shots.get(f);
                int size = Math.max(2, Integer.highestOneBit(Math.max(1, s.palette.length - 1)) << 1); // a power of 2
                int[] p = Arrays.copyOf(s.palette, size);
                byte[] r = new byte[size], g = new byte[size], bl = new byte[size];
                for (int i = 0; i < size; i++) {
                    r[i] = (byte) (p[i] >> 16);
                    g[i] = (byte) (p[i] >> 8);
                    bl[i] = (byte) p[i];
                }
                int bits = Integer.numberOfTrailingZeros(size);
                var model = new IndexColorModel(bits, size, r, g, bl);
                var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_INDEXED, model);
                image.getRaster().setDataElements(0, 0, W, H, s.index);
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
                String format = meta.getNativeMetadataFormatName();
                var root = (IIOMetadataNode) meta.getAsTree(format);
                var control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(delays[f]));
                control.setAttribute("transparentColorIndex", "0");
                root.appendChild(control);
                // Its own colours: left to itself the writer uses its default 216-colour table for every frame.
                var table = new IIOMetadataNode("LocalColorTable");
                table.setAttribute("sizeOfLocalColorTable", String.valueOf(size));
                table.setAttribute("sortFlag", "FALSE");
                for (int i = 0; i < size; i++) {
                    var entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", String.valueOf(i));
                    entry.setAttribute("red", String.valueOf(r[i] & 0xFF));
                    entry.setAttribute("green", String.valueOf(g[i] & 0xFF));
                    entry.setAttribute("blue", String.valueOf(bl[i] & 0xFF));
                    table.appendChild(entry);
                }
                for (int i = root.getLength() - 1; i >= 0; i--) {
                    if (root.item(i).getNodeName().equals("LocalColorTable")) root.removeChild(root.item(i));
                }
                root.appendChild(table);
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, meta), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
