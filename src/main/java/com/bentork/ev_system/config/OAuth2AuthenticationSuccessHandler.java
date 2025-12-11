package com.bentork.ev_system.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        Optional<User> optionalUser = userRepo.findByEmail(email);
        User user = optionalUser.orElseGet(() -> {
            User newUser = new User();
            newUser.setName(name);
            newUser.setEmail(email);
            newUser.setPassword("");
            try {
                return userRepo.save(newUser);
            } catch (Exception e) {
                throw new RuntimeException("Error saving new user: " + e.getMessage(), e);
            }
        });

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), "", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);

        String redirectUrl = "http://ev-user-webpage.s3-website.eu-north-1.amazonaws.com/login?token=" + token;

        String ocppId = (String) request.getSession().getAttribute("ocppId");
        if (ocppId != null) {
            redirectUrl += "&ocppId=" + ocppId;
        }

        response.sendRedirect(redirectUrl);
    }
}
