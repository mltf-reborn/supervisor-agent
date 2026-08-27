package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Response payload received from /api/v1/doc/processing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocProcessingResponseDto {

    private String status;
    private String message;
    private String gcsUrl;
    private String detectedDocumentType;
    private Map<String, Object> scores;
    private Map<String, Object> pixelLevelCheck;
    private Map<String, Object> extractedFields;
    private List<Map<String, Object>> fieldDetails;
    private Map<String, Object> metadata;

    public DocProcessingResponseDto() {
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

    public String getGcsUrl() {
        return gcsUrl;
    }

    public void setGcsUrl(String gcsUrl) {
        this.gcsUrl = gcsUrl;
    }

    public String getDetectedDocumentType() {
        return detectedDocumentType;
    }

    public void setDetectedDocumentType(String detectedDocumentType) {
        this.detectedDocumentType = detectedDocumentType;
    }

    public Map<String, Object> getScores() {
        return scores;
    }

    public void setScores(Map<String, Object> scores) {
        this.scores = scores;
    }

    public Map<String, Object> getPixelLevelCheck() {
        return pixelLevelCheck;
    }

    public void setPixelLevelCheck(Map<String, Object> pixelLevelCheck) {
        this.pixelLevelCheck = pixelLevelCheck;
    }

    public Map<String, Object> getExtractedFields() {
        return extractedFields;
    }

    public void setExtractedFields(Map<String, Object> extractedFields) {
        this.extractedFields = extractedFields;
    }

    public List<Map<String, Object>> getFieldDetails() {
        return fieldDetails;
    }

    public void setFieldDetails(List<Map<String, Object>> fieldDetails) {
        this.fieldDetails = fieldDetails;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean isTampered() {
        if (pixelLevelCheck != null && pixelLevelCheck.containsKey("isTampered")) {
            Object val = pixelLevelCheck.get("isTampered");
            if (val instanceof Boolean b) return b;
            if (val != null) return Boolean.parseBoolean(val.toString());
        }
        return false;
    }

    public double getDocumentScore() {
        if (scores != null && scores.containsKey("documentScore")) {
            Object val = scores.get("documentScore");
            if (val instanceof Number n) return n.doubleValue();
            if (val != null) {
                try {
                    return Double.parseDouble(val.toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 100.0;
    }
}
