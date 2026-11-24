package dev.pppp.service.game;

import dev.pppp.emulator.Bus;
import java.util.Arrays;

/**
 * Mt. Silver's Red fights as PPPP v1's player: AAAA with the team that beat v1's Pokémon League, as its Hall of Fame
 * recorded it (v1's battery save, sHallOfFame: species, nicknames and order; the moves are those six Pokémon's in the
 * same save, 2026-11-23), raised to Red's own levels: Red's 81, 77, 77, 77, 75 and 73 handed out in order of their
 * Hall of Fame levels. Gold, Silver and Crystal; other ROMs are left alone. One per console:
 * {@link #patch} is the ROM patch for {@code EmulatorHost.insert}, {@link #nicknames} its frame hook.
 * <p>
 * ROM: Red's trainer record (pret data/trainers/parties.asm RedGroup, "RED@" TRAINERTYPE_MOVES) is rewritten as
 * AAAA's into the zero-filled tail of the same bank, and RedGroup's entry in TrainerGroups is pointed at it. Only in
 * memory: the file and its header (save states check the header) are untouched.
 * <p>
 * Nicknames: Gen 2 trainer data has none, and LoadEnemyMon (engine/battle/core.asm) puts the species name into
 * wEnemyMonNickname at each send-out (wOTPartyMonNicknames is only read in link and Battle Tower battles). So while a
 * battle against class RED runs, wEnemyMonNickname is set every frame to the nickname of party slot wCurOTMon.
 * CleanUpBattleRAM clears wBattleMode after the battle.
 */
public final class V1Red {

    /** "RED@", TRAINERTYPE_MOVES, then Lv 81 Pikachu: the start of Red's record in all three games. */
    private static final byte[] RED = {(byte) 0x91, (byte) 0x84, (byte) 0x83, 0x50, 1, 81, 25};
    /** pret constants/trainer_constants.asm: RED is class $3f (TrainerGroups entry 62). */
    private static final int RED_CLASS = 0x3F, BANK = 0x4000;

    /** "AAAA@", TRAINERTYPE_MOVES, {level, species (Pokédex number), 4 moves (ids 1-165 match Gen 1)} x 6, end. */
    static final byte[] RECORD = bytes(
            0x80, 0x80, 0x80, 0x80, 0x50, 1,
            81, 144, 64, 58, 19, 59,    // Articuno (HoF Lv 58): Peck, Ice Beam, Fly, Blizzard
            73, 148, 21, 59, 86, 87,    // Dragonair (40): Slam, Blizzard, Thunder Wave, Thunder
            77, 6, 5, 126, 90, 53,      // Charizard (47): Mega Punch, Fire Blast, Fissure, Flamethrower
            77, 145, 84, 65, 87, 0,     // Zapdos (52): ThunderShock, Drill Peck, Thunder
            75, 97, 29, 149, 94, 93,    // Hypno (44): Headbutt, Psywave, Psychic, Confusion
            77, 75, 70, 89, 153, 91,    // Graveler (47): Strength, Earthquake, Explosion, Dig
            0xFF);

    /** The Hall of Fame's nicknames in the shared Gen 1/2 charmap, "@"-padded to MON_NAME_LENGTH (11), its order. */
    static final byte[] NICKNAMES = names(
            bytes(0x88, 0x82, 0x84, 0x7F, 0x81, 0x88, 0x91, 0x81),        // ICE BIRB
            bytes(0xEF, 0xE6, 0xF5, 0xE6, 0x7F, 0xE1, 0xE2, 0xE7),        // ♂?♀? PkMn!
            bytes(0x82, 0x87, 0x8E, 0x91, 0x88, 0x99, 0x80, 0x91, 0x83),  // CHORIZARD (renamed since; the HoF has CHARIZARD)
            bytes(0x91, 0x80, 0x98, 0x88, 0x93, 0x8E),                    // RAYITO
            bytes(0x8F, 0x80, 0x89, 0x80),                                // PAJA
            bytes(0x82, 0x80, 0x92, 0x82, 0x8E, 0x93, 0x84));             // CASCOTE

    /** wBattleMode, wOtherTrainerClass, wCurOTMon, wEnemyMonNickname (pret pokecrystal11.sym / pokegold.sym). */
    private int battleMode, trainerClass, curOTMon, enemyNickname;

    /** The ROM with Red replaced, or the same array when it is not a Gen 2 Pokémon ROM laid out as expected. */
    public byte[] patch(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        int at = indexOf(rom, RED, 0);
        if (crystal == null || at < 0 || indexOf(rom, RED, at + 1) >= 0) return rom;
        int bank = at / BANK * BANK, slot = bank + BANK - 0x40, groupPointer = -1;
        for (int i = bank; i < slot; i++) {
            if ((rom[i] & 0xFF | (rom[i + 1] & 0xFF) << 8) != BANK + at % BANK) continue;
            if (groupPointer >= 0) return rom; // ambiguous
            groupPointer = i;
        }
        for (int i = slot; i < bank + BANK; i++) if (rom[i] != 0) return rom; // not free
        if (groupPointer < 0) return rom;
        byte[] out = rom.clone();
        System.arraycopy(RECORD, 0, out, slot, RECORD.length);
        out[groupPointer] = (byte) (BANK + slot % BANK);
        out[groupPointer + 1] = (byte) ((BANK + slot % BANK) >> 8);
        battleMode = crystal ? 0xD22D : 0xD116;
        trainerClass = battleMode + 2;
        curOTMon = crystal ? 0xC663 : 0xCB41;
        enemyNickname = crystal ? 0xC616 : 0xCAF6;
        return out;
    }

    /** Frame hook: AAAA's nicknames while Red's battle runs. No-op unless {@link #patch} changed the ROM. */
    public void nicknames(Bus bus) {
        if (enemyNickname == 0) return;
        int svbk = bus.getByte(0xFF70);
        if (svbk != 0xFF && (svbk & 7) > 1) return; // another WRAM bank is in
        if (bus.getByte(battleMode) != 2 || bus.getByte(trainerClass) != RED_CLASS) return;
        int slot = bus.getByte(curOTMon);
        if (slot >= NICKNAMES.length / 11) return;
        for (int i = 0; i < 11; i++) {
            int b = NICKNAMES[slot * 11 + i] & 0xFF;
            if (bus.getByte(enemyNickname + i) != b) bus.setByte(enemyNickname + i, b);
        }
    }

    private static byte[] names(byte[]... names) {
        byte[] out = new byte[names.length * 11];
        Arrays.fill(out, (byte) 0x50);
        for (int i = 0; i < names.length; i++) System.arraycopy(names[i], 0, out, i * 11, names[i].length);
        return out;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
