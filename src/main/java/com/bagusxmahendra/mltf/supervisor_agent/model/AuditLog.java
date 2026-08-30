package com.bagusxmahendra.mltf.supervisor_agent.model;

import com.google.cloud.Timestamp;

public record AuditLog(
        Timestamp processingDate,
        String type,
        String referenceId,
        String subject,
        String description,
        String status
) {
    public static AuditLog fromStruct(com.google.cloud.spanner.Struct struct) {
        Timestamp processingDate = struct.getTimestamp("processing_date");
        String type = struct.isNull("type") ? null : struct.getString("type");
        String referenceId = struct.isNull("reference_id") ? null : struct.getString("reference_id");
        String subject = struct.isNull("subject") ? null : struct.getString("subject");
        String description = struct.isNull("description") ? null : struct.getString("description");
        String status = struct.isNull("status") ? null : struct.getString("status");

        return new AuditLog(processingDate, type, referenceId, subject, description, status);
    }
}
