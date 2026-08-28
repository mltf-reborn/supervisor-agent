package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Repository
public class SpannerApplicationDocumentRepository implements ApplicationDocumentRepository {

    private final DatabaseClient databaseClient;

    public SpannerApplicationDocumentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(
            String applicationId,
            String documentId,
            String filename,
            String gcsUrl,
            String contentType,
            String status,
            String message,
            String processingDetails
    ) {
        return Mono.fromRunnable(() -> {
            Mutation mutation = Mutation.newInsertBuilder("document")
                    .set("transaction_id").to(applicationId)
                    .set("document_id").to(documentId)
                    .set("document_filename").to(filename)
                    .set("gcs_url").to(gcsUrl)
                    .set("content_type").to(contentType)
                    .set("document_status").to(status)
                    .set("document_message").to(message)
                    .set("document_processing_details").to(processingDetails)
                    .set("created_at").to(Value.COMMIT_TIMESTAMP)
                    .build();
            databaseClient.write(List.of(mutation));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}