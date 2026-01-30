package com.bentork.ev_system.config;

import com.bentork.ev_system.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

        @Autowired
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @Autowired
        private CustomUserDetailsService userDetailsService;

        @Autowired
        private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // Add this

        @Autowired
        private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider daoAuthenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setUserDetailsService(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder());
                return provider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> {
                                }) // Enable CORS support
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(jwtAuthenticationEntryPoint) // Use custom
                                                                                                       // entry point
                                )
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/api/user/signup",
                                                                "/api/user/login",
                                                                "/api/admin/signup",
                                                                "/api/admin/login",
                                                                "/api/user/request-otp",
                                                                "/api/user/reset-password",
                                                                "/api/admin/request-otp",
                                                                "/api/admin/reset-password",
                                                                "/oauth2/**",
                                                                "/login/**",
                                                                "/api/user/google-login-success",
                                                                "/api/user/byemail/**",
                                                                "/error",
                                                                "/favicon.ico")
                                                .permitAll()
                                                // Allow authenticated users to READ stations
                                                .requestMatchers(HttpMethod.GET, "/api/stations/**").authenticated()
                                                // Only ADMIN can create/update/delete stations
                                                .requestMatchers(HttpMethod.POST, "/api/stations/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/stations/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/stations/**")
                                                .hasAuthority("ADMIN")

                                                // Allow authenticated users to READ chargers
                                                .requestMatchers(HttpMethod.GET, "/api/chargers/**").authenticated()
                                                // Only ADMIN can create/update/delete chargers
                                                .requestMatchers(HttpMethod.POST, "/api/chargers/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/chargers/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/chargers/**")
                                                .hasAuthority("ADMIN")

                                                // Allow authenticated users to READ locations
                                                .requestMatchers(HttpMethod.GET, "/api/location/**").authenticated()
                                                // Only ADMIN can create/update/delete locations
                                                .requestMatchers(HttpMethod.POST, "/api/location/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/location/**")
                                                .hasAuthority("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/location/**")
                                                .hasAuthority("ADMIN")

                                                // Admin-only endpoints
                                                .requestMatchers(
                                                                "/api/plans/**",
                                                                "/api/emergency-contacts/**",
                                                                "/api/revenue/**")
                                                .hasAuthority("ADMIN")

                                                // Dealer station management (Admin only)
                                                .requestMatchers("/api/dealer-stations/**").hasAuthority("ADMIN")

                                                // Dealer endpoints (Dealer can access their own data)
                                                .requestMatchers("/api/dealer/**").hasAuthority("DEALER")

                                                .requestMatchers("/api/user-plan-selection/**").permitAll()

                                                // Add this line to allow authenticated users to access sessions
                                                .requestMatchers("/api/sessions/**").authenticated()
                                                .requestMatchers("/api/user/charger/**").authenticated()
                                                .requestMatchers("/api/user/plans/**").authenticated()
                                                .requestMatchers("/api/wallet/**").authenticated()
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(daoAuthenticationProvider())
                                .oauth2Login(oauth -> oauth
                                                .successHandler(oAuth2AuthenticationSuccessHandler))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
