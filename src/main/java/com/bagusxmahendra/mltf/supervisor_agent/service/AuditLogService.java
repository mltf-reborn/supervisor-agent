package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.AuditLogResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.AuditLog;
import com.bagusxmahendra.mltf.supervisor_agent.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String LOG_TYPE_KYC = "KYC";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Mono<Void> logKycVerification(String userId, String fullName, String idNumber,
                                         String referenceId, KycVerifyResponse response) {
        String subject = buildSubject(userId, fullName, idNumber);
        String description = serializeResponse(response);

        AuditLog auditLog = new AuditLog(
                null,
                LOG_TYPE_KYC,
                referenceId,
                subject,
                description,
                response.status()
        );

        return auditLogRepository.save(auditLog)
                .doOnSuccess(v -> log.info("KYC audit log saved – referenceId: {}, userId: {}", referenceId, userId))
                .doOnError(e -> log.error("Failed to save KYC audit log – referenceId: {}, error: {}", referenceId, e.getMessage()));
    }

    public Mono<List<AuditLogResponse>> getAuditLogs(String type, Integer limit) {
        int max = (limit != null && limit > 0) ? limit : 200;
        Mono<List<AuditLog>> source = (type != null && !type.isBlank())
                ? auditLogRepository.findByType(type.toUpperCase(), max)
                : auditLogRepository.findAll(max);

        return source.map(list -> list.stream()
                .map(AuditLogResponse::fromModel)
                .collect(Collectors.toList()));
    }

    public Mono<AuditLogResponse> getAuditLogByReferenceId(String referenceId) {
        return auditLogRepository.findByReferenceId(referenceId)
                .map(AuditLogResponse::fromModel);
    }

    private String buildSubject(String userId, String fullName, String idNumber) {
        return LOG_TYPE_KYC + " " + userId + "-" + fullName + "-" + idNumber;
    }

    private String serializeResponse(KycVerifyResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize KycVerifyResponse to JSON, falling back to toString: {}", e.getMessage());
            return response.toString();
        }
    }
}
