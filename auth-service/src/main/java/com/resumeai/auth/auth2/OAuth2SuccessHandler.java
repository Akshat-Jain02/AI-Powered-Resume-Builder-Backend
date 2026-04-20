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

        // ── Extract email from provider ──────────────────────────────────────
        // CustomOAuth2UserService ensures that GitHub users have a real email in attributes
        String email = (String) attrs.get("email");

        if (email == null || email.isBlank()) {
            log.error("OAuth2 user has no email attribute after CustomOAuth2UserService processing. Attributes: {}", attrs);
            response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
            return;
        }

        // ── Find or create local user ────────────────────────────────────────
        UserAuthEntity user = userService.findOrCreateUser(email);
        log.info("OAuth2 user resolved: username={}", user.getUsername());

        List<String> roles = (user.getRoles() != null && !user.getRoles().isEmpty())
                ? user.getRoles()
                : List.of("USER");

        // ── Generate JWT ─────────────────────────────────────────────────────
        String token = jwtService.generateToken(user.getUsername(), user.getEmail(), roles);

        // ── Redirect to frontend with token in URL params ────────────────────
        // The Login.jsx page reads ?token= and ?username= on mount and stores them
        String redirectUrl = frontendUrl + "/login"
                + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8);

        log.info("Redirecting OAuth2 user to: {}", frontendUrl + "/login?token=***");
        response.sendRedirect(redirectUrl);
    }
}
