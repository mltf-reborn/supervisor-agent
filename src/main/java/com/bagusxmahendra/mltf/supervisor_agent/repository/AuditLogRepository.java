package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.AuditLog;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AuditLogRepository {

    Mono<Void> save(AuditLog auditLog);

    Mono<AuditLog> findByReferenceId(String referenceId);

    Mono<List<AuditLog>> findAll(int limit);

    Mono<List<AuditLog>> findByType(String type, int limit);
}

