package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result payload returned from graph analysis service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphAnalysisResult(
    @JsonProperty("status")
    String status,

    @JsonProperty("checkName")
    String checkName,

    @JsonProperty("passed")
    boolean passed,

    @JsonProperty("discrepancies")
    List<String> discrepancies
) {
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_FLAGGED = "FLAGGED";
    public static final String STATUS_REJECTED = "REJECTED";
}
