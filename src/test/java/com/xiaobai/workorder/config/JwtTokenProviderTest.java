package com.xiaobai.workorder.config;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "test-jwt-secret-key-for-unit-tests-only-32chars";
    private static final long EXPIRATION = 3600000L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", EXPIRATION);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtTokenProvider.generateToken("EMP001", 1L, "WORKER");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_returnsCorrectSubject() {
        String token = jwtTokenProvider.generateToken("EMP001", 1L, "WORKER");
        String username = jwtTokenProvider.extractUsername(token);
        assertThat(username).isEqualTo("EMP001");
    }

    @Test
    void extractUserId_returnsCorrectLongValue() {
        String token = jwtTokenProvider.generateToken("EMP001", 42L, "WORKER");
        Long userId = jwtTokenProvider.extractUserId(token);
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtTokenProvider.generateToken("EMP001", 1L, "ADMIN");
        String role = jwtTokenProvider.extractRole(token);
        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "secret", SECRET);
        ReflectionTestUtils.setField(expiredProvider, "expiration", -1L);

        String expiredToken = expiredProvider.generateToken("EMP001", 1L, "WORKER");

        UserDetails userDetails = User.withUsername("EMP001")
                .password("pass")
                .authorities(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> expiredProvider.validateToken(expiredToken, userDetails))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_tamperedToken_throwsJwtException() {
        String token = jwtTokenProvider.generateToken("EMP001", 1L, "WORKER");
        String tamperedToken = token + "tampered";

        assertThatThrownBy(() -> jwtTokenProvider.extractUsername(tamperedToken))
                .isInstanceOf(JwtException.class);
    }
}
