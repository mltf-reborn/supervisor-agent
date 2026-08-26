package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;

public record KycStatusResponse(
        KycStatus status
) {
    public static KycStatusResponse from(KycProfile profile) {
        if (profile == null) {
            return null;
        }
        return new KycStatusResponse(profile.status());
    }
}

