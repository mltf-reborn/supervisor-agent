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
            log.warn("createCase called with null request");
            return Mono.error(new IllegalArgumentException("CreateCaseRequest cannot be null"));
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
                        res.caseId(), res.caseStatus(), res.assignedTo()));
    }
}
