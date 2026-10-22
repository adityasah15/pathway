package com.tcet.RestApi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AnalyticsClientConfig {

    @Bean
    RestClient analyticsRestClient(
            RestClient.Builder builder,
            @Value("${analytics.service.url:http://localhost:5000}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
