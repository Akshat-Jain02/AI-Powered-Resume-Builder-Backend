package com.resumeai.auth.auth2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = loadUserFromParent(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if ("github".equalsIgnoreCase(registrationId)) {
            return handleGitHubEmail(userRequest, oauthUser);
        }

        return oauthUser;
    }

    protected OAuth2User loadUserFromParent(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    private OAuth2User handleGitHubEmail(OAuth2UserRequest userRequest, OAuth2User oauthUser) {
        String email = (String) oauthUser.getAttributes().get("email");

        if (email == null || email.isBlank()) {
            log.info("GitHub email is private, fetching via API...");
            String fetchedEmail = fetchGitHubEmail(userRequest.getAccessToken().getTokenValue());
            
            if (fetchedEmail != null) {
                log.info("Successfully fetched private GitHub email: {}", fetchedEmail);
                // OAuth2User attributes are immutable in the default implementation, so we create a new map
                Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
                attributes.put("email", fetchedEmail);
                
                String userNameAttributeName = userRequest.getClientRegistration()
                        .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
                
                return new DefaultOAuth2User(oauthUser.getAuthorities(), attributes, userNameAttributeName);
            }
        }

        return oauthUser;
    }

    private String fetchGitHubEmail(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "https://api.github.com/user/emails",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> emails = response.getBody();
            if (emails != null && !emails.isEmpty()) {
                // Find primary verified email
                return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    // Or just the first verified one
                    .orElseGet(() -> emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                        .map(e -> (String) e.get("email"))
                        .findFirst()
                        // Or just the first one
                        .orElse((String) emails.get(0).get("email")));
            }
        } catch (Exception e) {
            log.error("Failed to fetch email from GitHub API: {}", e.getMessage());
        }
        return null;
    }
}
