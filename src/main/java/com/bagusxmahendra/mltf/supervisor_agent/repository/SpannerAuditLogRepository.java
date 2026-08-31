package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.AuditLog;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;

@Repository
public class SpannerAuditLogRepository implements AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(SpannerAuditLogRepository.class);

    private static final String SELECT_AUDIT_LOG_COLUMNS =
            "SELECT processing_date, type, reference_id, subject, description, status " +
            "FROM audit_log ";

    private final DatabaseClient databaseClient;

    public SpannerAuditLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(AuditLog auditLog) {
        return Mono.fromRunnable(() -> {
            log.debug("Saving audit log to Spanner – type: {}, referenceId: {}", auditLog.type(), auditLog.referenceId());

            Mutation.WriteBuilder builder = Mutation.newInsertOrUpdateBuilder("audit_log")
                    .set("processing_date").to(Value.COMMIT_TIMESTAMP)
                    .set("type").to(auditLog.type())
                    .set("reference_id").to(auditLog.referenceId())
                    .set("subject").to(auditLog.subject())
                    .set("description").to(auditLog.description())
                    .set("status").to(auditLog.status());

            databaseClient.write(Collections.singletonList(builder.build()));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    @Override
    public Mono<AuditLog> findByReferenceId(String referenceId) {
        return Mono.fromCallable(() -> {
            log.debug("Executing Spanner query to find audit log for referenceId: {}", referenceId);
            Statement statement = Statement.newBuilder(SELECT_AUDIT_LOG_COLUMNS + "WHERE reference_id = @referenceId")
                    .bind("referenceId").to(referenceId)
                    .build();

            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                if (rs.next()) {
                    return AuditLog.fromStruct(rs.getCurrentRowAsStruct());
                }
                return null;
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<java.util.List<AuditLog>> findAll(int limit) {
        return Mono.fromCallable(() -> {
            int maxRows = limit > 0 ? limit : 200;
            log.debug("Executing Spanner query to find all audit logs, limit: {}", maxRows);
            Statement statement = Statement.newBuilder(
                    SELECT_AUDIT_LOG_COLUMNS + "ORDER BY processing_date DESC LIMIT @limit"
            ).bind("limit").to(maxRows).build();

            java.util.List<AuditLog> list = new java.util.ArrayList<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                while (rs.next()) {
                    list.add(AuditLog.fromStruct(rs.getCurrentRowAsStruct()));
                }
            }
            return list;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<java.util.List<AuditLog>> findByType(String type, int limit) {
        return Mono.fromCallable(() -> {
            int maxRows = limit > 0 ? limit : 200;
            log.debug("Executing Spanner query to find audit logs by type: {}, limit: {}", type, maxRows);
            Statement statement = Statement.newBuilder(
                    SELECT_AUDIT_LOG_COLUMNS + "WHERE type = @type ORDER BY processing_date DESC LIMIT @limit"
            ).bind("type").to(type)
             .bind("limit").to(maxRows)
             .build();

            java.util.List<AuditLog> list = new java.util.ArrayList<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                while (rs.next()) {
                    list.add(AuditLog.fromStruct(rs.getCurrentRowAsStruct()));
                }
            }
            return list;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}
