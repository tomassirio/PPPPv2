package dev.pppp.service.game;

import dev.pppp.repository.PlayerRepository;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.emulator.Bus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AAAA (PPPP v1's player, who now waits on Mt. Silver as Red: V1Red) thanks every Gold, Silver and Crystal player with
 * a Portrait Mail (Articuno, AAAA's lead) in their PC's mailbox, once, after their game has been saved.
 * <p>
 * Written into the cartridge's save RAM while the player is free in the overworld (PokecenterDig.busy: no battle,
 * menu, dialog or step, so the game's own save RAM is closed), opening and closing it as the game's OpenSRAM and
 * CloseSRAM do (pret home/sram.asm). Addresses from pret ram/sram.asm with layout.link: "SRAM Bank 0" at $a600 holds
 * sPartyMail (6) and sPartyMailBackup (6) of MAIL_STRUCT_LENGTH $2f, then sMailboxCount, sMailboxes (10), and the same
 * again as backup (BackupPartyMonMail copies one onto the other). A mailmsg (macros/ram.asm) is two 16-character lines
 * around '<NEXT>', a 10-byte author (Crystal: 8 + nationality; "@@" reads as English, european_mail.asm), the author's
 * ID, species and the mail item. The message prints up to its first '@' (MailGFX_PlaceMessage), so line 1 is padded
 * with spaces. Same in all three games.
 */
@Component
public class AaaaMail {
    private static final Logger log = LoggerFactory.getLogger(AaaaMail.class);

    private static final int RAMG = 0x0000, RAMB = 0x4000, SRAM_ENABLE = 0x0A, SRAM_DISABLE = 0x00;
    private static final int CHECK_VALUE = 0xA008, SAVE_CHECK_VALUE_1 = 99; // sCheckValue1, SRAM bank 1
    static final int MAILBOX_COUNT = 0xA600 + 12 * 0x2F, MAILBOXES = MAILBOX_COUNT + 1, MAIL_LENGTH = 0x2F,
            CAPACITY = 10, MAILBOX_BYTES = 1 + CAPACITY * MAIL_LENGTH, BACKUP = MAILBOX_COUNT + MAILBOX_BYTES;
    private static final int LINE = 16, NEXT = 0x4E, END = 0x50, SPACE = 0x7F;
    private static final int ARTICUNO = 144, PORTRAITMAIL = 0xB7, AUTHOR_ID = 1;

    /** The Mail (one is two lines of 16; a list so more could follow in mailbox order). */
    static final String[][] TEXT = {{"Thank you, Pib!", "Mt.Silver awaits"}};

    private final PlayerConsoles consoles;
    private final PlayerRepository players;
    private final PokecenterDig dig;

    public AaaaMail(PlayerConsoles consoles, PlayerRepository players, PokecenterDig dig) {
        this.consoles = consoles;
        this.players = players;
        this.dig = dig;
    }

    /** Every 10 s: each running Gen 2 console that has not had the Mail yet and is free gets it. */
    @Scheduled(fixedDelay = 10_000)
    public void deliver() {
        for (String id : consoles.running()) {
            if (!consoles.isAwake(id) || consoles.linkedWith(id) != null || players.aaaaMail(id)) continue;
            PokecenterDig.Game game = dig.game(id);
            if (game == null) continue; // not Gold, Silver or Crystal
            Boolean done = consoles.withBus(id, bus -> PokecenterDig.busy(game, bus) == null && write(bus));
            if (Boolean.TRUE.equals(done)) {
                players.markAaaaMail(id);
                log.info("AAAA's Mail delivered to {}", id);
            }
        }
    }

    /**
     * Adds the Mail to the mailbox and its backup. False (nothing written) before the first save, or while the
     * mailbox has no room for all of them (the next try may find room).
     */
    static boolean write(Bus bus) {
        bus.setByte(RAMG, SRAM_ENABLE);
        try {
            bus.setByte(RAMB, 1);
            if (bus.getByte(CHECK_VALUE) != SAVE_CHECK_VALUE_1) return false; // never saved: no mailbox yet
            bus.setByte(RAMB, 0);
            int count = bus.getByte(MAILBOX_COUNT);
            if (count + TEXT.length > CAPACITY) return false;
            for (int i = 0; i < TEXT.length; i++) {
                byte[] mail = mail(TEXT[i][0], TEXT[i][1]);
                int at = MAILBOXES + (count + i) * MAIL_LENGTH;
                for (int b = 0; b < MAIL_LENGTH; b++) bus.setByte(at + b, mail[b] & 0xFF);
            }
            bus.setByte(MAILBOX_COUNT, count + TEXT.length);
            for (int b = 0; b < MAILBOX_BYTES; b++) bus.setByte(BACKUP + b, bus.getByte(MAILBOX_COUNT + b));
            return true;
        } finally {
            bus.setByte(RAMG, SRAM_DISABLE);
        }
    }

    /** One mailmsg: line 1 (spaces), '<NEXT>', line 2 ('@'), author "AAAA", ID, Articuno, Portrait Mail. */
    static byte[] mail(String line1, String line2) {
        byte[] m = new byte[MAIL_LENGTH];
        int o = 0;
        for (int i = 0; i < LINE; i++) m[o++] = (byte) (i < line1.length() ? encode(line1.charAt(i)) : SPACE);
        m[o++] = (byte) NEXT;
        for (int i = 0; i < LINE; i++) m[o++] = (byte) (i < line2.length() ? encode(line2.charAt(i)) : END);
        for (int i = 0; i < 10; i++) m[o++] = (byte) (i < 4 ? 0x80 : END); // "AAAA@@@@@@"
        m[o++] = (byte) (AUTHOR_ID >> 8);
        m[o++] = (byte) AUTHOR_ID;
        m[o++] = (byte) ARTICUNO;
        m[o] = (byte) PORTRAITMAIL;
        return m;
    }

    /** pret constants/charmap.asm, the characters the Mails use. */
    static int encode(char c) {
        if (c >= 'A' && c <= 'Z') return 0x80 + (c - 'A');
        if (c >= 'a' && c <= 'z') return 0xA0 + (c - 'a');
        if (c >= '0' && c <= '9') return 0xF6 + (c - '0');
        return switch (c) {
            case ' ' -> SPACE;
            case '!' -> 0xE7;
            case ',' -> 0xF4;
            case '.' -> 0xE8;
            case '\'' -> 0xE0;
            case '-' -> 0xE3;
            default -> throw new IllegalArgumentException("No Game Boy character for " + c);
        };
    }
}
