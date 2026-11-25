package dev.pppp.service.game;

import dev.pppp.emulator.Bus;
import dev.pppp.exception.NotFoundException;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.GiftRepository;
import dev.pppp.repository.PlayerRepository;
import dev.pppp.repository.ProgressRepository;
import dev.pppp.service.ChatService;
import dev.pppp.service.PlayerConsoles;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Mystery Gifts: an admin sends one of CATALOG (fair items, each with a quantity cap) to a player or to everyone. It
 * waits in Redis (GiftRepository) until that player's Gold, Silver or Crystal console runs with them free in the
 * overworld, then lands in the PC's item storage (wNumPCItems: added to a stack of the same item, up to 99, or a new
 * entry while there is room) or, for an event Pokémon (EVENTS), the party once it has room; MYSTERY_GIFT {user, name,
 * quantity, pokemon} tells their browser. Both live in WRAM: the player saves the game to keep them.
 */
@Service
public class MysteryGifts {
    private static final Logger log = LoggerFactory.getLogger(MysteryGifts.class);

    /** id, the item as the games name it (or the event Pokémon), the most an admin may send at once, pokemon: an event Pokémon. */
    public record Gift(String id, String name, int max, boolean pokemon) {
        Gift(String id, String name, int max) {
            this(id, name, max, false);
        }
    }

    /**
     * Event Pokémon, as Gen 2's distributions gave them (fixed movesets), one at a time, into the party (GiftMon): the
     * GS Ball's Celebi, Crystal's Odd Egg (a baby that knows Dizzy Punch, sometimes shiny), the event Mew and Pokémon
     * Stadium's Surfing Pikachu. By gift id; the Odd Egg rolls its baby when it is delivered.
     */
    static final Map<String, java.util.function.Function<java.util.Random, GiftMon.Spec>> EVENTS = Map.of(
            "celebi", r -> new GiftMon.Spec("Celebi", 30, List.of("Heal Bell", "Safeguard", "AncientPower", "Future Sight"), false, false),
            "odd-egg", GiftMon::oddEgg,
            "mew", r -> new GiftMon.Spec("Mew", 5, List.of("Pound"), false, false),
            "surfing-pikachu", r -> new GiftMon.Spec("Pikachu", 5, List.of("ThunderShock", "Growl", "Surf"), false, false));

    public static final List<Gift> CATALOG = List.of(
            new Gift("rare-candy", "Rare Candy", 3),
            new Gift("fire-stone", "Fire Stone", 1),
            new Gift("water-stone", "Water Stone", 1),
            new Gift("thunderstone", "Thunderstone", 1),
            new Gift("leaf-stone", "Leaf Stone", 1),
            new Gift("moon-stone", "Moon Stone", 1),
            new Gift("sun-stone", "Sun Stone", 1),
            new Gift("everstone", "Everstone", 1),
            new Gift("pp-up", "PP Up", 3),
            new Gift("full-restore", "Full Restore", 3),
            new Gift("max-revive", "Max Revive", 3),
            new Gift("max-repel", "Max Repel", 10),
            new Gift("escape-rope", "Escape Rope", 5),
            new Gift("ultra-ball", "Ultra Ball", 10),
            new Gift("great-ball", "Great Ball", 10),
            new Gift("gold-berry", "Gold Berry", 5),
            new Gift("moomoo-milk", "Moomoo Milk", 5),
            new Gift("nugget", "Nugget", 3),
            new Gift("amulet-coin", "Amulet Coin", 1),
            new Gift("celebi", "Celebi (GS Ball, Lv 30)", 1, true),
            new Gift("odd-egg", "Odd Egg", 1, true),
            new Gift("mew", "Mew (Lv 5)", 1, true),
            new Gift("surfing-pikachu", "Surfing Pikachu (Lv 5)", 1, true));
    private final java.util.Random random = new java.util.Random();

    private static final int MAX_PC_ITEMS = 50, MAX_STACK = 99, END = 0xFF;

    private final GiftRepository gifts;
    private final PlayerConsoles consoles;
    private final PlayerRepository players;
    private final ProgressRepository progress;
    private final PokecenterDig dig;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public MysteryGifts(GiftRepository gifts, PlayerConsoles consoles, PlayerRepository players, ProgressRepository progress,
                        PokecenterDig dig, ChatService chat, ApplicationEventPublisher events) {
        this.gifts = gifts;
        this.consoles = consoles;
        this.players = players;
        this.progress = progress;
        this.dig = dig;
        this.chat = chat;
        this.events = events;
    }

    /**
     * Queues the gift for one player (to) or, with to null, everyone who has picked a game. IllegalArgumentException
     * (400) for a quantity outside 1..max; NotFoundException for an unknown gift or player. Returns how many got it.
     */
    public int send(String adminId, String giftId, int quantity, String to) {
        Gift gift = CATALOG.stream().filter(g -> g.id().equals(giftId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No such gift"));
        if (quantity < 1 || quantity > gift.max()) {
            throw new IllegalArgumentException("Send 1 to " + gift.max() + " " + gift.name());
        }
        List<String> who = to == null ? progress.ranking().stream().filter(id -> players.game(id) != null).toList() : List.of(to);
        if (to != null && players.game(to) == null) throw new NotFoundException(to + " has not picked a game");
        who.forEach(id -> gifts.add(id, gift.id(), quantity));
        String what = quantity + "x " + gift.name();
        chat.postAdmin(adminId + " sent a Mystery Gift (" + what + ") to " + (to == null ? "everyone" : to));
        if (to == null) chat.postSystem("A Mystery Gift is on its way to every Pib: " + what + "!");
        return who.size();
    }

    /** Every 10 s: each running console whose player has a gift waiting and stands free gets the oldest one. */
    @Scheduled(fixedDelay = 10_000)
    public void deliver() {
        for (String id : consoles.running()) {
            String next = gifts.peek(id);
            if (next == null || !consoles.isAwake(id) || consoles.linkedWith(id) != null) continue;
            PokecenterDig.Game game = dig.game(id);
            if (game == null) continue;
            String[] parts = next.split(":");
            Gift gift = CATALOG.stream().filter(g -> g.id().equals(parts[0])).findFirst().orElse(null);
            int quantity = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (gift == null) { // retired from the catalog: dropped
                gifts.pop(id);
                continue;
            }
            Boolean done;
            if (gift.pokemon()) { // into the party once it has room
                GiftMon.Spec spec = EVENTS.get(gift.id()).apply(random);
                done = consoles.withBus(id, bus -> PokecenterDig.busy(game, bus) == null && GiftMon.add(bus, game.maps(), spec, random));
            } else {
                int item = game.maps().itemId(gift.name());
                if (item == 0) { // not in this game: dropped
                    gifts.pop(id);
                    continue;
                }
                done = consoles.withBus(id, bus -> PokecenterDig.busy(game, bus) == null && put(bus, game.maps().pcItems(), item, quantity));
            }
            if (Boolean.TRUE.equals(done)) {
                gifts.delivered(id);
                events.publishEvent(new LiveEvent("MYSTERY_GIFT", Map.of("user", id, "name", gift.name(), "quantity", quantity, "pokemon", gift.pokemon())));
                log.info("Mystery Gift {}x {} delivered to {}", quantity, gift.name(), id);
            }
        }
    }

    /**
     * Adds quantity of item to the PC's list at pcItems: onto its stack (up to 99), else as a new entry before the -1.
     * False (nothing written) when the list is full or does not look like one (the next try may do).
     */
    static boolean put(Bus bus, int pcItems, int item, int quantity) {
        int count = bus.getByte(pcItems);
        if (count > MAX_PC_ITEMS || bus.getByte(pcItems + 1 + count * 2) != END) return false;
        for (int i = 0; i < count; i++) {
            int at = pcItems + 1 + i * 2;
            if (bus.getByte(at) == item && bus.getByte(at + 1) + quantity <= MAX_STACK) {
                bus.setByte(at + 1, bus.getByte(at + 1) + quantity);
                return true;
            }
        }
        if (count == MAX_PC_ITEMS) return false;
        int at = pcItems + 1 + count * 2;
        bus.setByte(at, item);
        bus.setByte(at + 1, quantity);
        bus.setByte(at + 2, END);
        bus.setByte(pcItems, count + 1);
        return true;
    }
}
