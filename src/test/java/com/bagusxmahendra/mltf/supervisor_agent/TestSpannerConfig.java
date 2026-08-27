package com.bagusxmahendra.mltf.supervisor_agent;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Spanner;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestSpannerConfig {

    @Bean
    @Primary
    public Spanner testSpanner() {
        return Mockito.mock(Spanner.class);
    }

    @Bean
    @Primary
    public DatabaseClient testDatabaseClient() {
        return Mockito.mock(DatabaseClient.class);
    }

    @Bean
    @Primary
    public com.google.cloud.storage.Storage testStorage() {
        return Mockito.mock(com.google.cloud.storage.Storage.class);
    }
}
