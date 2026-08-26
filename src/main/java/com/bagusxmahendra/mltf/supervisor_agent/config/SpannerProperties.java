package com.bagusxmahendra.mltf.supervisor_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gcp.spanner")
public class SpannerProperties {

    private String projectId = "mltf-506212";
    private String instanceId = "mltf-spanner";
    private String databaseId = "mortgage_db";
    private String emulatorHost;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public String getEmulatorHost() {
        return emulatorHost;
    }

    public void setEmulatorHost(String emulatorHost) {
        this.emulatorHost = emulatorHost;
    }
}
