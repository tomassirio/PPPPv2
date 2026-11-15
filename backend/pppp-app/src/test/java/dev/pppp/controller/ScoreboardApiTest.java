package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.emulator.TestRom;
import dev.pppp.model.PartyMon;
import dev.pppp.model.Progress;
import dev.pppp.service.PlayerConsoles;
import dev.pppp.service.game.Gen2Save;
import dev.pppp.service.game.ProgressTracker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // one Redis: the scoreboard test counts every player
class ScoreboardApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("ppp-score-test");
            TestRom.write(Files.createDirectories(DATA.resolve("roms"))); // cartridge "test": no game profile
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired PlayerConsoles consoles;
    @Autowired ProgressTracker tracker;
    @Autowired StringRedisTemplate redis;
    @Autowired dev.pppp.repository.ProgressRepository progressRepo;

    /** A Gold/Silver/Crystal save with these Johto badge bits, the starter received and nothing else. */
    static Gen2Save save(int johtoBadges) {
        return save(johtoBadges, null, List.of());
    }

    /** The same, at 12:05 play time, standing at location with this party. */
    static Gen2Save save(int johtoBadges, String location, List<PartyMon> party) {
        return new Gen2Save(1, johtoBadges, 0, 0, false, true, false, false, new BitSet(), new BitSet(), party.size(),
                List.of(), Set.of(), 0, 0, 0, 0, 12, 5, location, party);
    }

    @Test
    @Order(1)
    void scoreboardAndActivityFollowRecordedProgress() throws Exception {
        mvc.perform(get("/api/v1/scoreboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/activity")).andExpect(status().isUnauthorized());
        for (String id : new String[] {"pib@x.y", "pibette@x.y"}) {
            RequestPostProcessor who = user(id).roles("PLAYER");
            mvc.perform(get("/api/v1/scoreboard").with(who)).andExpect(jsonPath("$[?(@.userId == '" + id + "')]").isEmpty());
            mvc.perform(put("/api/v1/me/game").with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cartridgeId\":\"test\"}")).andExpect(status().isOk());
        }
        RequestPostProcessor pib = user("pib@x.y").roles("PLAYER");
        mvc.perform(get("/api/v1/scoreboard").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)) // picking a game is enough to be listed
                .andExpect(jsonPath("$[0].points").value(0))
                .andExpect(jsonPath("$[0].online").value(false));
        mvc.perform(get("/api/v1/activity").with(pib)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        consoles.open("pibette@x.y"); // the test ROM has no profile, so the job itself scores nothing
        tracker.record("pibette@x.y", new Progress(Set.of("Boulder"), Set.of("Pidgey"), Set.of("Pidgey", "Rattata"), 12, false));
        tracker.record("pibette@x.y", new Progress(Set.of("Boulder"), Set.of("Pidgey"), Set.of("Pidgey"), 12, false)); // again: nothing
        // A Gen 2 save: Zephyr (10 story) ranks pib above pibette's points, which no longer rank anyone.
        tracker.record("pib@x.y", new Progress(Set.of(), Set.of(), Set.of("Pidgey"), 5, false), save(0b1));

        mvc.perform(get("/api/v1/scoreboard").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("pib@x.y"))
                .andExpect(jsonPath("$[0].points").value(2))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].trainerScore").value(10))
                .andExpect(jsonPath("$[0].title").value("Youngster"))
                .andExpect(jsonPath("$[0].grade").value("F"))
                .andExpect(jsonPath("$[0].achievementPoints").value(1 + 3))
                .andExpect(jsonPath("$[0].achievementsUnlocked").value(2))
                .andExpect(jsonPath("$[1].userId").value("pibette@x.y"))
                .andExpect(jsonPath("$[1].points").value(100 + 10 + 2 + 2 + 5))
                .andExpect(jsonPath("$[1].badges").value(1))
                .andExpect(jsonPath("$[1].caught").value(1))
                .andExpect(jsonPath("$[1].seen").value(2))
                .andExpect(jsonPath("$[1].game").value("test"))
                .andExpect(jsonPath("$[1].online").value(true))
                .andExpect(jsonPath("$[1].trainerScore").value(0))
                .andExpect(jsonPath("$[1].achievementsUnlocked").value(0));
        mvc.perform(get("/api/v1/activity?limit=3").with(pib)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].user").value("pib@x.y"))
                .andExpect(jsonPath("$[0].type").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$[0].detail").value("Zephyr Badge"))
                .andExpect(jsonPath("$[0].points").value(3))
                .andExpect(jsonPath("$[0].at").isNumber())
                .andExpect(jsonPath("$[1].detail").value("New Bark Beginnings"))
                .andExpect(jsonPath("$[2].type").value("SEEN"))
                .andExpect(jsonPath("$[2].detail").value("Pidgey"))
                .andExpect(jsonPath("$[2].points").value(2));
        mvc.perform(get("/api/v1/activity?limit=0").with(pib)).andExpect(status().isBadRequest());

        assertThat(redis.opsForStream().size("pibs:events")).isEqualTo(5 + 1 + 2);
        assertThat(redis.opsForZSet().score("pibs:scoreboard", "pibette@x.y")).isEqualTo(0);
        assertThat(redis.opsForZSet().score("pibs:scoreboard", "pib@x.y")).isEqualTo(10 * 10_000 + 4);
        assertThat(redis.opsForHash().keys("pibs:achievements:pib@x.y"))
                .containsExactlyInAnyOrder("new-bark-beginnings", "zephyr-badge");
        assertThat(redis.opsForHash().entries("pibs:trainer:pib@x.y")).containsEntry("score", "10")
                .containsEntry("title", "Youngster").containsEntry("grade", "F").containsEntry("story", "10");
        assertThat(redis.opsForHash().entries("pibs:progress:pibette@x.y")).containsEntry("badges", "Boulder")
                .containsEntry("level", "12").containsEntry("hallOfFame", "false");
        // Set.of iterates in a different order per JVM, so the stored order is not fixed.
        assertThat(((String) redis.opsForHash().get("pibs:progress:pibette@x.y", "seen")).split("\\|"))
                .containsExactlyInAnyOrder("Pidgey", "Rattata");
    }

    @Test
    @Order(2)
    void achievementsOfTheCallerAndOfOtherPlayers() throws Exception {
        mvc.perform(get("/api/v1/achievements")).andExpect(status().isUnauthorized());
        RequestPostProcessor ash = user("Ash@X.y").roles("PLAYER");
        mvc.perform(get("/api/v1/achievements").with(ash)).andExpect(status().isOk()) // nothing yet: all locked
                .andExpect(jsonPath("$.userId").value("ash@x.y"))
                .andExpect(jsonPath("$.trainer.score").value(0))
                .andExpect(jsonPath("$.trainer.title").value("Youngster"))
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.achievements.length()").value(55))
                .andExpect(jsonPath("$.achievements[0].id").value("new-bark-beginnings"))
                .andExpect(jsonPath("$.achievements[0].unlockedAt").doesNotExist());
        mvc.perform(get("/api/v1/players/ash@x.y/achievements").with(ash)).andExpect(status().isNotFound());

        tracker.record("ash@x.y", Progress.EMPTY, save(0b11));
        mvc.perform(get("/api/v1/achievements").with(ash)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trainer.score").value(20))
                .andExpect(jsonPath("$.trainer.story").value(20))
                .andExpect(jsonPath("$.points").value(1 + 3 + 3))
                .andExpect(jsonPath("$.unlocked").value(3))
                .andExpect(jsonPath("$.achievements[0].unlockedAt").isNumber())
                .andExpect(jsonPath("$.achievements[0].category").value("Story"))
                .andExpect(jsonPath("$.achievements[0].points").value(1))
                .andExpect(jsonPath("$.achievements[0].implemented").value(true))
                .andExpect(jsonPath("$.achievements[?(@.id == 'plain-badge')].unlockedAt").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[0].rarity").value("common"))
                .andExpect(jsonPath("$.achievements[0].hidden").value(false))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].hidden").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].name").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].note").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].rarity").value(org.hamcrest.Matchers.contains("legendary")));
        mvc.perform(get("/api/v1/players/Ash@x.y/achievements").with(user("misty@x.y").roles("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ash@x.y"))
                .andExpect(jsonPath("$.unlocked").value(3));
    }

    @Test
    @Order(3)
    void aPibsProfile() throws Exception {
        RequestPostProcessor misty = user("misty@x.y").roles("PLAYER");
        mvc.perform(get("/api/v1/players/brock@x.y/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/players/brock@x.y/profile").with(misty)).andExpect(status().isNotFound());

        var quagsire = new PartyMon(195, "Quagsire", "MUDDY", 34, 30, 91, "PSN", "Leftovers",
                List.of("Surf", "Earthquake"), true, false, List.of("water", "ground"));
        var egg = new PartyMon(175, "Egg", null, 5, 12, 12, null, null, List.of(), false, true, List.of());
        tracker.record("brock@x.y", new Progress(Set.of(), Set.of("Onix"), Set.of("Onix", "Geodude"), 14, false),
                save(0b1, "Ecruteak City", List.of(quagsire, egg)));

        mvc.perform(get("/api/v1/players/Brock@x.y/profile").with(misty)).andExpect(status().isOk())
                .andExpect(jsonPath("$.row.userId").value("brock@x.y"))
                .andExpect(jsonPath("$.row.location").value("Ecruteak City"))
                .andExpect(jsonPath("$.row.playTime").value("12:05"))
                .andExpect(jsonPath("$.row.johtoBadges").value(1))
                .andExpect(jsonPath("$.row.kantoBadges").value(0))
                .andExpect(jsonPath("$.row.party.length()").value(2))
                .andExpect(jsonPath("$.row.party[0].species").value(195))
                .andExpect(jsonPath("$.row.party[0].hpPercent").value(33))
                .andExpect(jsonPath("$.row.party[0].status").value("PSN"))
                .andExpect(jsonPath("$.row.party[0].shiny").value(true))
                .andExpect(jsonPath("$.row.party[1].egg").value(true))
                .andExpect(jsonPath("$.party[0].name").value("Quagsire"))
                .andExpect(jsonPath("$.party[0].nickname").value("MUDDY"))
                .andExpect(jsonPath("$.party[0].hp").value(30))
                .andExpect(jsonPath("$.party[0].maxHp").value(91))
                .andExpect(jsonPath("$.party[0].item").value("Leftovers"))
                .andExpect(jsonPath("$.party[0].moves[1]").value("Earthquake"))
                .andExpect(jsonPath("$.party[0].types[1]").value("ground"))
                .andExpect(jsonPath("$.party[1].nickname").doesNotExist())
                .andExpect(jsonPath("$.achievements.length()").value(55))
                .andExpect(jsonPath("$.achievements[0].id").value("new-bark-beginnings"))
                .andExpect(jsonPath("$.achievements[0].unlocked").value(true))
                .andExpect(jsonPath("$.achievements[0].progress").doesNotExist())
                .andExpect(jsonPath("$.achievements[?(@.id == 'field-researcher')].progress").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'field-researcher')].goal").value(org.hamcrest.Matchers.contains(50)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'field-researcher')].unlocked").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.stats.caught").value(1))
                .andExpect(jsonPath("$.stats.seen").value(2))
                .andExpect(jsonPath("$.stats.playTime").value("12:05"))
                .andExpect(jsonPath("$.stats.trades").value(0))
                .andExpect(jsonPath("$.stats.linkWins").value(0))
                .andExpect(jsonPath("$.stats.linkLosses").value(0))
                .andExpect(jsonPath("$.recent.length()").value(6)) // Onix caught, 2 seen, Lv 10, 2 achievements
                .andExpect(jsonPath("$.recent[0].user").value("brock@x.y"))
                .andExpect(jsonPath("$.recent[0].type").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$.recent[?(@.user != 'brock@x.y')]").isEmpty());
        assertThat(redis.opsForHash().keys("pibs:trainer:brock@x.y")).contains("card", "score");
        // A restarted backend reads the card back from Redis.
        assertThat(progressRepo.card("brock@x.y").party()).containsExactly(quagsire, egg);
    }

    @Test
    @Order(4)
    void aLockedLegendaryIsHiddenUntilUnlocked() throws Exception {
        RequestPostProcessor misty = user("misty@x.y").roles("PLAYER");
        // Inside Elm's lab, with the trainer's name: no Red beaten yet.
        tracker.record("gold@x.y", Progress.EMPTY, new Gen2Save(1, 0xFF, 0, 1, false, true, false, false, new BitSet(),
                new BitSet(), 0, List.of(), Set.of(), 0, 0, 0, 0, 40, 0, "New Bark Town · Elm's Lab", List.of(), "GOLD",
                false, "ELMS_LAB", 0, Set.of(), List.of()));
        mvc.perform(get("/api/v1/players/gold@x.y/profile").with(misty)).andExpect(status().isOk())
                .andExpect(jsonPath("$.row.trainerName").value("GOLD"))
                .andExpect(jsonPath("$.row.location").value("New Bark Town · Elm's Lab"))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].hidden").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].name").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].description").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].points").value(org.hamcrest.Matchers.contains(50)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].category").value(org.hamcrest.Matchers.contains("Story")))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].rarity").value(org.hamcrest.Matchers.contains("legendary")))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].unlocked").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'champion-of-johto')].name").value(org.hamcrest.Matchers.contains("Champion of Johto")))
                .andExpect(jsonPath("$.achievements[?(@.hidden == true)].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "the-silent-summit", "250-club", "living-history", "the-time-traveller", "full-house", "low-level")));
        mvc.perform(get("/api/v1/players/gold@x.y/achievements").with(misty))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].name").value(org.hamcrest.Matchers.contains((Object) null)));

        tracker.record("gold@x.y", Progress.EMPTY, new Gen2Save(1, 0xFF, 0, 1, true, true, false, false, new BitSet(),
                new BitSet(), 0, List.of(), Set.of(), 0, 0, 0, 0, 41, 0, "Silver Cave · Room 3", List.of(), "GOLD",
                false, "SILVER_CAVE_ROOM_3", 0, Set.of(), List.of()));
        mvc.perform(get("/api/v1/players/gold@x.y/profile").with(misty)).andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].hidden").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].name").value(org.hamcrest.Matchers.contains("The Silent Summit")))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].unlocked").value(org.hamcrest.Matchers.contains(true)));
        mvc.perform(get("/api/v1/players/gold@x.y/achievements").with(misty))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].name").value(org.hamcrest.Matchers.contains("The Silent Summit")))
                .andExpect(jsonPath("$.achievements[?(@.id == 'the-silent-summit')].note").isNotEmpty());
    }
}
