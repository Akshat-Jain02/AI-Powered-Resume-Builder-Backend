package com.resumeai.auth.auth2;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.service.UserAuthService;
import com.resumeai.auth.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtService;
    private final UserAuthService userService;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attrs = oauthUser.getAttributes();

        log.info("OAuth2 login success. Provider attributes keys: {}", attrs.keySet());

        // ── Extract identifier (email) from provider ────────────────────────
        String email = (String) attrs.get("email");

        if (email == null || email.isBlank()) {
            // Fallback 1: login (common for GitHub/GitLab)
            if (attrs.containsKey("login")) {
                email = attrs.get("login") + "@github.oauth";
                log.info("Using provider login fallback: {}", email);
            } 
            // Fallback 2: principal name (sub/id)
            else {
                String name = oauthUser.getName();
                if (name != null && !name.isBlank()) {
                    email = name + "@oauth";
                    log.info("Using principal name fallback: {}", email);
                }
            }
        }

        if (email == null || email.isBlank()) {
            log.error("OAuth2 user has no resolvable identifier. Attributes: {}", attrs);
            response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
            return;
        }

        // ── Find or create local user ────────────────────────────────────────
        try {
            UserAuthEntity user = userService.findOrCreateUser(email);
            log.info("OAuth2 user resolved: username={}", user.getUsername());

            if (user.isBanned()) {
                log.warn("Banned user '{}' tried to login via OAuth2", user.getUsername());
                response.sendRedirect(frontendUrl + "/login?error=banned");
                return;
            }

            List<String> roles = (user.getRoles() != null && !user.getRoles().isEmpty())
                    ? user.getRoles()
                    : List.of("USER");

            // ── Generate JWT ─────────────────────────────────────────────────────
            String token = jwtService.generateToken(user.getUsername(), user.getEmail(), roles);

            // ── Redirect to frontend with token in URL params ────────────────────
            String redirectUrl = frontendUrl + "/login"
                    + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&username=" + URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8);

            log.info("Redirecting OAuth2 user to: {}", frontendUrl + "/login?token=***");
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("Error finding or creating user for OAuth2 identifier {}: {}", email, e.getMessage());
            response.sendRedirect(frontendUrl + "/login?error=oauth_error");
        }
    }
}
