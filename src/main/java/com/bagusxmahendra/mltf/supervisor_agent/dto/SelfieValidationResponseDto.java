package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response payload received from /api/v1/selfie/validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SelfieValidationResponseDto {

    private String status;
    private String message;

    @JsonProperty("isIdentical")
    @JsonAlias({"is_identical", "identical", "isMatch", "is_match"})
    private Boolean isIdentical;

    @JsonProperty("confidenceScore")
    @JsonAlias({"confidentScore", "confident_score", "confidence_score", "confidence", "score", "matchScore"})
    private Double confidenceScore;

    @JsonProperty("matchStatus")
    @JsonAlias({"match_status", "statusMatch", "verdict"})
    private String matchStatus;

    @JsonProperty("explanation")
    @JsonAlias({"explaination", "description", "reasoning", "details"})
    private String explanation;

    private String idDocumentUrl;
    private String selfieUrl;
    private Map<String, Object> facialComparisonDetails;
    private Map<String, Object> metadata;

    public SelfieValidationResponseDto() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsIdentical() {
        return isIdentical;
    }

    public void setIsIdentical(Boolean identical) {
        isIdentical = identical;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
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

    public Map<String, Object> getFacialComparisonDetails() {
        return facialComparisonDetails;
    }

    public void setFacialComparisonDetails(Map<String, Object> facialComparisonDetails) {
        this.facialComparisonDetails = facialComparisonDetails;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
