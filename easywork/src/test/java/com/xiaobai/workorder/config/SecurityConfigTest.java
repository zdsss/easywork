package com.xiaobai.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
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
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOrigins())
                .isNotNull()
                .contains("http://localhost:5173", "http://localhost:5174")
                .doesNotContain("*");
    }

    @Test
    void corsConfigurationSource_loadsAllowedMethodsFromConfig() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedMethods())
                .isNotNull()
                .contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Test
    void corsConfigurationSource_loadsMaxAgeFromConfig() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void corsConfigurationSource_allowedHeadersAllowAll() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedHeaders())
                .isNotNull()
                .contains("*");
    }
}
