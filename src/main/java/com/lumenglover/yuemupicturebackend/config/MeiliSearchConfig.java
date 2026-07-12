package com.lumenglover.yuemupicturebackend.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MeiliSearchConfig {

    @Value("${spring.meilisearch.host}")
    private String host;

    @Value("${spring.meilisearch.api-key:}")
    private String apiKey;

    @Bean
    public Client meiliSearchClient() {
        try {
            log.info("Initializing Meilisearch client with host: {}", host);
            Config config = new Config(host, apiKey);
            Client client = new Client(config);
            // Verify connection
            String version = client.getVersion();
            log.info("Successfully connected to Meilisearch server. Version: {}", version);
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Meilisearch client, host: {}, error: {}", host, e.getMessage(), e);
            // Return a client anyway to prevent spring boot context startup failure
            Config config = new Config(host, apiKey);
            return new Client(config);
        }
    }
}
