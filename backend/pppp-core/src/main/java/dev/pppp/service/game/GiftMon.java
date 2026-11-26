package dev.pppp.service.game;

import dev.pppp.emulator.Bus;
import java.util.List;
import java.util.Random;

/**
 * Event Pokémon as Mystery Gifts (MysteryGifts): the ones Gen 2 gave out with fixed movesets, built as the game builds a
 * party member (pret macros/ram.asm party_struct, 48 bytes) and added to the end of the party in WRAM: species list,
 * struct, OT name ("AAAA", ID 1: like any traded Pokémon it obeys up to the level the player's badges allow) and
 * nickname (the species' name), and marked seen and caught in the Pokédex. Stats by Gen 2's formula with no stat exp;
 * exp from the species' growth rate; happiness 70 (an egg: the species' step cycles). Only into a party with room.
 */
final class GiftMon {
    private GiftMon() {}

    /** species, level, moves (as the ROM names them), an egg, shiny. */
    record Spec(String species, int level, List<String> moves, boolean egg, boolean shiny) {}

    /** Crystal's Odd Egg (data/events/odd_eggs.asm): a baby knowing Dizzy Punch; percent chances, the shiny ones after. */
    private static final Object[][] ODD_EGGS = {
            {"Pichu", 8, 1, List.of("ThunderShock", "Charm", "Dizzy Punch")},
            {"Cleffa", 16, 3, List.of("Pound", "Charm", "Dizzy Punch")},
            {"Igglybuff", 16, 3, List.of("Sing", "Charm", "Dizzy Punch")},
            {"Smoochum", 14, 2, List.of("Pound", "Lick", "Dizzy Punch")},
            {"Magby", 10, 2, List.of("Ember", "Dizzy Punch")},
            {"Elekid", 12, 2, List.of("Quick Attack", "Leer", "Dizzy Punch")},
            {"Tyrogue", 10, 1, List.of("Tackle", "Dizzy Punch")}};

    /** One roll of the Odd Egg, as Crystal's odds have it (100 in all). */
    @SuppressWarnings("unchecked")
    static Spec oddEgg(Random random) {
        int roll = random.nextInt(100);
        for (Object[] e : ODD_EGGS) {
            int normal = (int) e[1], shiny = (int) e[2];
            if (roll < normal) return new Spec((String) e[0], 5, (List<String>) e[3], true, false);
            roll -= normal;
            if (roll < shiny) return new Spec((String) e[0], 5, (List<String>) e[3], true, true);
            roll -= shiny;
        }
        throw new IllegalStateException("odds do not add up to 100");
    }

    private static final int PARTY_LENGTH = 6, MON = 48, NAME = 11, EGG = 0xFD, END = 0xFF, NAME_END = 0x50,
            BASE_HAPPINESS = 70, BASE_EGG_STEPS = 15, BASE_GROWTH_RATE = 22, DEX_BYTES = 32, AAAA_ID = 1;
    private static final int[] SHINY_ATTACK = {2, 3, 6, 7, 10, 11, 14, 15};
    private static final byte[] AAAA = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, NAME_END},
            EGG_NAME = {(byte) 0x84, (byte) 0x86, (byte) 0x86, NAME_END};

    /**
     * Adds the Pokémon to the party; false (nothing written) when the party is full, the list does not look like one,
     * or this ROM lacks the species or a move.
     */
    static boolean add(Bus bus, PokemonGen2 game, Spec spec, Random random) {
        int species = game.speciesId(spec.species());
        int[] moves = spec.moves().stream().mapToInt(game::moveId).toArray();
        if (species == 0 || moves.length > 4 || java.util.Arrays.stream(moves).anyMatch(m -> m == 0)) return false;
        int p = game.partyCount(), count = bus.getByte(p);
        if (count >= PARTY_LENGTH || bus.getByte(p + 1 + count) != END) return false;
        byte[] base = game.baseData(species);
        if ((base[0] & 0xFF) != species) return false;

        int atk, def, spd, spc;
        if (spec.shiny()) {
            atk = SHINY_ATTACK[random.nextInt(SHINY_ATTACK.length)];
            def = spd = spc = 10;
        } else {
            atk = random.nextInt(16);
            def = random.nextInt(16);
            spd = random.nextInt(16);
            spc = random.nextInt(16);
        }
        int hpDv = (atk & 1) << 3 | (def & 1) << 2 | (spd & 1) << 1 | spc & 1, level = spec.level();
        int[] dvs = {hpDv, atk, def, spd, spc, spc}; // HP, Atk, Def, Spd, SpAtk, SpDef (one Special DV)
        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) {
            stats[i] = ((base[1 + i] & 0xFF) + dvs[i]) * 2 * level / 100 + (i == 0 ? level + 10 : 5); // BASE_STATS order
        }

        int at = p + 1 + PARTY_LENGTH + 1 + count * MON;
        for (int i = 0; i < MON; i++) bus.setByte(at + i, 0);
        bus.setByte(at, species);
        for (int i = 0; i < moves.length; i++) {
            bus.setByte(at + 2 + i, moves[i]);
            bus.setByte(at + 23 + i, game.movePp(moves[i]));
        }
        bus.setByte(at + 6, AAAA_ID >> 8);
        bus.setByte(at + 7, AAAA_ID);
        int exp = exp(base[BASE_GROWTH_RATE] & 0xFF, level);
        bus.setByte(at + 8, exp >> 16);
        bus.setByte(at + 9, exp >> 8);
        bus.setByte(at + 10, exp);
        bus.setByte(at + 21, atk << 4 | def);
        bus.setByte(at + 22, spd << 4 | spc);
        bus.setByte(at + 27, spec.egg() ? base[BASE_EGG_STEPS] & 0xFF : BASE_HAPPINESS);
        bus.setByte(at + 31, level);
        bus.setByte(at + 34, stats[0] >> 8); // HP = max HP
        bus.setByte(at + 35, stats[0]);
        for (int i = 0; i < 6; i++) {
            bus.setByte(at + 36 + i * 2, stats[i] >> 8);
            bus.setByte(at + 37 + i * 2, stats[i]);
        }

        int ot = p + 1 + PARTY_LENGTH + 1 + PARTY_LENGTH * MON, nick = ot + PARTY_LENGTH * NAME;
        write(bus, ot + count * NAME, AAAA);
        write(bus, nick + count * NAME, spec.egg() ? EGG_NAME : named(game.rawName(species)));
        bus.setByte(p + 1 + count, spec.egg() ? EGG : species);
        bus.setByte(p + 2 + count, END);
        bus.setByte(p, count + 1);
        if (!spec.egg()) {
            int bit = species - 1, caught = game.dexCaught();
            for (int flags : new int[] {caught, caught + DEX_BYTES}) {
                bus.setByte(flags + bit / 8, bus.getByte(flags + bit / 8) | 1 << bit % 8);
            }
        }
        return true;
    }

    /** A party member's bytes as the game keeps them: party_struct (48), OT name and nickname (11 each). */
    record Raw(byte[] mon, byte[] ot, byte[] nick) {}

    /** The bytes of a party slot, or null past the party. */
    static Raw read(Bus bus, PokemonGen2 game, int slot) {
        int p = game.partyCount(), count = bus.getByte(p);
        if (slot < 0 || slot >= count || count > PARTY_LENGTH) return null;
        int mons = p + 1 + PARTY_LENGTH + 1, ot = mons + PARTY_LENGTH * MON, nick = ot + PARTY_LENGTH * NAME;
        return new Raw(bytes(bus, mons + slot * MON, MON), bytes(bus, ot + slot * NAME, NAME), bytes(bus, nick + slot * NAME, NAME));
    }

    /** Whether the slot holds an egg (the species list has EGG for it). */
    static boolean egg(Bus bus, PokemonGen2 game, int slot) {
        return bus.getByte(game.partyCount() + 1 + slot) == EGG;
    }

    /** Takes a member out of the party, the ones after it moving up (as RemoveMonFromParty). */
    static void remove(Bus bus, PokemonGen2 game, int slot) {
        int p = game.partyCount(), count = bus.getByte(p);
        int mons = p + 1 + PARTY_LENGTH + 1, ot = mons + PARTY_LENGTH * MON, nick = ot + PARTY_LENGTH * NAME;
        for (int i = slot; i < count - 1; i++) {
            bus.setByte(p + 1 + i, bus.getByte(p + 2 + i));
            copy(bus, mons + (i + 1) * MON, mons + i * MON, MON);
            copy(bus, ot + (i + 1) * NAME, ot + i * NAME, NAME);
            copy(bus, nick + (i + 1) * NAME, nick + i * NAME, NAME);
        }
        bus.setByte(p + count, END);
        bus.setByte(p, count - 1);
    }

    /** Adds a traded Pokémon's bytes to the end of the party, marked seen and caught; false when the party is full. */
    static boolean put(Bus bus, PokemonGen2 game, Raw raw) {
        int p = game.partyCount(), count = bus.getByte(p);
        if (count >= PARTY_LENGTH || bus.getByte(p + 1 + count) != END) return false;
        int species = raw.mon()[0] & 0xFF;
        int mons = p + 1 + PARTY_LENGTH + 1, ot = mons + PARTY_LENGTH * MON, nick = ot + PARTY_LENGTH * NAME;
        for (int i = 0; i < MON; i++) bus.setByte(mons + count * MON + i, raw.mon()[i] & 0xFF);
        write(bus, ot + count * NAME, raw.ot());
        write(bus, nick + count * NAME, raw.nick());
        bus.setByte(p + 1 + count, species);
        bus.setByte(p + 2 + count, END);
        bus.setByte(p, count + 1);
        int bit = species - 1, caught = game.dexCaught();
        for (int flags : new int[] {caught, caught + DEX_BYTES}) bus.setByte(flags + bit / 8, bus.getByte(flags + bit / 8) | 1 << bit % 8);
        return true;
    }

    /** Evolve when traded (pret data/pokemon/evos_attacks.asm EVOLVE_TRADE): species, into, the item it holds (null: any but an Everstone). */
    private static final Object[][] TRADE_EVOLUTIONS = {{64, 65, null}, {67, 68, null}, {75, 76, null}, {93, 94, null},
            {95, 208, "Metal Coat"}, {123, 212, "Metal Coat"}, {117, 230, "Dragon Scale"}, {61, 186, "King's Rock"},
            {79, 199, "King's Rock"}, {137, 233, "Up-Grade"}};

    /**
     * The Pokémon as it arrives from a trade in this game: evolved when its species does so by trade (the item it
     * needed used up), stats recalculated, a nickname that was the species' name renamed; a held item this ROM does
     * not have dropped. The same bytes otherwise.
     */
    static Raw arrive(PokemonGen2 game, Raw raw) {
        byte[] mon = raw.mon().clone(), nick = raw.nick();
        String held = game.itemName(mon[1] & 0xFF);
        if (mon[1] != 0 && (held == null || held.equalsIgnoreCase("Teru-Sama"))) mon[1] = 0;
        int species = mon[0] & 0xFF, item = mon[1] & 0xFF, into = 0;
        for (Object[] e : TRADE_EVOLUTIONS) {
            if ((int) e[0] != species) continue;
            if (e[2] == null ? item != game.itemId("Everstone") : item == game.itemId((String) e[2])) {
                into = (int) e[1];
                if (e[2] != null) mon[1] = 0;
            }
        }
        if (into == 0) return new Raw(mon, raw.ot(), nick);
        mon[0] = (byte) into;
        stats(game, mon);
        byte[] was = game.rawName(species); // the game's nickname check: the text up to "@"
        if (PokemonRed.text(nick, 0, NAME).equals(PokemonRed.text(was, 0, was.length))) nick = named(game.rawName(into));
        return new Raw(mon, raw.ot(), nick);
    }

    /** Recalculates a party_struct's stats for its species and level (Gen 2's formula with its DVs and stat exp); HP moves with max HP. */
    static void stats(PokemonGen2 game, byte[] mon) {
        int level = mon[31] & 0xFF, atk = (mon[21] & 0xFF) >> 4, def = mon[21] & 0xF, spd = (mon[22] & 0xFF) >> 4, spc = mon[22] & 0xF;
        byte[] base = game.baseData(mon[0] & 0xFF);
        int[] dvs = {(atk & 1) << 3 | (def & 1) << 2 | (spd & 1) << 1 | spc & 1, atk, def, spd, spc, spc}, exp = {0, 1, 2, 3, 4, 4};
        int oldMax = u16(mon, 36);
        for (int i = 0; i < 6; i++) {
            int statExp = u16(mon, 11 + exp[i] * 2), root = (int) Math.ceil(Math.sqrt(statExp));
            int v = (((base[1 + i] & 0xFF) + dvs[i]) * 2 + Math.min(255, root) / 4) * level / 100 + (i == 0 ? level + 10 : 5);
            mon[36 + i * 2] = (byte) (v >> 8);
            mon[37 + i * 2] = (byte) v;
        }
        int hp = u16(mon, 34);
        if (hp > 0) {
            hp = Math.max(1, hp + u16(mon, 36) - oldMax);
            mon[34] = (byte) (hp >> 8);
            mon[35] = (byte) hp;
        }
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xFF) << 8 | b[at + 1] & 0xFF;
    }

    private static byte[] bytes(Bus bus, int at, int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) bus.getByte(at + i);
        return out;
    }

    private static void copy(Bus bus, int from, int to, int n) {
        for (int i = 0; i < n; i++) bus.setByte(to + i, bus.getByte(from + i));
    }

    /** Exp at a level, by growth rate (pret engine/pokemon/experience.asm GrowthRates, n = level). */
    static int exp(int growth, int n) {
        int n3 = n * n * n, n2 = n * n;
        return Math.max(0, switch (growth) {
            case 1 -> 3 * n3 / 4 + 10 * n2 - 30; // slightly fast
            case 2 -> 3 * n3 / 4 + 20 * n2 - 70; // slightly slow
            case 3 -> 6 * n3 / 5 - 15 * n2 + 100 * n - 140; // medium slow
            case 4 -> 4 * n3 / 5; // fast
            case 5 -> 5 * n3 / 4; // slow
            default -> n3; // medium fast
        });
    }

    private static byte[] named(byte[] raw) {
        byte[] out = java.util.Arrays.copyOf(raw, NAME);
        out[NAME - 1] = NAME_END;
        return out;
    }

    private static void write(Bus bus, int at, byte[] text) {
        for (int i = 0; i < NAME; i++) bus.setByte(at + i, i < text.length ? text[i] & 0xFF : NAME_END);
    }
}
