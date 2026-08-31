package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingRequestDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for communicating with the Forensic Document Processing Agent microservice (/api/v1/doc/processing).
 */
@Component
public class DocumentProcessingClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingClient.class);

    private final WebClient webClient;
    private final SupervisorAgentProperties properties;

    public DocumentProcessingClient(WebClient.Builder webClientBuilder, SupervisorAgentProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getDocumentProcessingUrl())
                .build();
    }

    /**
     * Processes document for forensic tampering, OCR, and key-value extraction.
     */
    public Mono<DocProcessingResponseDto> processDocument(String gcsUrl, String mimeType, String customPrompt) {
        DocProcessingRequestDto request = new DocProcessingRequestDto(gcsUrl, mimeType, "IDENTITY_DOCUMENT", customPrompt);
        log.info("Calling Document Processing Agent at {}/api/v1/doc/processing for GCS URL: {}",
                properties.getDocumentProcessingUrl(), gcsUrl);

        return webClient.post()
                .uri("/api/v1/doc/processing")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DocProcessingResponseDto.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .doOnSuccess(res -> log.info("Received document processing response: status={}, detectedType={}",
                        res.getStatus(), res.getDetectedDocumentType()))
                .onErrorResume(err -> {
                    log.warn("Failed to reach Document Processing Agent ({}), generating resilient fallback inspection", err.getMessage());
                    return Mono.just(createFallbackResponse(gcsUrl, mimeType, err));
                });
    }

    private DocProcessingResponseDto createFallbackResponse(String gcsUrl, String mimeType, Throwable err) {
        DocProcessingResponseDto res = new DocProcessingResponseDto();
        res.setStatus("IN_REVIEW");
        String errorMsg = err != null && err.getMessage() != null ? err.getMessage() : "Service unavailable";
        res.setMessage("Failed to reach Document Processing Agent (/api/v1/doc/processing): " + errorMsg);
        res.setGcsUrl(gcsUrl);
        res.setDetectedDocumentType("IDENTITY_DOCUMENT");

        Map<String, Object> pixelCheck = new LinkedHashMap<>();
        pixelCheck.put("isTampered", false);
        pixelCheck.put("tamperingRiskLevel", "UNKNOWN");
        pixelCheck.put("tamperingConfidence", 0.0);
        pixelCheck.put("findings", "Document processing API unreachable. Inspection could not be completed automatically.");
        pixelCheck.put("anomalies", Collections.emptyList());
        res.setPixelLevelCheck(pixelCheck);

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("documentScore", 50.0);
        scores.put("originalityScore", 50.0);
        scores.put("confidenceScore", 50.0);
        scores.put("scoringBreakdown", "Automated scoring unavailable due to service unreachability");
        res.setScores(scores);

        return res;
    }
}
