package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for communicating with the External KYC/AML Verification endpoint (/api/v1/external/kyc).
 */
@Component
public class ExternalKycClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalKycClient.class);
    private static final String MOCK_DATA_PATH = "/data/mock-external-kyc.json";

    private final WebClient webClient;
    private final SupervisorAgentProperties properties;
    private final ObjectMapper objectMapper;
    private List<ExternalKycResponse> mockKycRecords = new ArrayList<>();

    public ExternalKycClient(WebClient.Builder webClientBuilder, SupervisorAgentProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getExternalKycUrl())
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        loadMockData();
    }

    private void loadMockData() {
        try (InputStream is = getClass().getResourceAsStream(MOCK_DATA_PATH)) {
            if (is != null) {
                this.mockKycRecords = objectMapper.readValue(is, new TypeReference<List<ExternalKycResponse>>() {});
                log.info("Loaded {} mock External KYC records from {}", mockKycRecords.size(), MOCK_DATA_PATH);
            } else {
                log.warn("Mock data file {} not found on classpath, using built-in defaults", MOCK_DATA_PATH);
                this.mockKycRecords = createDefaultMockRecords();
            }
        } catch (Exception e) {
            log.warn("Failed to load mock data from {} ({}): using built-in defaults", MOCK_DATA_PATH, e.getMessage());
            this.mockKycRecords = createDefaultMockRecords();
        }
    }

    private List<ExternalKycResponse> createDefaultMockRecords() {
        List<ExternalKycResponse> records = new ArrayList<>();
        records.add(ExternalKycResponse.verified("940822-10-5819", "AHMAD SYAZWAN BIN ABDULLAH", "1994-08-22", "Malaysian"));
        records.add(ExternalKycResponse.verified("880512-14-5123", "JOHN DOE", "1988-05-12", "American"));
        records.add(ExternalKycResponse.suspicious("FRAUD-12345", "ROBERT JOHNSON", "National registry blacklist match: High risk fraud flagged by financial intelligence unit."));
        return records;
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
     * Generates realistic simulated mock KYC data based on input characteristics and loaded JSON dataset.
     */
    public ExternalKycResponse generateMockKycData(
            String idNumber,
            String fullName,
            String dateOfBirth,
            String nationality
    ) {
        String trimmedId = (idNumber != null && !idNumber.isBlank()) ? idNumber.trim() : null;
        String trimmedName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : null;
        String trimmedDob = (dateOfBirth != null && !dateOfBirth.isBlank()) ? dateOfBirth.trim() : null;
        String trimmedNat = (nationality != null && !nationality.isBlank()) ? nationality.trim() : null;

        // Check for fraud/blacklist simulation triggers
        boolean isFraudTrigger = false;
        if (trimmedId != null) {
            String upper = trimmedId.toUpperCase();
            if (upper.contains("FRAUD") || upper.contains("BLACKLIST") || upper.contains("SCAM")) {
                isFraudTrigger = true;
            }
        }
        if (trimmedName != null) {
            String upper = trimmedName.toUpperCase();
            if (upper.contains("FRAUD") || upper.contains("BLACKLIST") || upper.contains("SCAM")) {
                isFraudTrigger = true;
            }
        }

        if (isFraudTrigger) {
            ExternalKycResponse suspiciousTemplate = mockKycRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsBlacklisted()) || "SUSPICIOUS".equalsIgnoreCase(r.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (suspiciousTemplate != null) {
                return cloneResponse(suspiciousTemplate, trimmedId, trimmedName, trimmedDob, trimmedNat);
            }
            return ExternalKycResponse.suspicious(
                    trimmedId != null ? trimmedId : "FRAUD-12345",
                    trimmedName != null ? trimmedName : "Fake Person",
                    "National registry blacklist match: High risk fraud flagged by financial intelligence unit."
            );
        }

        // 1. Try exact or normalized match by ID Number
        if (trimmedId != null) {
            for (ExternalKycResponse record : mockKycRecords) {
                if (record.getIdNumber() != null && (
                        record.getIdNumber().equalsIgnoreCase(trimmedId) ||
                        record.getIdNumber().replace("-", "").equalsIgnoreCase(trimmedId.replace("-", ""))
                )) {
                    return cloneResponse(record, trimmedId, trimmedName, trimmedDob, trimmedNat);
                }
            }
        }

        // 2. Try match by Full Name
        if (trimmedName != null) {
            for (ExternalKycResponse record : mockKycRecords) {
                if (record.getFullName() != null && (
                        record.getFullName().equalsIgnoreCase(trimmedName) ||
                        record.getFullName().toUpperCase().contains(trimmedName.toUpperCase()) ||
                        trimmedName.toUpperCase().contains(record.getFullName().toUpperCase())
                )) {
                    return cloneResponse(record, trimmedId, trimmedName, trimmedDob, trimmedNat);
                }
            }
        }

        // 3. Fallback: Use the first verified record from mock data as template, or default
        ExternalKycResponse defaultTemplate = mockKycRecords.stream()
                .filter(r -> Boolean.FALSE.equals(r.getIsBlacklisted()) && "SUCCESS".equalsIgnoreCase(r.getStatus()))
                .findFirst()
                .orElse(null);

        if (defaultTemplate != null) {
            return cloneResponse(defaultTemplate, trimmedId, trimmedName, trimmedDob, trimmedNat);
        }

        String effectiveId = trimmedId != null ? trimmedId : "940822-10-5819";
        String effectiveName = trimmedName != null ? trimmedName : "AHMAD SYAZWAN BIN ABDULLAH";
        String effectiveDob = trimmedDob != null ? trimmedDob : "1994-08-22";
        String effectiveNat = trimmedNat != null ? trimmedNat : "Malaysian";
        return ExternalKycResponse.verified(effectiveId, effectiveName, effectiveDob, effectiveNat);
    }

    private ExternalKycResponse cloneResponse(
            ExternalKycResponse source,
            String overrideId,
            String overrideName,
            String overrideDob,
            String overrideNat
    ) {
        ExternalKycResponse copy = new ExternalKycResponse();
        copy.setStatus(source.getStatus());
        copy.setMessage(source.getMessage());
        copy.setIdNumber(overrideId != null ? overrideId : source.getIdNumber());
        copy.setFullName(overrideName != null ? overrideName : source.getFullName());
        copy.setDateOfBirth(overrideDob != null ? overrideDob : source.getDateOfBirth());
        copy.setNationality(overrideNat != null ? overrideNat : (source.getNationality() != null ? source.getNationality() : "Malaysian"));
        copy.setRegistryStatus(source.getRegistryStatus());
        copy.setIsIdentityVerified(source.getIsIdentityVerified());
        copy.setIsBlacklisted(source.getIsBlacklisted());
        copy.setAmlSanctionsStatus(source.getAmlSanctionsStatus());
        copy.setPepStatus(source.getPepStatus());
        copy.setCreditScore(source.getCreditScore());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setRiskScore(source.getRiskScore());
        copy.setRemarks(source.getRemarks());
        copy.setFlags(source.getFlags() != null ? new ArrayList<>(source.getFlags()) : Collections.emptyList());
        copy.setCheckedAt(Instant.now());
        return copy;
    }

    public List<ExternalKycResponse> getMockKycRecords() {
        return Collections.unmodifiableList(mockKycRecords);
    }
}
