package com.bagusxmahendra.mltf.supervisor_agent.model;

public record SubmittedApplication(
        String transactionId,
        String userId,
        String applicationType,
        String status
) {
}
