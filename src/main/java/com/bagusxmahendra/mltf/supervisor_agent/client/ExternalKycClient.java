package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for communicating with the External KYC/AML Verification endpoint (/api/v1/external/kyc).
 */
@Component
public class ExternalKycClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalKycClient.class);

    private final WebClient webClient;
    private final SupervisorAgentProperties properties;

    public ExternalKycClient(WebClient.Builder webClientBuilder, SupervisorAgentProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getExternalKycUrl())
                .build();
    }

    /**
     * Queries external KYC registry, AML sanctions, and PEP databases.
     */
    public Mono<ExternalKycResponse> fetchExternalKycData(
            String idNumber,
            String fullName,
            String dateOfBirth,
            String nationality
    ) {
        ExternalKycRequest request = new ExternalKycRequest(idNumber, fullName, dateOfBirth, nationality);
        log.info("Calling External KYC API at {}/api/v1/external/kyc for idNumber: {}, fullName: {}",
                properties.getExternalKycUrl(), idNumber, fullName);

        return webClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ExternalKycResponse.class)
                .timeout(Duration.ofSeconds(3))
                .doOnSuccess(res -> log.info("Received External KYC response: status={}, verified={}, riskLevel={}",
                        res.getStatus(), res.getIsIdentityVerified(), res.getRiskLevel()))
                .onErrorResume(err -> {
                    log.warn("External KYC API endpoint not responding or unavailable ({}), generating mock result", err.getMessage());
                    return Mono.just(generateMockKycData(idNumber, fullName, dateOfBirth, nationality));
                });
    }

    /**
     * Generates realistic simulated mock KYC data based on input characteristics.
     */
    public ExternalKycResponse generateMockKycData(
            String idNumber,
            String fullName,
            String dateOfBirth,
            String nationality
    ) {
        String effectiveId = idNumber != null && !idNumber.isBlank() ? idNumber.trim() : "940822-10-5819";
        String effectiveName = fullName != null && !fullName.isBlank() ? fullName.trim() : "AHMAD SYAZWAN BIN ABDULLAH";
        String effectiveDob = dateOfBirth != null && !dateOfBirth.isBlank() ? dateOfBirth.trim() : "1994-08-22";
        String effectiveNat = nationality != null && !nationality.isBlank() ? nationality.trim() : "Malaysian";

        // Check for fraud/blacklist simulation triggers
        String upperId = effectiveId.toUpperCase();
        String upperName = effectiveName.toUpperCase();
        if (upperId.contains("FRAUD") || upperId.contains("BLACKLIST") || upperName.contains("FRAUD") || upperName.contains("SCAM")) {
            return ExternalKycResponse.suspicious(effectiveId, effectiveName, "National registry blacklist match: High risk fraud flagged by financial intelligence unit.");
        }

        return ExternalKycResponse.verified(effectiveId, effectiveName, effectiveDob, effectiveNat);
    }
}
