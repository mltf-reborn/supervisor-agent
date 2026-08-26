package com.bagusxmahendra.mltf.supervisor_agent.model;

public enum KycStatus {
    PENDING,
    IN_REVIEW,
    APPROVED,
    REJECTED;

    public static KycStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return KycStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
