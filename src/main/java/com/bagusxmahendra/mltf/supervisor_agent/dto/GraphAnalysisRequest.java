package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Ingestion request payload carrying flat loan application data and attached extracted documents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphAnalysisRequest(
    @JsonProperty("loanApplication")
    Map<String, Object> loanApplication,

    @JsonProperty("documents")
    List<DynamicDocumentData> documents
) {}
