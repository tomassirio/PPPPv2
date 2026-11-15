package dev.pppp.model;

import java.util.List;

/**
 * Which keyboard key presses each pad button, for one player, as KeyboardEvent.key values (single characters
 * lower-cased: "z", "ArrowUp", "Enter", " "). One key per button.
 */
public record KeyBindings(String up, String down, String left, String right, String a, String b, String start, String select) {
    /** Arrow keys, Z = A, X = B, Enter = Start, Shift = Select: what a player has until they change it. */
    public static final KeyBindings DEFAULT = new KeyBindings("ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "z", "x", "Enter", "Shift");

    public List<String> keys() {
        return List.of(up, down, left, right, a, b, start, select);
    }
}
