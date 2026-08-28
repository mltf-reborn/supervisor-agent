package com.bagusxmahendra.mltf.supervisor_agent.dto;

import java.util.List;

public record ApplicationInquiryResponse(
        String applicationID,
        String status,
        List<ApplicationDocumentItem> documents
) {
}
