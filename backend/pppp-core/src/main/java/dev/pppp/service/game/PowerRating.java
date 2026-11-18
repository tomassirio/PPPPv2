package dev.pppp.service.game;

/**
 * Power Rating per species, Gen 2 dex number 1..251: the tier table of the Achievements & Trainer Score design
 * ("Pokémon Power Rating (all 251)"), copied as given. All 251 add up to 6220; without Mew and Celebi, 6020.
 * The design's "suggested hand-tweaks" (Shuckle to C, Dratini and Larvitar to D, Smeargle to E) are not applied.
 * Checked against the base stats in the Crystal ROM (pret BaseData): every tier B-F species fits its BST band
 * except Alakazam (BST 490, listed in B).
 */
public final class PowerRating {
    private PowerRating() {}

    static final int TOTAL_WITHOUT_MYTHICALS = 6020;
    private static final int[] PR = new int[252];

    private static void tier(int pr, int... dex) {
        for (int d : dex) PR[d] = pr;
    }

    static {
        tier(100, 150, 249, 250); // S: box legends
        tier(100, 151, 251); // M: mythical
        tier(70, 144, 145, 146, 149, 243, 244, 245, 248); // A: birds, beasts, 600-BST dragons
        tier(45, 3, 6, 9, 38, 55, 59, 62, 65, 68, 73, 78, 89, 91, 94, 103, 121, 123, 127, 130, 131, 134, 135, 136, 142,
                143, 154, 157, 160, 169, 181, 186, 196, 197, 208, 212, 213, 214, 217, 229, 230, 232, 233, 242); // B: 500+
        tier(30, 18, 26, 28, 31, 34, 36, 42, 45, 49, 57, 71, 76, 80, 82, 85, 87, 97, 99, 101, 106, 107, 110, 112, 113,
                115, 119, 122, 124, 125, 126, 128, 139, 141, 171, 178, 182, 189, 199, 203, 205, 210, 221, 224, 226, 227,
                234, 237, 241); // C: 450-499
        tier(20, 2, 5, 8, 12, 15, 20, 22, 24, 40, 44, 47, 51, 53, 61, 64, 67, 70, 75, 77, 93, 95, 105, 108, 114, 117,
                137, 148, 153, 156, 159, 162, 164, 166, 168, 176, 184, 185, 192, 193, 195, 198, 200, 202, 206, 207, 211,
                215, 219, 222, 247); // D: 380-449
        tier(10, 1, 4, 7, 17, 25, 27, 30, 33, 35, 43, 48, 54, 56, 58, 60, 63, 66, 69, 72, 74, 79, 81, 83, 84, 86, 88,
                90, 92, 96, 98, 100, 102, 104, 109, 111, 118, 120, 133, 138, 140, 147, 152, 155, 158, 170, 177, 180, 188,
                190, 201, 209, 216, 223, 225, 228, 231, 238, 239, 240, 246); // E: 300-379
        tier(5, 10, 11, 13, 14, 16, 19, 21, 23, 29, 32, 37, 39, 41, 46, 50, 52, 116, 129, 132, 161, 163, 165, 167, 172,
                173, 174, 175, 179, 183, 187, 191, 194, 204, 218, 220, 235, 236); // F: under 300
    }

    /**
     * The species' tier letter from its PR: S (box legends), M (Mew, Celebi), A 70, B 45, C 30, D 20, E 10, F 5;
     * null outside 1..251.
     */
    public static String tier(int dex) {
        if (dex == 151 || dex == 251) return "M";
        return switch (of(dex)) {
            case 100 -> "S";
            case 70 -> "A";
            case 45 -> "B";
            case 30 -> "C";
            case 20 -> "D";
            case 10 -> "E";
            case 5 -> "F";
            default -> null;
        };
    }

    /** The species' PR; 0 outside 1..251. */
    static int of(int dex) {
        return dex >= 1 && dex <= 251 ? PR[dex] : 0;
    }
}
