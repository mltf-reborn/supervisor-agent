package com.bagusxmahendra.mltf.supervisor_agent.repository;

import reactor.core.publisher.Mono;

public interface ApplicationDocumentRepository {

    Mono<Void> save(
            String applicationId,
            String documentId,
            String filename,
            String gcsUrl,
            String contentType,
            String status,
            String message,
            String processingDetails
    );
}