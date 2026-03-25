package com.bentork.ev_system.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oAuth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oAuth2Token.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String imageUrl = oAuth2User.getAttribute("picture");

        // Fetch phone number from Google People API
        String phoneNumber = fetchPhoneNumberFromPeopleApi(oAuth2Token);

        Optional<User> optionalUser = userRepo.findByEmail(email);
        User user = optionalUser.orElseGet(() -> {
            User newUser = new User();
            newUser.setName(name);
            newUser.setEmail(email);
            newUser.setPassword("");
            newUser.setImageUrl(imageUrl);

            // Set phone number if available
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                newUser.setMobile(phoneNumber);
            }

            try {
                return userRepo.save(newUser);
            } catch (Exception e) {
                throw new RuntimeException("Error saving new user: " + e.getMessage(), e);
            }
        });

        // Update profile picture if user exists but image changed
        if (optionalUser.isPresent()) {
            User existingUser = optionalUser.get();
            boolean updated = false;

            if (imageUrl != null && !imageUrl.equals(existingUser.getImageUrl())) {
                existingUser.setImageUrl(imageUrl);
                updated = true;
            }

            // Update phone number if it was not set before and we got one from Google
            if (phoneNumber != null && !phoneNumber.isEmpty()
                    && (existingUser.getMobile() == null || existingUser.getMobile().isEmpty())) {
                existingUser.setMobile(phoneNumber);
                updated = true;
            }

            if (updated) {
                user = userRepo.save(existingUser);
            }
        }

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), "", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);

        String redirectUrl = "https://web.bentork.in/login?token=" + token;

        String ocppId = (String) request.getSession().getAttribute("ocppId");
        if (ocppId != null) {
            redirectUrl += "&ocppId=" + ocppId;
        }

        response.sendRedirect(redirectUrl);
    }

    /**
     * Fetches the user's phone number from Google People API using the OAuth2
     * access token.
     * Returns the first phone number found, or null if none available.
     */
    @SuppressWarnings("unchecked")
    private String fetchPhoneNumberFromPeopleApi(OAuth2AuthenticationToken oAuth2Token) {
        try {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oAuth2Token.getAuthorizedClientRegistrationId(),
                    oAuth2Token.getName());

            if (client == null || client.getAccessToken() == null) {
                log.warn("No authorized client or access token found for People API call");
                return null;
            }

            String accessToken = client.getAccessToken().getTokenValue();

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> apiResponse = restTemplate.exchange(
                    "https://people.googleapis.com/v1/people/me?personFields=phoneNumbers",
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (apiResponse.getBody() != null) {
                List<Map<String, Object>> phoneNumbers = (List<Map<String, Object>>) apiResponse.getBody()
                        .get("phoneNumbers");
                if (phoneNumbers != null && !phoneNumbers.isEmpty()) {
                    String phone = (String) phoneNumbers.get(0).get("value");
                    log.info("Phone number fetched from Google People API for user: {}", oAuth2Token.getName());
                    return phone;
                }
            }

            log.info("No phone number found in Google account for user: {}", oAuth2Token.getName());
            return null;

        } catch (Exception e) {
            log.warn("Failed to fetch phone number from Google People API: {}", e.getMessage());
            return null;
        }
    }
}
