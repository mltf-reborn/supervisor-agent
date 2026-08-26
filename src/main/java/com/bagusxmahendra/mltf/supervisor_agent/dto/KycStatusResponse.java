package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KycStatusResponse(
        String userId,
        KycStatus status,
        String fullName,
        String email,
        String phoneNumber,
        String idCardType,
        Double riskScore,
        String riskLevel,
        String rejectionReason,
        String remarks,
        Instant verifiedAt,
        Instant updatedAt
) {
    public KycStatusResponse(KycStatus status) {
        this(null, status, null, null, null, null, null, null, null, null, null, null);
    }

    public static KycStatusResponse from(KycProfile profile) {
        if (profile == null) {
            return null;
        }
        return new KycStatusResponse(
                profile.userId(),
                profile.status(),
                profile.fullName(),
                profile.email(),
                profile.phoneNumber(),
                profile.idCardType(),
                profile.riskScore(),
                profile.riskLevel(),
                profile.rejectionReason(),
                profile.remarks(),
                profile.verifiedAt(),
                profile.updatedAt()
        );
    }
}
