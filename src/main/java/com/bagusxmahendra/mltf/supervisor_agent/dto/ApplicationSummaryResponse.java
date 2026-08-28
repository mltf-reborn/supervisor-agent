package com.bagusxmahendra.mltf.supervisor_agent.dto;

public record ApplicationSummaryResponse(
        String applicationReferenceNumber,
        String dateApplied,
        String facilityPurpose,
        String propertyProject,
        String propertyPrice,
        String applicationType,
        String applicationStatus
) {
}