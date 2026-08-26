package com.bagusxmahendra.mltf.supervisor_agent.config;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
public class SpannerConfig {

    private static final Logger log = LoggerFactory.getLogger(SpannerConfig.class);

    private Spanner spanner;

    @Bean
    @ConditionalOnMissingBean
    public Spanner spanner(SpannerProperties properties) {
        log.info("Initializing Spanner client for project: {}, instance: {}, database: {}",
                properties.getProjectId(), properties.getInstanceId(), properties.getDatabaseId());

        SpannerOptions.Builder builder = SpannerOptions.newBuilder()
                .setProjectId(properties.getProjectId());

        if (properties.getEmulatorHost() != null && !properties.getEmulatorHost().isBlank()) {
            log.info("Configuring Spanner emulator with host: {}", properties.getEmulatorHost());
            builder.setEmulatorHost(properties.getEmulatorHost());
        }

        this.spanner = builder.build().getService();
        return this.spanner;
    }

    @Bean
    @ConditionalOnMissingBean
    public DatabaseClient databaseClient(Spanner spanner, SpannerProperties properties) {
        DatabaseId databaseId = DatabaseId.of(
                properties.getProjectId(),
                properties.getInstanceId(),
                properties.getDatabaseId()
        );
        log.info("Creating Spanner DatabaseClient for {}", databaseId);
        return spanner.getDatabaseClient(databaseId);
    }

    @PreDestroy
    public void close() {
        if (this.spanner != null && !this.spanner.isClosed()) {
            log.info("Closing Spanner client...");
            this.spanner.close();
        }
    }
}
