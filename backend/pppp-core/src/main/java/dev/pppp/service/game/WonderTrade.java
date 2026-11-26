package dev.pppp.service.game;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.WonderMon;
import dev.pppp.repository.WonderRepository;
import dev.pppp.service.PlayerConsoles;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Wonder Trade, between Pibs only: a Gold, Silver or Crystal player sends one of their party, sight unseen, once a day
 * (UTC). If another Pib's Pokémon waits in the pool (the oldest first), the two swap: theirs joins the sender's party at
 * once, and the sender's goes to that Pib, delivered (every 10 s) once their console runs free with room in the party.
 * With nobody else waiting, the Pokémon waits in the pool for the next Pib who sends one.
 * <p>
 * On the console it is one step, free in the overworld: the Pokémon leaves the party (its bytes kept as the game has
 * them: OT, DVs, moves, item, nickname) and the other joins at the end, as a trade would have it: evolving if it
 * evolves by trade (GiftMon.arrive), marked in the Pokédex. Then the game is saved as a link trade saves it
 * (TradeSave: the Pokémon data, main copy and backup), so Reset cannot undo it; the same when a Pokémon arrives later.
 * The game must have been saved once before; a Pokémon holding Mail cannot go.
 * Events: WONDER_TRADE {user, gave, got, from} (a swap), WONDER_ARRIVED {user, got, from} (a delivery), WONDER_POOL
 * {users} (who has one waiting, oldest first) whenever the pool changes.
 * <p>
 * Only once the player's Cable Club is open (ProgressTracker.cableClubOpen), as for link trades. An egg cannot go, nor
 * the last Pokémon that can fight. A Pokémon goes in once per player (WonderRepository's
 * "given" keys): a player who resets without saving has theirs back, but cannot send the same one again.
 */
@Service
public class WonderTrade {
    private static final Logger log = LoggerFactory.getLogger(WonderTrade.class);

    /** A Pokémon as the dialog shows it: species = Pokédex number; evolvedFrom: the species it was before the trade. */
    public record Mon(int species, String name, String nickname, String ot, int level, boolean shiny, Integer evolvedFrom) {}

    /** gave; got and from (who sent it) null when it went into the pool to wait. */
    public record Traded(Mon gave, Mon got, String from) {}

    /** One of theirs waiting in the pool, since when. */
    public record Waiting(int species, String name, String nickname, int level, boolean shiny, long since) {}

    /**
     * waiting: other Pibs' Pokémon in the pool; mine: theirs waiting there; tradedToday: today's Wonder Trade is used;
     * incoming: Pokémon traded for theirs, on their way to the party; open: their Cable Club is (Wonder Trade with it).
     */
    public record Pool(int waiting, List<Waiting> mine, boolean tradedToday, long incoming, boolean open) {}

    /** What the console step did: error (nothing changed) or the bytes that left and arrived (null: none). */
    private record Outcome(String error, GiftMon.Raw gave, GiftMon.Raw got) {}

    private final PlayerConsoles consoles;
    private final PokecenterDig dig;
    private final WonderRepository repo;
    private final ApplicationEventPublisher events;
    private final ProgressTracker tracker;
    private final dev.pppp.repository.LinkHistoryRepository history;

    public WonderTrade(PlayerConsoles consoles, PokecenterDig dig, WonderRepository repo, ApplicationEventPublisher events,
                       ProgressTracker tracker, dev.pppp.repository.LinkHistoryRepository history) {
        this.history = history;
        this.tracker = tracker;
        this.consoles = consoles;
        this.dig = dig;
        this.repo = repo;
        this.events = events;
    }

    public Pool pool(String userId) {
        List<WonderMon> all = repo.pool();
        List<Waiting> mine = all.stream().filter(m -> m.user().equals(userId))
                .map(m -> new Waiting(m.species(), m.name(), m.nickname(), m.level(), m.shiny(), m.at())).toList();
        return new Pool(all.size() - mine.size(), mine, today().equals(repo.day(userId)), repo.incoming(userId),
                tracker.cableClubOpen(userId));
    }

    /**
     * Sends the party's slot (0-5), which must still hold species: swapped with the oldest Pokémon another Pib has
     * waiting, else into the pool. IllegalStateException (409) when it cannot happen now; nothing changed then.
     */
    // ponytail: one trade at a time for everyone; per-player locks if the pool ever gets busy.
    public synchronized Traded trade(String userId, int slot, int species) {
        PokecenterDig.Game game = dig.game(userId);
        if (game == null || !consoles.running().contains(userId)) throw new IllegalStateException("Wonder Trade needs your Gold, Silver or Crystal running");
        if (consoles.linkedWith(userId) != null) throw new IllegalStateException("Unplug the link cable first");
        if (!tracker.cableClubOpen(userId)) throw new IllegalStateException("Wonder Trade opens with your Cable Club: bring Mr. Pokémon's Mystery Egg back to Prof. Elm first");
        String today = today();
        if (today.equals(repo.day(userId))) throw new IllegalStateException("One Wonder Trade a day: come back tomorrow!");
        PokemonGen2 rom = game.maps();
        WonderMon incoming = repo.pool().stream().filter(m -> !m.user().equals(userId)).findFirst().orElse(null);
        String[] key = {null};
        Outcome out = consoles.withBus(userId, bus -> {
            String busy = PokecenterDig.busy(game, bus);
            if (busy != null) return new Outcome("You're " + busy + ": try again when you can walk around", null, null);
            GiftMon.Raw gave = GiftMon.read(bus, rom, slot);
            if (gave == null || (gave.mon()[0] & 0xFF) != species) return new Outcome("Your party changed: pick again", null, null);
            if (GiftMon.egg(bus, rom, slot)) return new Outcome("Eggs can't be Wonder Traded", null, null);
            if (!anotherFighter(bus, rom, slot)) return new Outcome("Keep at least one other Pokémon that can fight", null, null);
            String held = rom.itemName(gave.mon()[1] & 0xFF);
            if (held != null && held.toLowerCase().contains("mail")) return new Outcome("Take the Mail off first", null, null);
            if (!TradeSave.saved(bus, rom)) return new Outcome("Save your game once first", null, null);
            key[0] = key(gave);
            if (repo.given(userId, key[0])) return new Outcome("This Pokémon has been Wonder Traded before", null, null);
            GiftMon.remove(bus, rom, slot);
            if (incoming == null) {
                TradeSave.save(bus, rom); // as a link trade saves: Reset cannot bring it back
                return new Outcome(null, gave, null);
            }
            GiftMon.Raw got = GiftMon.arrive(rom, raw(incoming));
            if (!GiftMon.put(bus, rom, got)) { // could not join: the one sent goes back
                GiftMon.put(bus, rom, gave);
                return new Outcome("The trade didn't go through: try again", null, null);
            }
            TradeSave.save(bus, rom);
            return new Outcome(null, gave, got);
        });
        if (out == null) throw new IllegalStateException("Wonder Trade needs your Gold, Silver or Crystal running");
        if (out.error() != null) throw new IllegalStateException(out.error());
        consoles.batterySave(userId); // the .sav on disk too, at once
        repo.traded(userId, today);
        repo.give(userId, key[0]);
        tracker.unlockNow(userId, "wonder-sent");
        WonderMon mine = entry(userId, rom, out.gave());
        Mon gave = mon(rom, out.gave(), null);
        if (incoming == null) {
            repo.add(mine);
            history.add(userId, new dev.pppp.model.LinkEvent("WONDER", System.currentTimeMillis(), null, null, null, null, link(gave), null));
            poolChanged();
            log.info("Wonder Trade: {} put {} in the pool", userId, gave.name());
            return new Traded(gave, null, null);
        }
        if (!repo.take(incoming)) log.warn("Wonder Trade: {} was gone from the pool already", incoming.id());
        repo.deliver(incoming.user(), mine);
        poolChanged();
        Mon got = mon(rom, out.got(), incoming.species() != (out.got().mon()[0] & 0xFF) ? incoming.species() : null);
        tracker.unlockNow(userId, "wonder-received");
        history.add(userId, new dev.pppp.model.LinkEvent("WONDER", System.currentTimeMillis(), incoming.user(), null, null, null, link(gave), link(got)));
        events.publishEvent(new LiveEvent("WONDER_TRADE", Map.of("user", userId, "gave", gave, "got", got, "from", incoming.user())));
        log.info("Wonder Trade: {} sent {}, got {}'s {}", userId, gave.name(), incoming.user(), got.name());
        return new Traded(gave, got, incoming.user());
    }

    /** Every 10 s: a Pokémon traded for one of theirs joins each running, free console's party once it has room. */
    @Scheduled(fixedDelay = 10_000)
    public void deliver() {
        for (String id : consoles.running()) {
            try {
                WonderMon next = repo.nextIncoming(id);
                if (next == null || !consoles.isAwake(id) || consoles.linkedWith(id) != null) continue;
                PokecenterDig.Game game = dig.game(id);
                if (game == null) continue;
                PokemonGen2 rom = game.maps();
                GiftMon.Raw arrived = GiftMon.arrive(rom, raw(next));
                Boolean done = consoles.withBus(id, bus -> {
                    if (PokecenterDig.busy(game, bus) != null || !GiftMon.put(bus, rom, arrived)) return false;
                    TradeSave.save(bus, rom);
                    return true;
                });
                if (!Boolean.TRUE.equals(done)) continue;
                consoles.batterySave(id);
                repo.delivered(id);
                tracker.unlockNow(id, "wonder-received");
                Mon got = mon(rom, arrived, next.species() != (arrived.mon()[0] & 0xFF) ? next.species() : null);
                history.add(id, new dev.pppp.model.LinkEvent("WONDER", System.currentTimeMillis(), next.user(), null, null, null, null, link(got)));
                events.publishEvent(new LiveEvent("WONDER_ARRIVED", Map.of("user", id, "got", got, "from", next.user())));
                log.info("Wonder Trade: {}'s {} reached {}", next.user(), got.name(), id);
            } catch (RuntimeException e) {
                log.warn("Wonder Trade delivery failed for {}", id, e);
            }
        }
    }

    private static dev.pppp.model.LinkEvent.Mon link(Mon m) {
        return new dev.pppp.model.LinkEvent.Mon(m.species(), m.level(), m.shiny());
    }

    private void poolChanged() {
        events.publishEvent(new LiveEvent("WONDER_POOL", Map.of("users", repo.pool().stream().map(WonderMon::user).toList())));
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Another party member, not an egg, with HP left. */
    private static boolean anotherFighter(dev.pppp.emulator.Bus bus, PokemonGen2 rom, int slot) {
        int count = bus.getByte(rom.partyCount());
        for (int i = 0; i < count; i++) {
            if (i == slot || GiftMon.egg(bus, rom, i)) continue;
            GiftMon.Raw r = GiftMon.read(bus, rom, i);
            if (r != null && ((r.mon()[34] & 0xFF) << 8 | r.mon()[35] & 0xFF) > 0) return true;
        }
        return false;
    }

    /** "{OT ID}:{DVs}:{species}" in hex: the same Pokémon, wherever it is. */
    static String key(GiftMon.Raw raw) {
        byte[] m = raw.mon();
        return HexFormat.of().formatHex(new byte[] {m[6], m[7]}) + ":" + HexFormat.of().formatHex(new byte[] {m[21], m[22]})
                + ":" + HexFormat.of().toHexDigits(m[0]);
    }

    private static WonderMon entry(String userId, PokemonGen2 rom, GiftMon.Raw raw) {
        Mon m = mon(rom, raw, null);
        Base64.Encoder b64 = Base64.getEncoder();
        return new WonderMon(UUID.randomUUID().toString(), userId, m.species(), m.name(), m.nickname(), m.ot(), m.level(),
                m.shiny(), b64.encodeToString(raw.mon()), b64.encodeToString(raw.ot()), b64.encodeToString(raw.nick()),
                System.currentTimeMillis());
    }

    private static GiftMon.Raw raw(WonderMon m) {
        Base64.Decoder b64 = Base64.getDecoder();
        return new GiftMon.Raw(b64.decode(m.mon()), b64.decode(m.otName()), b64.decode(m.nick()));
    }

    static Mon mon(PokemonGen2 rom, GiftMon.Raw raw, Integer evolvedFrom) {
        byte[] m = raw.mon();
        int species = m[0] & 0xFF;
        String name = rom.speciesName(species), nick = PokemonRed.text(raw.nick(), 0, raw.nick().length);
        return new Mon(species, name, nick.isBlank() || nick.equalsIgnoreCase(name) ? null : nick,
                PokemonRed.text(raw.ot(), 0, raw.ot().length), m[31] & 0xFF, PokemonGen2.shiny(m[21] & 0xFF, m[22] & 0xFF), evolvedFrom);
    }
}
