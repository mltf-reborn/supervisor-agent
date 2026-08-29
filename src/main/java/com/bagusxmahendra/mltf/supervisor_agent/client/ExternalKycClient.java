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
 * Uses JSON dataset (/data/mock-external-kyc.json) for mock verification.
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

    public synchronized void loadMockData() {
        try (InputStream is = getClass().getResourceAsStream(MOCK_DATA_PATH)) {
            if (is != null) {
                this.mockKycRecords = objectMapper.readValue(is, new TypeReference<List<ExternalKycResponse>>() {});
                log.info("Loaded {} mock External KYC records from JSON: {}", mockKycRecords.size(), MOCK_DATA_PATH);
            } else {
                log.warn("Mock data file {} not found on classpath", MOCK_DATA_PATH);
                this.mockKycRecords = Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to load mock data from {} ({}): no mock records loaded", MOCK_DATA_PATH, e.getMessage());
            this.mockKycRecords = Collections.emptyList();
        }
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
                    log.warn("External KYC API endpoint not responding or unavailable ({}), generating mock result from JSON dataset", err.getMessage());
                    return Mono.just(generateMockKycData(idNumber, fullName, dateOfBirth, nationality));
                });
    }

    /**
     * Generates simulated mock KYC data strictly using the JSON dataset (/data/mock-external-kyc.json).
     *
     * Rules:
     * 1. Inquiry is performed using IDNumber against the JSON dataset.
     * 2. If IDNumber is not found in the JSON dataset -> status IN_REVIEW (registryStatus: NOT_FOUND).
     * 3. If IDNumber is matched:
     *    a. If record is SUSPICIOUS/blacklisted -> return SUSPICIOUS record as configured in JSON.
     *    b. Compare the inquiry full name with the record full name:
     *       - Must be EXACTLY the same (case-insensitive, normalized whitespace).
     *       - If NOT same -> status IN_REVIEW (registryStatus: NAME_MISMATCH).
     *       - If EXACTLY THE SAME -> return verified record (status from JSON / SUCCESS).
     */
    public ExternalKycResponse generateMockKycData(
            String idNumber,
            String fullName,
            String dateOfBirth,
            String nationality
    ) {
        String trimmedId = (idNumber != null && !idNumber.isBlank()) ? idNumber.trim() : null;
        String trimmedName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : null;

        // 1. Inquiry requires IDNumber
        if (trimmedId == null) {
            log.warn("External KYC inquiry ID number is missing -> status IN_REVIEW");
            return ExternalKycResponse.notFound(null, trimmedName);
        }

        // 2. Search record in JSON dataset by IDNumber (exact or normalized without hyphens)
        ExternalKycResponse matchedRecord = null;
        for (ExternalKycResponse record : mockKycRecords) {
            if (record.getIdNumber() != null) {
                String recId = record.getIdNumber().trim();
                if (recId.equalsIgnoreCase(trimmedId) ||
                    recId.replace("-", "").equalsIgnoreCase(trimmedId.replace("-", ""))) {
                    matchedRecord = record;
                    break;
                }
            }
        }

        // 3. If ID not found in JSON dataset -> status IN_REVIEW
        if (matchedRecord == null) {
            log.info("External KYC inquiry ID [{}] not found in JSON dataset -> status IN_REVIEW", trimmedId);
            return ExternalKycResponse.notFound(trimmedId, trimmedName);
        }

        // If the matched JSON record is flagged as SUSPICIOUS or blacklisted
        if (Boolean.TRUE.equals(matchedRecord.getIsBlacklisted()) || "SUSPICIOUS".equalsIgnoreCase(matchedRecord.getStatus())) {
            log.info("External KYC inquiry ID [{}] matched blacklisted/suspicious record in JSON -> {}", trimmedId, matchedRecord.getStatus());
            return cloneResponse(matchedRecord);
        }

        // 4. If ID matched, compare the full name: must be exactly the same
        String normInputName = (trimmedName != null) ? trimmedName.replaceAll("\\s+", " ").trim() : "";
        String normRecordName = (matchedRecord.getFullName() != null) ? matchedRecord.getFullName().replaceAll("\\s+", " ").trim() : "";

        boolean isExactSameName = !normInputName.isEmpty() && normInputName.equalsIgnoreCase(normRecordName);

        if (!isExactSameName) {
            log.info("External KYC inquiry ID [{}] matched, but input name [{}] does not match JSON registry name [{}] -> status IN_REVIEW",
                    trimmedId, trimmedName, matchedRecord.getFullName());
            return ExternalKycResponse.nameMismatch(trimmedId, trimmedName, matchedRecord.getFullName());
        }

        // 5. ID matched and Name is exactly the same -> Return verified record from JSON
        log.info("External KYC inquiry ID [{}] and name [{}] verified successfully from JSON record",
                trimmedId, matchedRecord.getFullName());
        return cloneResponse(matchedRecord);
    }

    private ExternalKycResponse cloneResponse(ExternalKycResponse source) {
        ExternalKycResponse copy = new ExternalKycResponse();
        copy.setStatus(source.getStatus());
        copy.setMessage(source.getMessage());
        copy.setIdNumber(source.getIdNumber());
        copy.setFullName(source.getFullName());
        copy.setDateOfBirth(source.getDateOfBirth());
        copy.setNationality(source.getNationality());
        copy.setOccupation(source.getOccupation());
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

    public void setMockKycRecords(List<ExternalKycResponse> records) {
        this.mockKycRecords = records != null ? new ArrayList<>(records) : new ArrayList<>();
    }
}
