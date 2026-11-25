package dev.pppp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Which keyboard key presses each pad button, for one player, as KeyboardEvent.key values (single characters
 * lower-cased: "z", "ArrowUp", "Enter", " "). One key per button; fast: the key that toggles Fast forward (null: none).
 */
public record KeyBindings(String up, String down, String left, String right, String a, String b, String start, String select,
                          String fast) {
    /** Arrow keys, Z = A, X = B, Enter = Start, Shift = Select, Space = Fast: what a player has until they change it. */
    public static final KeyBindings DEFAULT = new KeyBindings("ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "z", "x", "Enter", "Shift", " ");

    /** Every bound key (Fast's too when it has one). */
    public List<String> keys() {
        List<String> out = new ArrayList<>(List.of(up, down, left, right, a, b, start, select));
        if (fast != null) out.add(fast);
        return out;
    }
}
