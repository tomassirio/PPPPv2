package dev.pppp.service.game;

import dev.pppp.model.Bloodstain;
import dev.pppp.repository.BloodstainRepository;
import dev.pppp.service.PlayerConsoles;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Bloodstains, as Dark Souls': where a Gold, Silver or Crystal player lost a battle, a red mark on every screen that
 * has the tile in view; standing on it tells who lost there and to what. Twice a second each running console's
 * wBattleMode is read: when a battle starts the tile the player stood on is kept (Ghosts.lastStood), and while it runs
 * the foe (the trainer and their Pokémon out, or the wild one); when it ends with wBattleResult LOSE (pret
 * engine/battle/core.asm LostBattle; ClearBattleRAM zeroes it when a battle starts) the stain is left. Link battles
 * leave none. A stain lasts KEEP; a player has MAX_PER_PLAYER at most, the oldest go first. Each keeps the last
 * seconds before the loss as a replay (Replays), deleted with it.
 */
@Service
public class Bloodstains {
    private static final Logger log = LoggerFactory.getLogger(Bloodstains.class);

    static final Duration KEEP = Duration.ofHours(48);
    static final int MAX_PER_PLAYER = 5;
    private static final int LOSE = 1, RESULT = 0x3F, CLASS_NAME = 13, STEP = 16, SCREEN_W = 160, SCREEN_H = 144;

    /**
     * WRAM (pret pokegold.sym / pokecrystal11.sym): wBattleMode, wOtherTrainerClass (0: wild), wOtherTrainerID,
     * wBattleResult, wEnemyMonSpecies, wEnemyMonLevel, wOTClassName (GetOTName: the class, or the rival's name),
     * wLinkMode.
     */
    record Layout(int battleMode, int trainerClass, int trainerId, int battleResult, int enemySpecies, int enemyLevel,
                  int otClassName, int linkMode) {}

    static final Layout GOLD_SILVER = new Layout(0xD116, 0xD118, 0xD11B, 0xCFE9, 0xD0EF, 0xD0FC, 0xCB34, 0xD042),
            CRYSTAL = new Layout(0xD22D, 0xD22F, 0xD231, 0xD0EE, 0xD206, 0xD213, 0xC656, 0xC2DC);

    /** A stain on the player's screen: its tile's top left in screen pixels; here: they stand on it; replayMs: its replay's length (0: none). */
    public record Shown(String id, String user, int x, int y, String foe, long at, boolean mine, boolean here,
                        int replayMs) {}

    /** A battle running: where it began, and the latest foe seen. Poll thread only. */
    private static final class Fight {
        final Ghosts.Where where;
        String foe;

        Fight(Ghosts.Where where) {
            this.where = where;
        }
    }

    private final PlayerConsoles consoles;
    private final PokecenterDig dig;
    private final Ghosts ghosts;
    private final BloodstainRepository repo;
    private final Replays replays;
    private final Map<String, Fight> fights = new ConcurrentHashMap<>();
    private final List<Bloodstain> stains = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;

    public Bloodstains(PlayerConsoles consoles, PokecenterDig dig, Ghosts ghosts, BloodstainRepository repo,
                       Replays replays) {
        this.replays = replays;
        this.consoles = consoles;
        this.dig = dig;
        this.ghosts = ghosts;
        this.repo = repo;
    }

    @Scheduled(fixedDelay = 500)
    public void watch() {
        Set<String> running = consoles.running();
        fights.keySet().retainAll(running);
        replays.retain(running);
        for (String id : running) {
            try {
                PokecenterDig.Game game = dig.game(id);
                if (game != null) watch(id, game.maps());
            } catch (RuntimeException e) {
                log.warn("Bloodstain watch failed for {}", id, e);
            }
        }
    }

    private void watch(String id, PokemonGen2 game) {
        Layout at = game.crystal() ? CRYSTAL : GOLD_SILVER;
        byte[] mode = consoles.readBytes(id, at.battleMode, 1);
        if (mode == null) return; // another WRAM bank is in: next time
        Fight fight = fights.get(id);
        if (mode[0] != 0) {
            byte[] link = consoles.readBytes(id, at.linkMode, 1);
            if (link == null) return;
            if (link[0] != 0) { // a link battle: nothing to leave
                fights.remove(id);
                return;
            }
            if (fight == null) {
                Ghosts.Where w = ghosts.lastStood(id);
                if (w == null) return;
                fights.put(id, fight = new Fight(w));
            }
            String foe = foe(id, game, at);
            if (foe != null) fight.foe = foe;
            return;
        }
        if (fight == null) return;
        byte[] result = consoles.readBytes(id, at.battleResult, 1);
        if (result == null) return; // read it next time
        fights.remove(id);
        if ((result[0] & RESULT) == LOSE && fight.foe != null) leave(id, fight.where, fight.foe);
    }

    /** Who the player is fighting: "Leader Whitney's Miltank Lv 20", "Silver's Croconaw Lv 18", "a wild Gastly Lv 17". */
    private String foe(String id, PokemonGen2 game, Layout at) {
        byte[] trainer = consoles.readBytes(id, at.trainerClass, at.trainerId - at.trainerClass + 1),
                mon = consoles.readBytes(id, at.enemySpecies, at.enemyLevel - at.enemySpecies + 1);
        if (trainer == null || mon == null) return null;
        int species = mon[0] & 0xFF, level = mon[mon.length - 1] & 0xFF, trainerClass = trainer[0] & 0xFF;
        if (species < 1 || species > 251 || level < 1 || level > 100) return null;
        String pokemon = game.speciesName(species) + " Lv " + level;
        if (trainerClass == 0) return "a wild " + pokemon;
        byte[] cls = consoles.readBytes(id, at.otClassName, CLASS_NAME);
        if (cls == null) return null;
        String className = PokemonRed.titleCase(PokemonRed.text(cls, 0, CLASS_NAME)),
                name = game.trainerName(trainerClass, trainer[trainer.length - 1] & 0xFF);
        String who = name == null ? className : className.isBlank() ? name : className + " " + name;
        return who.isBlank() ? "a trainer's " + pokemon : who + "'s " + pokemon;
    }

    /** Leaves a stain on the tile the player stood on (the nearest one mid-step), dropping their oldest past MAX_PER_PLAYER. */
    void leave(String userId, Ghosts.Where where, String foe) {
        load();
        List<Bloodstain> mine = new ArrayList<>(stains.stream().filter(s -> s.user().equals(userId)).toList());
        mine.sort(Comparator.comparingLong(Bloodstain::at));
        while (mine.size() >= MAX_PER_PLAYER) remove(mine.removeFirst().id());
        Bloodstain stain = new Bloodstain(UUID.randomUUID().toString(), userId, where.map(),
                Math.floorDiv(where.x() + STEP / 2, STEP), Math.floorDiv(where.y() + STEP / 2, STEP), foe, System.currentTimeMillis());
        repo.save(stain);
        stains.add(stain);
        replays.save(userId, stain.id());
        log.info("Bloodstain: {} lost to {} on {}", userId, foe, where.map());
    }

    /** The stains in view on the player's screen this frame (none in a menu, a battle, a link room). */
    public List<Shown> visible(String userId) {
        Ghosts.Where w = ghosts.where(userId);
        if (w == null) return List.of();
        load();
        long since = System.currentTimeMillis() - KEEP.toMillis();
        List<Shown> out = new ArrayList<>();
        for (Bloodstain s : stains) {
            if (s.at() < since) {
                remove(s.id());
                continue;
            }
            if (!s.map().equals(w.map())) continue;
            int sx = Ghosts.SCREEN_X + s.x() * STEP - w.x(), sy = Ghosts.SCREEN_Y + s.y() * STEP - w.y();
            if (sx <= -STEP || sx >= SCREEN_W || sy <= -STEP || sy >= SCREEN_H) continue;
            out.add(new Shown(s.id(), s.user(), sx, sy, s.foe(), s.at(), s.user().equals(userId),
                    s.x() * STEP == w.x() && s.y() * STEP == w.y(), replays.millis(s.id())));
        }
        return out;
    }

    private void remove(String id) {
        repo.delete(id);
        replays.delete(id);
        stains.removeIf(s -> s.id().equals(id));
    }

    private synchronized void load() {
        if (loaded) return;
        stains.addAll(repo.all());
        loaded = true;
    }
}
