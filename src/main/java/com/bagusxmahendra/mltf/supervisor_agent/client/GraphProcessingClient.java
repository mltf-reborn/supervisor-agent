package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Client for communicating with the Graph Processing Agent microservice (/api/v1/graph/analysis).
 * Dispatches loan application metadata and extracted document data for fraud triangulation.
 */
@Component
public class GraphProcessingClient {

    private static final Logger log = LoggerFactory.getLogger(GraphProcessingClient.class);

    private final WebClient webClient;
    private final SupervisorAgentProperties properties;

    public GraphProcessingClient(WebClient.Builder webClientBuilder, SupervisorAgentProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getGraphProcessingUrl())
                .build();
    }

    /**
     * Calls the Graph Analysis endpoint (POST /api/v1/graph/analysis).
     *
     * @param request the graph analysis request containing flat loanApplication and document extracted data
     * @return Mono of GraphAnalysisResult
     */
    public Mono<GraphAnalysisResult> analyzeGraph(GraphAnalysisRequest request) {
        if (request == null) {
            log.warn("analyzeGraph called with null request");
            return Mono.error(new IllegalArgumentException("GraphAnalysisRequest cannot be null"));
        }

        log.info("Calling Graph Processing Agent at {}/api/v1/graph/analysis", properties.getGraphProcessingUrl());

        return webClient.post()
                .uri("/api/v1/graph/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GraphAnalysisResult.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .doOnSuccess(res -> log.info("Received graph analysis response: status={}, checkName={}, passed={}, discrepancies={}",
                        res.status(), res.checkName(), res.passed(), res.discrepancies()))
                .onErrorResume(err -> {
                    log.warn("Failed to reach Graph Processing Agent ({}). Falling back to resilient FLAGGED inspection", err.getMessage());
                    return Mono.just(new GraphAnalysisResult(
                            GraphAnalysisResult.STATUS_FLAGGED,
                            "SALARY_TRIANGULATION",
                            false,
                            List.of("Failed to reach Graph Processing Agent: " + err.getMessage())
                    ));
                });
    }
}
