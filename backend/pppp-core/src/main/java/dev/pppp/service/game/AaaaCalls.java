package dev.pppp.service.game;

import dev.pppp.emulator.Bus;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.PlayerRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.PlayerConsoles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AAAA phones: real Pokégear calls in Gold, Silver and Crystal. "???" rings at story moments with static on the line,
 * gets into the phone at the first one, and is AAAA from the moment he wins on Mt. Silver (as Red: V1Red) until the
 * player beats him. Each call goes once per player, only when its moment happens (an achievement unlocking, or a
 * battle ending as {@link #battles} sees it), queued in pibs:player:{userId} fields "call:{id}" ("pending", then the
 * time it rang) and rung, oldest story first, while the player is free in the overworld with a tab open.
 * <p>
 * ROM ({@link #patch}, at insert; pret engine/phone/phone.asm, data/phone/*.asm, addresses from pokecrystal11.sym and
 * pokegold.sym, the same in pokesilver.sym):
 * <ul>
 *   <li>PhoneContacts entry 8 (one of the three unused TRAINER_NONE, PHONE_00 rows in all three games) becomes the
 *   caller: TRAINER_NONE with name index 6 ("???") or 7 ("AAAA"), map N_A, callee script ANYTIME (the Pokégear's
 *   call), no caller script time (so never a random call).</li>
 *   <li>NonTrainerCallerNames (5 names in Gold/Silver, 6 in Crystal) is copied with those two into the free tail of
 *   the phone bank ($24:7fc0) and GetCallerName.NotTrainer's {@code ld hl, NonTrainerCallerNames} pointed at it. Name
 *   index 0 ("----------") stays: the Pokégear shows it for empty slots. Crystal's names carry their ':', Gold's code
 *   adds it.</li>
 *   <li>The texts and scripts go into the free tail of bank 3 ($7b00-$7eff; PokecenterDig has $7f00 on).</li>
 * </ul>
 * Ringing ({@link #ring}): the console's wCurCaller is set to 8 and wCallerContact's caller script to the call's, then
 * the map re-entry script queue (as PokecenterDig) runs {@code pause 30; farsjump Script_ReceivePhoneCall}: the same
 * rings, caller ID box, {@code memcall} of the caller script, "Click!" and hang-up as any call (CheckSpecialPhoneCall
 * does the same with a SpecialPhoneCallList entry, which would mean patching that table and saving its id).
 * <p>
 * Calling ??? back counts in event flags 2046 and 2047 (unused in all three: pret constants/event_flags.asm, "Unused:
 * next 48/116 events" before 2048), so it lives in the save: "......" twice, "so many commands at once" the third
 * time, round again. AAAA answers "AY LMAOCHAMP" until beaten, then thanks for playing.
 */
@Component
public class AaaaCalls {
    private static final Logger log = LoggerFactory.getLogger(AaaaCalls.class);

    private static final Object LINE = 0x4F, CONT = 0x55, DONE = 0x57;

    /** One call: its id (Redis field "call:{id}"), whether it is AAAA's (it reveals him), the text in phone lines of 18. */
    record Call(String id, boolean aaaa, Object... text) {}

    /** In story order: a backlog rings in this order. */
    static final List<Call> CALLS = List.of(
            new Call("badge", false, "............", LINE, "............"),
            new Call("rocket", false, "........ROCKET", LINE, ".........."),
            new Call("blackout", false, "....B....B....", LINE, "..START......"),
            new Call("birb", false, "....a new birb?", LINE, "........."),
            new Call("champion", false, "...... A new", LINE, "champion, huh?"),
            new Call("kanto", false, "Come and get me,", LINE, "Pib."),
            new Call("lost", true, "AY LMAOCHAMP"),
            new Call("beaten", true, "GG champ! Thanks", LINE, "for playing", CONT, "PPPPv2!"));

    /** What ??? says when the player calls: twice the first, then the second. */
    static final Object[] DOTS = {"............", LINE, "............"},
            COMMANDS = {"........ so many", LINE, "commands at", CONT, "once........"};

    /** The achievements that ring a call (Achievements ids). */
    static final Map<String, String> ACHIEVEMENTS = Map.of("zephyr-badge", "badge", "rocket-bust", "rocket",
            "tin-tower-guardian", "birb", "whirl-islands-guardian", "birb", "champion-of-johto", "champion",
            "back-to-kanto", "kanto");

    /** Who answers the Pokégear: ??? (nobody beat or met him yet), AAAA laughing, AAAA beaten. */
    enum Stage { UNKNOWN, REVEALED, BEATEN }

    /**
     * Per game: PhoneContacts, GetCallerName.NotTrainer's {@code ld hl} (+4), NonTrainerCallerNames and its length,
     * Script_ReceivePhoneCall (all bank $24); WRAM wCurCaller, wCallerContact, wMapReentryScriptQueueFlag, wBattleMode
     * (wOtherTrainerClass +2, wBattleType +3), wLinkMode, wBattleResult, wInBattleTowerBattle (Crystal only); script
     * opcodes pause and end (the others are the same in both: macros/scripts/events.asm).
     */
    record Layout(boolean crystal, int contacts, int namesLd, int names, int nameCount, int receiveCall, int curCaller,
                  int callerContact, int reentry, int battleMode, int linkMode, int battleResult, int towerBattle,
                  int pause, int end) {}

    static final Layout CRYSTAL = new Layout(true, 0x445F, 0x43C9, 0x43D6, 6, 0x4241, 0xDBF9, 0xD03F, 0xD45C,
            0xD22D, 0xC2DC, 0xD0EE, 0xCFC0, 0x8B, 0x91),
            GOLD_SILVER = new Layout(false, 0x443A, 0x43BD, 0x43CD, 5, 0x4249, 0xD943, 0xCF2A, 0xD183,
                    0xD116, 0xD042, 0xCFE9, -1, 0x8A, 0x90);

    private static final int PHONE_BANK = 0x24, SCRIPT_BANK = 3, SCRIPTS = 0x7B00, SCRIPTS_END = 0x7F00,
            NAMES = 0x7FC0, NAMES_END = 0x8000, CONTACT = 8, CONTACT_SIZE = 12, SCRIPT2_BANK = 9, ANYTIME = 7,
            UNKNOWN_NAME = 6, AAAA_NAME = 7, FLAG_1 = 2046, FLAG_2 = 2047;
    private static final int SJUMP_FAR = 0x04, IFTRUE = 0x09, ADDCELLNUM = 0x28, CHECKEVENT = 0x31, CLEAREVENT = 0x32,
            SETEVENT = 0x33, WRITETEXT = 0x4C, LD_HL = 0x21, TERMINATOR = 0x50;
    /** pret: RED is trainer class $3f; WIN 0, LOSE 1 (wBattleResult, under BATTLERESULT_BITMASK); BATTLETYPE_CANLOSE 1. */
    private static final int RED = 0x3F, WIN = 0, LOSE = 1, CANLOSE = 1, RESULT_MASK = 0x3F;

    /** Where things went in bank 3: each call's caller script, the ring script, the three callee scripts. */
    record Built(Layout at, byte[] rom, Map<String, Integer> callers, int ring, Map<Stage, Integer> callees) {}

    private final PlayerConsoles consoles;
    private final PlayerRepository players;
    private final PokecenterDig dig;
    private final CartridgeService cartridges;
    private final Map<String, Optional<Built>> built = new ConcurrentHashMap<>(); // by cartridge id

    public AaaaCalls(PlayerConsoles consoles, PlayerRepository players, PokecenterDig dig, CartridgeService cartridges) {
        this.consoles = consoles;
        this.players = players;
        this.dig = dig;
        this.cartridges = cartridges;
        consoles.onBattle((userId, outcome) -> Thread.ofVirtual().start(() -> battle(userId, outcome)));
    }

    @EventListener
    public void onAchievement(LiveEvent event) {
        if (!"ACHIEVEMENT_UNLOCKED".equals(event.type()) || !(event.payload() instanceof Map<?, ?> m)) return;
        String call = ACHIEVEMENTS.get(m.get("id"));
        if (call != null) queue((String) m.get("user"), call);
    }

    /** A battle ended (see {@link #battles}): AAAA laughs at a loss to him, until he is beaten. */
    void battle(String userId, String outcome) {
        if ("lost".equals(outcome) && players.aaaaCalls(userId).containsKey("beaten")) return;
        queue(userId, outcome);
    }

    /** Dev only: queues call again (or "forget": every call). Returns what happened. */
    public String redo(String userId, String call) {
        if ("forget".equals(call)) {
            players.aaaaCalls(userId).keySet().forEach(c -> players.forgetAaaaCall(userId, c));
            return "forgotten";
        }
        if (CALLS.stream().noneMatch(c -> c.id().equals(call))) return "no call " + call;
        players.forgetAaaaCall(userId, call);
        queue(userId, call);
        return "queued";
    }

    void queue(String userId, String call) {
        if (players.queueAaaaCall(userId, call)) log.info("AAAA's {} call queued for {}", call, userId);
    }

    /** Every 5 s: each awake Gen 2 console with a call waiting, free in the overworld, rings with the oldest. */
    @Scheduled(fixedDelay = 5_000)
    public void deliver() {
        for (String id : consoles.running()) {
            if (!consoles.isAwake(id) || consoles.linkedWith(id) != null) continue;
            Map<String, String> calls = players.aaaaCalls(id);
            Call next = CALLS.stream().filter(c -> "pending".equals(calls.get(c.id()))).findFirst().orElse(null);
            if (next == null) continue;
            PokecenterDig.Game game = dig.game(id);
            Built b = built(id);
            if (game == null || b == null) continue;
            if (next.aaaa()) { // his name on the caller ID, and his answer on the Pokégear, from this call on
                Map<String, String> after = new HashMap<>(calls);
                after.put(next.id(), "now");
                consoles.patchRom(id, contact(b, stage(after)));
            }
            Boolean rang = consoles.withBus(id, bus -> PokecenterDig.busy(game, bus) == null && ring(b, bus, b.callers().get(next.id())));
            if (Boolean.TRUE.equals(rang)) {
                players.madeAaaaCall(id, next.id());
                log.info("AAAA's {} call rings for {}", next.id(), id);
            }
        }
    }

    /** Who answers the Pokégear, by the calls that rang (Redis "call:" fields; "pending" ones have not). */
    public static Stage stage(Map<String, String> calls) {
        boolean beaten = calls.containsKey("beaten") && !"pending".equals(calls.get("beaten"));
        boolean lost = calls.containsKey("lost") && !"pending".equals(calls.get("lost"));
        return beaten ? Stage.BEATEN : lost ? Stage.REVEALED : Stage.UNKNOWN;
    }

    /** Arms a call running script (bank 3) as the caller's; the console must be free (PokecenterDig.busy). */
    static boolean ring(Built b, Bus bus, int script) {
        Layout at = b.at();
        bus.setByte(at.curCaller(), CONTACT);
        bus.setByte(at.callerContact() + SCRIPT2_BANK, SCRIPT_BANK);
        bus.setByte(at.callerContact() + SCRIPT2_BANK + 1, script & 0xFF);
        bus.setByte(at.callerContact() + SCRIPT2_BANK + 2, script >> 8);
        bus.setByte(at.reentry() + 1, SCRIPT_BANK);
        bus.setByte(at.reentry() + 2, b.ring() & 0xFF);
        bus.setByte(at.reentry() + 3, b.ring() >> 8);
        bus.setByte(at.reentry(), 1); // last: the flag arms it
        return true;
    }

    private Built built(String userId) {
        String cartridge = consoles.game(userId);
        return cartridge == null ? null : built.computeIfAbsent(cartridge, c -> {
            try {
                return Optional.ofNullable(build(Files.readAllBytes(cartridges.require(c))));
            } catch (IOException | RuntimeException e) {
                log.warn("No AAAA calls in {}: {}", c, e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** The ROM with the calls in it, ??? or AAAA answering as stage says; untouched when not as pret has it. */
    public static byte[] patch(byte[] rom, Stage stage) {
        Built b = build(rom);
        if (b == null) return rom;
        contact(b, stage).forEach((offset, value) -> b.rom()[offset] = (byte) (int) value);
        return b.rom();
    }

    /** PhoneContacts entry 8 for the stage: name index, ANYTIME, callee script (bank 3); as ROM offset -> byte. */
    static Map<Integer, Integer> contact(Built b, Stage stage) {
        int at = offset(PHONE_BANK, b.at().contacts()) + CONTACT * CONTACT_SIZE, callee = b.callees().get(stage);
        return Map.of(at + 1, stage == Stage.UNKNOWN ? UNKNOWN_NAME : AAAA_NAME, at + 4, ANYTIME, at + 5, SCRIPT_BANK,
                at + 6, callee & 0xFF, at + 7, callee >> 8);
    }

    /** Gold, Silver or Crystal laid out as pret has it, with the space free: a patched copy; else null. */
    static Built build(byte[] rom) {
        Boolean crystal = PokemonGen2.crystal(rom);
        if (crystal == null) return null;
        Layout at = crystal ? CRYSTAL : GOLD_SILVER;
        int contact = offset(PHONE_BANK, at.contacts()) + CONTACT * CONTACT_SIZE, ld = offset(PHONE_BANK, at.namesLd());
        int[] unused = {0, 0, 0xFF, 0xFF, 0}; // TRAINER_NONE, PHONE_00, N_A, no callee time
        for (int i = 0; i < unused.length; i++) if ((rom[contact + i] & 0xFF) != unused[i]) return null;
        if ((rom[ld] & 0xFF) != LD_HL || u16(rom, ld + 1) != at.names()) return null;
        if (!zero(rom, offset(SCRIPT_BANK, SCRIPTS), offset(SCRIPT_BANK, SCRIPTS_END))
                || !zero(rom, offset(PHONE_BANK, NAMES), offset(PHONE_BANK, NAMES_END))) return null;
        byte[] out = rom.clone();

        // Names: the game's, padded with "----------" (index 0) up to 6, then ??? and AAAA.
        int table = offset(PHONE_BANK, NAMES), strings = NAMES + 2 * (AAAA_NAME + 1);
        for (int i = 0; i < UNKNOWN_NAME; i++) put16(out, table + 2 * i, u16(rom, offset(PHONE_BANK, at.names()) + 2 * (i < at.nameCount() ? i : 0)));
        String colon = crystal ? ":" : "";
        for (String name : new String[] {"???" + colon, "AAAA" + colon}) {
            byte[] s = name(name);
            put16(out, table + 2 * (name.startsWith("?") ? UNKNOWN_NAME : AAAA_NAME), strings);
            System.arraycopy(s, 0, out, offset(PHONE_BANK, strings), s.length);
            strings += s.length;
        }
        put16(out, ld + 1, NAMES);

        // Bank 3: texts, then scripts.
        var asm = new Object() {
            int pc = SCRIPTS;

            int put(byte[] bytes) {
                System.arraycopy(bytes, 0, out, offset(SCRIPT_BANK, pc), bytes.length);
                pc += bytes.length;
                return pc - bytes.length;
            }

            int put(int... bytes) {
                byte[] b = new byte[bytes.length];
                for (int i = 0; i < bytes.length; i++) b[i] = (byte) bytes[i];
                return put(b);
            }
        };
        Map<String, Integer> texts = new LinkedHashMap<>(), callers = new LinkedHashMap<>();
        for (Call c : CALLS) texts.put(c.id(), asm.put(PokecenterDig.text(text(c.text()))));
        int dots = asm.put(PokecenterDig.text(text(DOTS))), commands = asm.put(PokecenterDig.text(text(COMMANDS)));
        for (Call c : CALLS) {
            int t = texts.get(c.id());
            callers.put(c.id(), "badge".equals(c.id())
                    ? asm.put(WRITETEXT, t & 0xFF, t >> 8, ADDCELLNUM, CONTACT, at.end())
                    : asm.put(WRITETEXT, t & 0xFF, t >> 8, at.end()));
        }
        int ring = asm.put(at.pause(), 30, SJUMP_FAR, PHONE_BANK, at.receiveCall() & 0xFF, at.receiveCall() >> 8);
        // ??? on the Pokégear: flag 1 clear: set it, dots. Flag 2 clear: set it, dots. Else clear both, commands.
        int unknown = asm.pc, second = unknown + 13, third = second + 13;
        asm.put(CHECKEVENT, FLAG_1 & 0xFF, FLAG_1 >> 8, IFTRUE, second & 0xFF, second >> 8,
                SETEVENT, FLAG_1 & 0xFF, FLAG_1 >> 8, WRITETEXT, dots & 0xFF, dots >> 8, at.end());
        asm.put(CHECKEVENT, FLAG_2 & 0xFF, FLAG_2 >> 8, IFTRUE, third & 0xFF, third >> 8,
                SETEVENT, FLAG_2 & 0xFF, FLAG_2 >> 8, WRITETEXT, dots & 0xFF, dots >> 8, at.end());
        asm.put(CLEAREVENT, FLAG_1 & 0xFF, FLAG_1 >> 8, CLEAREVENT, FLAG_2 & 0xFF, FLAG_2 >> 8,
                WRITETEXT, commands & 0xFF, commands >> 8, at.end());
        int lost = texts.get("lost"), beaten = texts.get("beaten");
        int laughing = asm.put(WRITETEXT, lost & 0xFF, lost >> 8, at.end());
        int thanks = asm.put(WRITETEXT, beaten & 0xFF, beaten >> 8, at.end());
        if (asm.pc > SCRIPTS_END) throw new IllegalStateException("AAAA's calls outgrew bank 3's free space");
        return new Built(at, out, callers, ring, Map.of(Stage.UNKNOWN, unknown, Stage.REVEALED, laughing, Stage.BEATEN, thanks));
    }

    /**
     * A frame hook for a console (one each): tells outcome how each battle ended, once: "beaten" (won against Red's
     * class, AAAA: V1Red), "lost" (lost to him) or "blackout" (lost one that blacks out: not BATTLETYPE_CANLOSE, a
     * link or a Battle Tower battle). wBattleResult stays after CleanUpBattleRAM clears wBattleMode, class and type
     * (pret engine/battle/core.asm), so those are kept from the battle's last frame. Not Gold/Silver/Crystal: nothing.
     */
    public static Consumer<Bus> battles(Consumer<String> outcome) {
        return new Consumer<>() {
            Layout at;
            boolean known, inBattle, noBlackout;
            int trainerClass, type;

            @Override
            public void accept(Bus bus) {
                if (!known) {
                    known = true;
                    Boolean crystal = PokecenterDig.crystal(bus);
                    at = crystal == null ? null : crystal ? CRYSTAL : GOLD_SILVER;
                }
                if (at == null || bus.wramBank() != 1) return;
                if (u8(bus, at.battleMode()) != 0) {
                    inBattle = true;
                    trainerClass = u8(bus, at.battleMode() + 2);
                    type = u8(bus, at.battleMode() + 3);
                    noBlackout |= u8(bus, at.linkMode()) != 0 || at.towerBattle() >= 0 && (u8(bus, at.towerBattle()) & 1) != 0;
                    return;
                }
                if (!inBattle) return;
                boolean skip = noBlackout;
                inBattle = noBlackout = false;
                if (skip) return;
                int result = u8(bus, at.battleResult()) & RESULT_MASK;
                if (trainerClass == RED && result == WIN) outcome.accept("beaten");
                else if (result == LOSE && type != CANLOSE) outcome.accept(trainerClass == RED ? "lost" : "blackout");
            }
        };
    }

    /** Phone text: strings and control bytes, ended by done. */
    private static Object[] text(Object... parts) {
        Object[] out = java.util.Arrays.copyOf(parts, parts.length + 1);
        out[parts.length] = DONE;
        return out;
    }

    /** A name in pret's charmap, '@'-ended. */
    private static byte[] name(String s) {
        byte[] t = PokecenterDig.text(s); // TX_START first
        byte[] out = java.util.Arrays.copyOfRange(t, 1, t.length + 1);
        out[out.length - 1] = TERMINATOR;
        return out;
    }

    private static boolean zero(byte[] rom, int from, int to) {
        for (int i = from; i < to; i++) if (rom[i] != 0) return false;
        return true;
    }

    private static int offset(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }

    private static int u16(byte[] rom, int at) {
        return rom[at] & 0xFF | (rom[at + 1] & 0xFF) << 8;
    }

    private static void put16(byte[] rom, int at, int value) {
        rom[at] = (byte) value;
        rom[at + 1] = (byte) (value >> 8);
    }

    private static int u8(Bus bus, int address) {
        return bus.getByte(address) & 0xFF;
    }
}
