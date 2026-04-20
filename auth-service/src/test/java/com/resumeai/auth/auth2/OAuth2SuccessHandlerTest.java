package com.resumeai.auth.auth2;

import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.service.UserAuthService;
import com.resumeai.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock private JwtUtil jwtService;
    @Mock private UserAuthService userService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    @InjectMocks private OAuth2SuccessHandler handler;

    private UserAuthEntity user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");
        user = new UserAuthEntity();
        user.setUsername("alice@example.com");
        user.setEmail("alice@example.com");
        user.setRoles(List.of("USER"));
    }

    @Test
    void onAuthenticationSuccess_withGoogleEmail_redirectsWithToken() throws IOException {
        OAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(), Map.of("email", "alice@example.com", "sub", "12345"), "sub"
        );
        when(authentication.getPrincipal()).thenReturn(oauthUser);
        when(userService.findOrCreateUser("alice@example.com")).thenReturn(user);
        when(jwtService.generateToken(eq("alice@example.com"), eq("alice@example.com"), anyList())).thenReturn("jwt-token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(argThat(url ->
                url.contains("/login") && url.contains("token=") && url.contains("username=")
        ));
    }

    @Test
    void onAuthenticationSuccess_withGitHubLoginFallback_redirectsWithToken() throws IOException {
        // GitHub users without public email — uses login attribute
        OAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(), Map.of("login", "alice_github", "id", 999), "id"
        );
        when(authentication.getPrincipal()).thenReturn(oauthUser);

        UserAuthEntity githubUser = new UserAuthEntity();
        githubUser.setUsername("alice_github@github.oauth");
        githubUser.setRoles(List.of("USER"));
        when(userService.findOrCreateUser("alice_github@github.oauth")).thenReturn(githubUser);
        when(jwtService.generateToken(eq("alice_github@github.oauth"), isNull(), anyList())).thenReturn("jwt-token-gh");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(argThat(url -> url.contains("token=jwt-token-gh")));
    }

    @Test
    void onAuthenticationSuccess_noEmailOrLogin_usesOAuthName() throws IOException {
        // Last resort: no email, no login — uses OAuth2 name (subject)
        OAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(), Map.of("sub", "oauth_subject_123"), "sub"
        );
        when(authentication.getPrincipal()).thenReturn(oauthUser);

        UserAuthEntity fallbackUser = new UserAuthEntity();
        fallbackUser.setUsername("oauth_subject_123@oauth");
        fallbackUser.setRoles(List.of("USER"));
        when(userService.findOrCreateUser("oauth_subject_123@oauth")).thenReturn(fallbackUser);
        when(jwtService.generateToken(anyString(), any(), anyList())).thenReturn("jwt-fallback");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(any());
    }

    @Test
    void onAuthenticationSuccess_userWithEmptyRoles_defaultsToUser() throws IOException {
        user.setRoles(List.of()); // empty roles list
        OAuth2User oauthUser = new DefaultOAuth2User(
                Set.of(), Map.of("email", "alice@example.com", "sub", "12345"), "sub"
        );
        when(authentication.getPrincipal()).thenReturn(oauthUser);
        when(userService.findOrCreateUser("alice@example.com")).thenReturn(user);
        when(jwtService.generateToken(anyString(), any(), anyList())).thenReturn("jwt-token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(jwtService).generateToken(eq("alice@example.com"), eq("alice@example.com"), eq(List.of("USER")));
    }
}
