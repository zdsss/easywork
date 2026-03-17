package com.xiaobai.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void corsConfigurationSource_loadsAllowedOriginsFromConfig() {
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(null);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOrigins())
                .isNotNull()
                .contains("http://localhost:5173", "http://localhost:5174")
                .doesNotContain("*");
    }

    @Test
    void corsConfigurationSource_loadsAllowedMethodsFromConfig() {
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(null);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedMethods())
                .isNotNull()
                .contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Test
    void corsConfigurationSource_loadsMaxAgeFromConfig() {
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(null);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void corsConfigurationSource_allowedHeadersAllowAll() {
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(null);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedHeaders())
                .isNotNull()
                .contains("*");
    }
}
