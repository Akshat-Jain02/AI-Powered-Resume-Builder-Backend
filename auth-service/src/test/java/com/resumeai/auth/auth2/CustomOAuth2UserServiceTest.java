package com.resumeai.auth.auth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOAuth2UserServiceTest {

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OAuth2UserRequest userRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(customOAuth2UserService, "restTemplate", restTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadUser_Google_ReturnsOriginalUser() {
        // Arrange
        OAuth2User originalUser = mock(OAuth2User.class);
        ClientRegistration registration = createClientRegistration("google");
        
        when(userRequest.getClientRegistration()).thenReturn(registration);
        
        CustomOAuth2UserService spyService = spy(customOAuth2UserService);
        doReturn(originalUser).when(spyService).loadUserFromParent(any());

        // Act
        OAuth2User result = spyService.loadUser(userRequest);

        // Assert
        assertEquals(originalUser, result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadUser_Github_EmailPresent_ReturnsOriginalUser() {
        // Arrange
        Map<String, Object> attributes = Map.of("email", "test@example.com", "login", "testuser");
        OAuth2User originalUser = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        ClientRegistration registration = createClientRegistration("github");
        
        when(userRequest.getClientRegistration()).thenReturn(registration);
        
        CustomOAuth2UserService spyService = spy(customOAuth2UserService);
        doReturn(originalUser).when(spyService).loadUserFromParent(any());

        // Act
        OAuth2User result = spyService.loadUser(userRequest);

        // Assert
        assertEquals(originalUser, result);
        assertEquals("test@example.com", result.getAttributes().get("email"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadUser_Github_EmailMissing_FetchesFromApi() {
        // Arrange
        Map<String, Object> attributes = Map.of("login", "testuser");
        OAuth2User originalUser = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        ClientRegistration registration = createClientRegistration("github");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token123", Instant.now(), Instant.now().plusSeconds(3600));

        when(userRequest.getClientRegistration()).thenReturn(registration);
        when(userRequest.getAccessToken()).thenReturn(accessToken);
        
        CustomOAuth2UserService spyService = spy(customOAuth2UserService);
        doReturn(originalUser).when(spyService).loadUserFromParent(any());

        List<Map<String, Object>> mockEmailsResponse = List.of(
            Map.of("email", "private@example.com", "primary", true, "verified", true)
        );
        
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(mockEmailsResponse));

        // Act
        OAuth2User result = spyService.loadUser(userRequest);

        // Assert
        assertEquals("private@example.com", result.getAttributes().get("email"));
        assertEquals("testuser", result.getAttributes().get("login"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadUser_Github_EmailMissing_ApiEmpty_ReturnsOriginal() {
        // Arrange
        Map<String, Object> attributes = Map.of("login", "testuser");
        OAuth2User originalUser = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        ClientRegistration registration = createClientRegistration("github");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token123", Instant.now(), Instant.now().plusSeconds(3600));

        when(userRequest.getClientRegistration()).thenReturn(registration);
        when(userRequest.getAccessToken()).thenReturn(accessToken);
        
        CustomOAuth2UserService spyService = spy(customOAuth2UserService);
        doReturn(originalUser).when(spyService).loadUserFromParent(any());

        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // Act
        OAuth2User result = spyService.loadUser(userRequest);

        // Assert
        assertEquals(originalUser, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadUser_Github_EmailMissing_ApiThrows_ReturnsOriginal() {
        // Arrange
        Map<String, Object> attributes = Map.of("login", "testuser");
        OAuth2User originalUser = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        ClientRegistration registration = createClientRegistration("github");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token123", Instant.now(), Instant.now().plusSeconds(3600));

        when(userRequest.getClientRegistration()).thenReturn(registration);
        when(userRequest.getAccessToken()).thenReturn(accessToken);
        
        CustomOAuth2UserService spyService = spy(customOAuth2UserService);
        doReturn(originalUser).when(spyService).loadUserFromParent(any());

        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("API Down"));

        // Act
        OAuth2User result = spyService.loadUser(userRequest);

        // Assert
        assertEquals(originalUser, result);
    }

    private ClientRegistration createClientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("id")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("uri")
                .tokenUri("uri")
                .userInfoUri("uri")
                .redirectUri("uri")
                .userNameAttributeName("login")
                .build();
    }
}
