package dev.pppp.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class Settings {
    private Settings() {}

    /** How long a pressed button is held and then released, in frames. */
    public record Input(@Min(1) @Max(60) int holdFrames, @Min(1) @Max(60) int releaseFrames) {
        public static final Input DEFAULT = new Input(6, 6);
    }
}
