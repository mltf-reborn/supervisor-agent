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
        String upper = value.trim().toUpperCase();
        if ("SUCCESS".equals(upper) || "APPROVED".equals(upper) || "APPROVE".equals(upper) || "PASSED".equals(upper)) {
            return APPROVED;
        }
        if ("FAILED".equals(upper) || "REJECTED".equals(upper) || "FAIL".equals(upper) || "REJECT".equals(upper) || "FRAUD".equals(upper)) {
            return REJECTED;
        }
        if ("IN_REVIEW".equals(upper) || "INREVIEW".equals(upper) || "MANUAL_REVIEW".equals(upper) || "REVIEW".equals(upper)) {
            return IN_REVIEW;
        }
        try {
            return KycStatus.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
