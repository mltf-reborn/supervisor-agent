package com.bagusxmahendra.mltf.supervisor_agent.model;

import java.time.Instant;

public record DocumentRecord(
        String transactionId,
        String documentId,
        String documentFilename,
        String gcsUrl,
        String contentType,
        String documentStatus,
        String documentMessage,
        String documentProcessingDetails,
        Instant createdAt
) {
}
