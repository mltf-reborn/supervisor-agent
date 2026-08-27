package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response payload returned by Case Management Service (POST/GET /api/v1/case).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaseResponse(
        String caseId,
        String userId,
        String caseType,
        String caseStatus,
        String documentUrl,
        String selfieUrl,
        Object documentVerificationDetails,
        Object selfieDetails,
        Object kycDetails,
        Double riskScore,
        String riskLevel,
        String rejectionReason,
        String remarks,
        String assignedTo,
        Instant createdAt,
        Instant updatedAt
) {
}
