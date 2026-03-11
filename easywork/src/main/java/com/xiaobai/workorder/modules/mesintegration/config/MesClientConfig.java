package com.xiaobai.workorder.modules.mesintegration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")
public class MesClientConfig {

    @Value("${app.mes.integration.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.mes.integration.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean(name = "mesRestTemplate")
    public RestTemplate mesRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
