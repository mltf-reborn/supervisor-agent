package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.DocumentRecord;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
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
            Mutation mutation = Mutation.newInsertOrUpdateBuilder("document")
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

    @Override
    public Mono<Void> delete(String applicationId, String documentId) {
        return Mono.fromRunnable(() -> {
            Mutation mutation = Mutation.delete("document", Key.of(applicationId, documentId));
            databaseClient.write(List.of(mutation));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<List<DocumentRecord>> findByTransactionId(String transactionId) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                    "SELECT transaction_id, document_id, document_filename, gcs_url, content_type, " +
                    "document_status, document_message, document_processing_details, created_at " +
                    "FROM document WHERE transaction_id = @transactionId ORDER BY created_at ASC")
                    .bind("transactionId").to(transactionId)
                    .build();

            List<DocumentRecord> list = new ArrayList<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                while (rs.next()) {
                    com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                    list.add(new DocumentRecord(
                            row.getString("transaction_id"),
                            row.getString("document_id"),
                            row.isNull("document_filename") ? null : row.getString("document_filename"),
                            row.isNull("gcs_url") ? null : row.getString("gcs_url"),
                            row.isNull("content_type") ? null : row.getString("content_type"),
                            row.isNull("document_status") ? null : row.getString("document_status"),
                            row.isNull("document_message") ? null : row.getString("document_message"),
                            row.isNull("document_processing_details") ? null : row.getString("document_processing_details"),
                            row.isNull("created_at") ? null : row.getTimestamp("created_at").toSqlTimestamp().toInstant()
                    ));
                }
            }
            return list;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateDocumentProcessingResult(
            String transactionId,
            String documentId,
            String status,
            String message,
            String processingDetails
    ) {
        return Mono.fromRunnable(() -> {
            Mutation mutation = Mutation.newUpdateBuilder("document")
                    .set("transaction_id").to(transactionId)
                    .set("document_id").to(documentId)
                    .set("document_status").to(status)
                    .set("document_message").to(message)
                    .set("document_processing_details").to(processingDetails)
                    .build();
            databaseClient.write(List.of(mutation));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}

