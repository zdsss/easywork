package com.xiaobai.workorder.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration. Replaces the stateless JWT filter chain with a
 * session-aware filter chain so that SecurityMockMvcRequestPostProcessors.user()
 * (which saves to the HTTP session) works correctly in @WebMvcTest tests.
 *
 * The same authorization rules as the production SecurityConfig are preserved.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Order(1)
    @org.springframework.context.annotation.Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/device/login").permitAll()
                        .requestMatchers("/api/device/**").hasAnyRole("WORKER", "ADMIN")
                        .requestMatchers("/api/mes/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // IF_REQUIRED (default) session management — lets SecurityMockMvcRequestPostProcessors
                // store the SecurityContext in the HTTP session where SecurityContextHolderFilter
                // can load it back, enabling .with(user(...).roles(...)) to work correctly.
                .build();
    }
}
