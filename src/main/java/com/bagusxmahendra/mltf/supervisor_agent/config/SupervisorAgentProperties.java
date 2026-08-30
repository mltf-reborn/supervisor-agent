package com.bagusxmahendra.mltf.supervisor_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "google.gemini")
public class SupervisorAgentProperties {

    /**
     * Gemini API Key for Google AI Studio / Gemini Developer API.
     */
    private String apiKey;

    /**
     * Gemini model name (default: gemini-3.5-flash-lite).
     */
    private String model = "gemini-3.5-flash-lite";

    /**
     * GCP Project ID if using Vertex AI.
     */
    private String projectId;

    /**
     * GCP Location / Region if using Vertex AI.
     */
    private String location = "us-central1";

    /**
     * Flag whether to use Vertex AI instead of Gemini Developer API.
     */
    private boolean useVertexAi = false;

    /**
     * Generation temperature (default: 0.1 for deterministic supervisor orchestration).
     */
    private float temperature = 0.1f;

    /**
     * Request timeout in seconds.
     */
    private int timeoutSeconds = 120;

    /**
     * Base URL for external Document Processing Agent (/api/v1/doc and /api/v1/selfie).
     */
    private String documentProcessingUrl = "http://localhost:8081";

    /**
     * Base URL for external KYC Verification Service (/api/v1/external/kyc).
     */
    private String externalKycUrl = "http://localhost:8080";

    /**
     * Base URL for Case Management Service (/api/v1/case).
     */
    private String caseManagementUrl = "http://localhost:8082";

    /**
     * Threshold for automated KYC Approval (default: 85.0%).
     */
    private double approvedThreshold = 85.0;

    /**
     * Threshold below which KYC is automatically Rejected as fraud (default: 50.0%).
     */
    private double rejectionThreshold = 50.0;

    /**
     * Similarity threshold for checking existing vs incoming application/applicant/property data (default: 0.80 / 80%).
     * If existing data similarity is >= threshold, data is updated; if < threshold, conflict error is thrown.
     */
    private double similarityThreshold = 0.80;

    /**
     * Flag whether to enable similarity checks (default: true).
     */
    private boolean similarityCheckEnabled = true;

    /**
     * Set of field names to ignore during data similarity and conflict detection.
     * Defaults to metadata, lifecycle statuses, timestamps, and primary/foreign keys.
     */
    private Set<String> similarityIgnoredFields = new HashSet<>(Set.of(
            "status", "application_status", "document_status",
            "application_date", "applicationdate",
            "created_at", "createdat",
            "updated_at", "updatedat",
            "verified_at", "verifiedat",
            "transaction_id", "transactionid",
            "user_id", "userid",
            "applicant_id", "applicantid",
            "property_id", "propertyid",
            "document_id", "documentid"
    ));

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isUseVertexAi() {
        return useVertexAi;
    }

    public void setUseVertexAi(boolean useVertexAi) {
        this.useVertexAi = useVertexAi;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDocumentProcessingUrl() {
        return documentProcessingUrl;
    }

    public void setDocumentProcessingUrl(String documentProcessingUrl) {
        this.documentProcessingUrl = documentProcessingUrl;
    }

    public String getExternalKycUrl() {
        return externalKycUrl;
    }

    public void setExternalKycUrl(String externalKycUrl) {
        this.externalKycUrl = externalKycUrl;
    }

    public String getCaseManagementUrl() {
        return caseManagementUrl;
    }

    public void setCaseManagementUrl(String caseManagementUrl) {
        this.caseManagementUrl = caseManagementUrl;
    }

    public double getApprovedThreshold() {
        return approvedThreshold;
    }

    public void setApprovedThreshold(double approvedThreshold) {
        this.approvedThreshold = approvedThreshold;
    }

    public double getRejectionThreshold() {
        return rejectionThreshold;
    }

    public void setRejectionThreshold(double rejectionThreshold) {
        this.rejectionThreshold = rejectionThreshold;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isSimilarityCheckEnabled() {
        return similarityCheckEnabled;
    }

    public void setSimilarityCheckEnabled(boolean similarityCheckEnabled) {
        this.similarityCheckEnabled = similarityCheckEnabled;
    }

    public Set<String> getSimilarityIgnoredFields() {
        return similarityIgnoredFields;
    }

    public void setSimilarityIgnoredFields(Set<String> similarityIgnoredFields) {
        this.similarityIgnoredFields = similarityIgnoredFields;
    }

    /**
     * Centralized check to determine if a field should be ignored during conflict detection.
     * Matches case-insensitively and ignores underscores/hyphens (e.g. application_date == applicationDate).
     */
    public boolean isFieldIgnored(String fieldName, java.util.Collection<String> customIgnored) {
        if (fieldName == null || fieldName.isBlank()) {
            return true;
        }
        String normalized = fieldName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (customIgnored != null) {
            for (String f : customIgnored) {
                if (f != null && f.replaceAll("[^a-zA-Z0-9]", "").equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
        }
        if (similarityIgnoredFields != null) {
            for (String f : similarityIgnoredFields) {
                if (f != null && f.replaceAll("[^a-zA-Z0-9]", "").equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isFieldIgnored(String fieldName) {
        return isFieldIgnored(fieldName, null);
    }
}
