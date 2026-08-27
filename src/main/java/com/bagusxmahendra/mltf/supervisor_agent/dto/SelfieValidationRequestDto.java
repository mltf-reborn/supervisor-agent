package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload sent to /api/v1/selfie/validation.
 */
public class SelfieValidationRequestDto {

    @JsonProperty("idDocumentUrl")
    @JsonAlias({"id_document_url", "idGcsUrl", "id_gcs_url", "idDocument", "id_document"})
    private String idDocumentUrl;

    @JsonProperty("selfieUrl")
    @JsonAlias({"selfie_url", "selfieGcsUrl", "selfie_gcs_url", "selfie", "selfieImage"})
    private String selfieUrl;

    @JsonProperty("idDocumentMimeType")
    @JsonAlias({"id_document_mime_type", "idMimeType", "id_mime_type"})
    private String idDocumentMimeType;

    @JsonProperty("selfieMimeType")
    @JsonAlias({"selfie_mime_type", "selfieMimeType"})
    private String selfieMimeType;

    @JsonProperty("customPrompt")
    @JsonAlias({"custom_prompt", "instructions", "prompt"})
    private String customPrompt;

    public SelfieValidationRequestDto() {
    }

    public SelfieValidationRequestDto(String idDocumentUrl, String selfieUrl) {
        this.idDocumentUrl = idDocumentUrl;
        this.selfieUrl = selfieUrl;
    }

    public SelfieValidationRequestDto(String idDocumentUrl, String selfieUrl, String idDocumentMimeType, String selfieMimeType, String customPrompt) {
        this.idDocumentUrl = idDocumentUrl;
        this.selfieUrl = selfieUrl;
        this.idDocumentMimeType = idDocumentMimeType;
        this.selfieMimeType = selfieMimeType;
        this.customPrompt = customPrompt;
    }

    public String getIdDocumentUrl() {
        return idDocumentUrl;
    }

    public void setIdDocumentUrl(String idDocumentUrl) {
        this.idDocumentUrl = idDocumentUrl;
    }

    public String getSelfieUrl() {
        return selfieUrl;
    }

    public void setSelfieUrl(String selfieUrl) {
        this.selfieUrl = selfieUrl;
    }

    public String getIdDocumentMimeType() {
        return idDocumentMimeType;
    }

    public void setIdDocumentMimeType(String idDocumentMimeType) {
        this.idDocumentMimeType = idDocumentMimeType;
    }

    public String getSelfieMimeType() {
        return selfieMimeType;
    }

    public void setSelfieMimeType(String selfieMimeType) {
        this.selfieMimeType = selfieMimeType;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }
}
