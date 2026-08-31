package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Dynamic document payload containing extracted data for graph analysis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DynamicDocumentData(
    @JsonProperty("documentType")
    String documentType,

    @JsonProperty("extractedData")
    Map<String, Object> extractedData
) {}
