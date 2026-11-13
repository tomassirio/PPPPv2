package dev.pppp.repository;

import dev.pppp.config.PpppProperties;
import dev.pppp.model.Cartridge;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

/** ROMs at {dataDir}/roms/{id}.gb|.gbc, battery saves at {dataDir}/saves/{id}.sav. Callers pass valid ids. */
@Repository
public class CartridgeRepository {
    private final Path roms, saves;

    public CartridgeRepository(PpppProperties props) {
        this.roms = props.dataDir().resolve("roms");
        this.saves = props.dataDir().resolve("saves");
    }

    public List<Cartridge> findAll() {
        if (!Files.isDirectory(roms)) return List.of();
        try (Stream<Path> files = Files.list(roms)) {
            return files.map(p -> {
                        String id = p.getFileName().toString().replaceFirst("\\.gbc?$", "");
                        return Cartridge.ID.matcher(id).matches() ? describe(id, p) : null;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Cartridge::id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The ROM file, or null. */
    public Path romPath(String id) {
        for (String ext : List.of(".gb", ".gbc")) {
            Path p = roms.resolve(id + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    /** ext is ".gb" or ".gbc". */
    public Cartridge save(String id, String ext, byte[] rom) {
        try {
            Files.createDirectories(roms);
            Path target = roms.resolve(id + ext);
            Files.write(target, rom);
            return describe(id, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(Path rom) {
        try {
            Files.delete(rom);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path batterySave(String id) {
        return saves.resolve(id + ".sav");
    }

    /** A player's own battery save: {dataDir}/saves/{user}/{id}.sav. */
    public Path playerBatterySave(String userId, String id) {
        return saves.resolve(dir(userId)).resolve(id + ".sav");
    }

    /** Where a player's console is parked between sessions: {dataDir}/saves/{user}/{id}.state. */
    public Path playerState(String userId, String id) {
        return saves.resolve(dir(userId)).resolve(id + ".state");
    }

    /** User ids are e-mails; anything that is not a plain file-name character becomes "_". */
    private static String dir(String userId) {
        if (!userId.contains("@")) throw new IllegalArgumentException("Not a user id: " + userId);
        return userId.replaceAll("[^a-z0-9._@+-]", "_");
    }

    public void deleteBatterySave(String id) {
        try {
            Files.deleteIfExists(batterySave(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Cartridge describe(String id, Path rom) {
        try {
            byte[] header = new byte[Cartridge.HEADER_SIZE];
            try (var in = Files.newInputStream(rom)) {
                in.readNBytes(header, 0, header.length);
            }
            return Cartridge.fromHeader(id, header, Files.size(rom));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
