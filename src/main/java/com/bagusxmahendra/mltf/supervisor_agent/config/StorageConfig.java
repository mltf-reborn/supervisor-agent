package com.bagusxmahendra.mltf.supervisor_agent.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public Storage storage(StorageProperties properties) {
        log.info("Initializing Google Cloud Storage client for project: {}, bucket: {}",
                properties.getProjectId(), properties.getBucketName());

        StorageOptions.Builder builder = StorageOptions.newBuilder()
                .setProjectId(properties.getProjectId());

        return builder.build().getService();
    }
}
