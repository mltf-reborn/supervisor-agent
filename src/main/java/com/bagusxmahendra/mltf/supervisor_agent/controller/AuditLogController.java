package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.AuditLogResponse;
import com.bagusxmahendra.mltf.supervisor_agent.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/audit-log", "/api/v1/audit-logs"})
public class AuditLogController {

    private static final Logger log = LoggerFactory.getLogger(AuditLogController.class);

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Get all audit log entries from Spanner audit_log table.
     * Supports optional type filtering and limit.
     */
    @GetMapping
    public Mono<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        log.info("Fetching audit logs from Spanner - type: {}, limit: {}", type, limit);
        return auditLogService.getAuditLogs(type, limit);
    }

    /**
     * Get a single audit log entry by reference ID.
     */
    @GetMapping("/{referenceId}")
    public Mono<AuditLogResponse> getAuditLogByReferenceId(@PathVariable String referenceId) {
        log.info("Fetching audit log by referenceId: {}", referenceId);
        return auditLogService.getAuditLogByReferenceId(referenceId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Audit log not found for reference ID: " + referenceId
                )));
    }
}
