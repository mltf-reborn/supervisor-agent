package com.bagusxmahendra.mltf.supervisor_agent.dto;

public record ApplicationDocumentItem(
        String id,
        String filename,
        String status,
        String message
) {
}
