package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.DocumentRecord;
import reactor.core.publisher.Mono;

import java.util.List;

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

    Mono<Void> delete(String applicationId, String documentId);

    Mono<List<DocumentRecord>> findByTransactionId(String transactionId);

    Mono<Void> updateDocumentProcessingResult(
            String transactionId,
            String documentId,
            String status,
            String message,
            String processingDetails
    );
}

