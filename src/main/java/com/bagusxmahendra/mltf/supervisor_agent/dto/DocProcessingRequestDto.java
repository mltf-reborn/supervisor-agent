package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload sent to /api/v1/doc/processing.
 */
public class DocProcessingRequestDto {

    @JsonProperty("gcsUrl")
    @JsonAlias({"gcs_url", "gcs_uri", "gcsUri", "url", "documentUrl", "document_url"})
    private String gcsUrl;

    @JsonProperty("mimeType")
    @JsonAlias({"mime_type", "contentType", "content_type"})
    private String mimeType;

    @JsonProperty("documentType")
    @JsonAlias({"document_type"})
    private String documentType;

    @JsonProperty("customPrompt")
    @JsonAlias({"custom_prompt", "instructions", "additional_instructions"})
    private String customPrompt;

    public DocProcessingRequestDto() {
    }

    public DocProcessingRequestDto(String gcsUrl) {
        this.gcsUrl = gcsUrl;
    }

    public DocProcessingRequestDto(String gcsUrl, String mimeType, String documentType, String customPrompt) {
        this.gcsUrl = gcsUrl;
        this.mimeType = mimeType;
        this.documentType = documentType;
        this.customPrompt = customPrompt;
    }

    public String getGcsUrl() {
        return gcsUrl;
    }

    public void setGcsUrl(String gcsUrl) {
        this.gcsUrl = gcsUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }
}
