package dev.pppp.security;

import dev.pppp.config.PpppProperties;
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
 * when listed in pppp.admins). Exists only when pppp.dev-login=true; never set that on a reachable host.
 */
@RestController
@ConditionalOnProperty(name = "pppp.dev-login", havingValue = "true")
class DevLoginController {
    private final PpppProperties props;

    DevLoginController(PpppProperties props) {
        this.props = props;
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
