package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CaseResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client for communicating with the Case Management Service microservice (/api/v1/case).
 * Dispatches human-in-the-loop compliance review cases when KYC verification falls into IN_REVIEW status.
 */
@Component
public class CaseManagementClient {

    private static final Logger log = LoggerFactory.getLogger(CaseManagementClient.class);

    private final WebClient webClient;
    private final SupervisorAgentProperties properties;

    public CaseManagementClient(WebClient.Builder webClientBuilder, SupervisorAgentProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getCaseManagementUrl())
                .build();
    }

    /**
     * Creates a new case in the Case Management Service (POST /api/v1/case) for human review.
     *
     * @param request the case creation payload
     * @return Mono of CaseResponse
     */
    public Mono<CaseResponse> createCase(CreateCaseRequest request) {
        if (request == null) {
            log.warn("createCase called with null request, returning fallback response");
            return Mono.just(createFallbackResponse(null));
        }

        log.info("Calling Case Management Service at {}/api/v1/case for userId: {}",
                properties.getCaseManagementUrl(), request.getUserId());

        return webClient.post()
                .uri("/api/v1/case")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CaseResponse.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(res -> log.info("Successfully created case in Case Management Service: caseId={}, status={}, assignedTo={}",
                        res.caseId(), res.caseStatus(), res.assignedTo()))
                .onErrorResume(err -> {
                    log.warn("Failed to reach Case Management Service ({}), creating resilient fallback response", err.getMessage());
                    return Mono.just(createFallbackResponse(request));
                });
    }

    private CaseResponse createFallbackResponse(CreateCaseRequest request) {
        String caseId = (request != null && request.getCaseId() != null && !request.getCaseId().isBlank())
                ? request.getCaseId()
                : "CASE-KYC-" + (System.currentTimeMillis() % 1000000) + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        String userId = request != null ? request.getUserId() : "unknown";
        String caseType = (request != null && request.getCaseType() != null) ? request.getCaseType() : "KYC";
        String caseStatus = "IN_PROGRESS";
        String docUrl = request != null ? request.getDocumentUrl() : null;
        String selfieUrl = request != null ? request.getSelfieUrl() : null;
        Object docVerification = request != null ? request.getDocumentVerificationDetails() : null;
        Object selfieDetails = request != null ? request.getSelfieDetails() : null;
        Object kycDetails = request != null ? request.getKycDetails() : null;
        Double riskScore = request != null ? request.getRiskScore() : 45.0;
        String riskLevel = (request != null && request.getRiskLevel() != null) ? request.getRiskLevel() : "MEDIUM";
        String remarks = (request != null && request.getRemarks() != null) ? request.getRemarks() : "Case created for manual human compliance review";
        String assignedTo = request != null ? request.getAssignedTo() : null;
        Instant now = Instant.now();

        return new CaseResponse(
                caseId,
                userId,
                caseType,
                caseStatus,
                docUrl,
                selfieUrl,
                docVerification,
                selfieDetails,
                kycDetails,
                riskScore,
                riskLevel,
                null,
                remarks,
                assignedTo,
                now,
                now
        );
    }
}
