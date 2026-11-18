package dev.pppp.security;

import dev.pppp.config.PpppProperties;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.ScoreEvent;
import dev.pppp.service.game.ProgressTracker;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local development only: GET /dev/login?email=x signs in as x with no password, as a player (and admin
 * when listed in pppp.admins); GET /dev/nudge re-reads a player's scoreboard data from Redis after a hand edit and
 * can post a feed entry, so the live updates (SCOREBOARD_CHANGED, PROGRESS) can be watched. Exists only when
 * pppp.dev-login=true; never set that on a reachable host.
 */
@RestController
@ConditionalOnProperty(name = "pppp.dev-login", havingValue = "true")
class DevLoginController {
    private final PpppProperties props;
    private final ProgressTracker tracker;
    private final ApplicationEventPublisher events;
    private final StringRedisTemplate redis;

    DevLoginController(PpppProperties props, ProgressTracker tracker, ApplicationEventPublisher events,
                       StringRedisTemplate redis) {
        this.props = props;
        this.tracker = tracker;
        this.events = events;
        this.redis = redis;
    }

    /** Drops the player's cached scoreboard data (it is read again from Redis) and, with type, adds a feed entry. */
    @GetMapping("/dev/nudge")
    String nudge(@RequestParam String user, @RequestParam(required = false) String type,
                 @RequestParam(defaultValue = "") String detail, @RequestParam(defaultValue = "0") int points) {
        String id = user.toLowerCase(Locale.ROOT);
        if (type != null) {
            var e = new ScoreEvent(id, type, detail, points, System.currentTimeMillis());
            redis.opsForStream().add("pibs:events", Map.of("user", id, "type", type, "detail", detail,
                    "points", Integer.toString(points), "at", Long.toString(e.at())));
            events.publishEvent(new LiveEvent("PROGRESS", e));
        }
        tracker.forget(id); // publishes SCOREBOARD_CHANGED with the re-read rows
        return "ok";
    }

    /**
     * Broadcasts the scoreboard with the player's grade (and its title) swapped, storing nothing: lets the grade-up
     * animations be watched on a real player's row. Without grade, broadcasts the real scoreboard again.
     */
    @GetMapping("/dev/grade")
    String grade(@RequestParam String user, @RequestParam(required = false) String grade,
                 @RequestParam(required = false) String title) {
        String id = user.toLowerCase(Locale.ROOT);
        var rows = tracker.scoreboard().stream().map(r -> grade == null || !r.userId().equals(id) ? r
                : new dev.pppp.model.ScoreRow(r.userId(), r.points(), r.badges(), r.caught(), r.seen(), r.game(),
                        r.online(), r.trainerScore(), title == null ? r.title() : title, grade, r.achievementPoints(),
                        r.achievementsUnlocked(), r.party(), r.location(), r.playTime(), r.johtoBadges(),
                        r.kantoBadges(), r.trainerName(), r.link())).toList();
        events.publishEvent(new LiveEvent("SCOREBOARD_CHANGED", rows));
        return "ok";
    }

    @GetMapping("/dev/login")
    void login(@RequestParam String email, HttpServletRequest req, HttpServletResponse res) throws IOException {
        String id = email.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9._+-]+@[a-z0-9.-]+")) throw new IllegalArgumentException("Not an e-mail address");
        List<GrantedAuthority> roles = new ArrayList<>(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));
        if (props.admins().stream().anyMatch(id::equalsIgnoreCase)) roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id, "n/a", roles));
        new HttpSessionSecurityContextRepository().saveContext(context, req, res);
        res.sendRedirect("/play");
    }
}
