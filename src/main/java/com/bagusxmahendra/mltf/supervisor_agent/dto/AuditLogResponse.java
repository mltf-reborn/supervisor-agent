package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.bagusxmahendra.mltf.supervisor_agent.model.AuditLog;

public record AuditLogResponse(
        String processingDate,
        String type,
        String referenceId,
        String subject,
        String description,
        String status
) {
    public static AuditLogResponse fromModel(AuditLog model) {
        if (model == null) return null;
        String dateStr = model.processingDate() != null ? model.processingDate().toString() : null;
        return new AuditLogResponse(
                dateStr,
                model.type(),
                model.referenceId(),
                model.subject(),
                model.description(),
                model.status()
        );
    }
}
