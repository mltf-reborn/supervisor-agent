package com.bagusxmahendra.mltf.supervisor_agent.dto;

public record ApplicationDocumentResponse(
        String documentFilename,
        String documentId,
        String documentStatus,
        String documentMessage
) {
}