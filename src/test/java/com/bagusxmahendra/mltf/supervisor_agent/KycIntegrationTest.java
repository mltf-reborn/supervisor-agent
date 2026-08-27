package com.bagusxmahendra.mltf.supervisor_agent;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestSpannerConfig.class)
class KycIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Storage storage;

    @MockitoBean
    private KycRepository kycRepository;

    @MockitoBean
    private com.bagusxmahendra.mltf.supervisor_agent.tools.KycSupervisorTools supervisorTools;

    private WebTestClient webTestClient;
    private Algorithm algorithm;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .configureClient()
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build();
        this.algorithm = Algorithm.HMAC256("integration-test-secret");
    }

    private String generateToken(String subject, Date expiresAt) {
        return JWT.create()
                .withSubject(subject)
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    @Test
    void getKycStatus_withValidAuth0Jwt_shouldReturnOkAndStatus() {
        String token = generateToken("usr_1001", Date.from(Instant.now().plusSeconds(3600)));
        KycProfile profile = new KycProfile(
                "usr_1001",
                "John Doe",
                "john.doe@example.com",
                "+1-555-0199",
                "ID-987654321",
                "NATIONAL_ID",
                LocalDate.of(1988, 5, 12),
                "123 Main St",
                "New York",
                "10001",
                "USA",
                "American",
                "Software Engineer",
                BigDecimal.valueOf(12500.00),
                KycStatus.APPROVED,
                12.5,
                "LOW",
                null,
                "Verified",
                "supervisor_01",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z")
        );

        when(kycRepository.findByUserId("usr_1001")).thenReturn(Mono.just(profile));

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.userId").doesNotExist()
                .jsonPath("$.fullName").doesNotExist()
                .jsonPath("$.email").doesNotExist()
                .jsonPath("$.phoneNumber").doesNotExist()
                .jsonPath("$.riskScore").doesNotExist();
    }

    @Test
    void getKycStatus_withoutAuthHeader_shouldReturn401() {
        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getKycStatus_withExpiredToken_shouldReturn401() {
        String expiredToken = generateToken("usr_1001", Date.from(Instant.now().minusSeconds(120)));

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getKycStatus_whenUserNotFoundInDb_shouldReturn404() {
        String token = generateToken("usr_unknown", Date.from(Instant.now().plusSeconds(3600)));
        when(kycRepository.findByUserId("usr_unknown")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void postKycVerify_withValidJwtAndFiles_shouldStoreInGcsAndReturnApproved() {
        String token = generateToken("usr_1001", Date.from(Instant.now().plusSeconds(3600)));

        Blob mockBlob = mock(Blob.class);
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mockBlob);
        when(kycRepository.save(any(KycProfile.class))).thenReturn(Mono.empty());

        java.util.Map<String, Object> docResult = new java.util.LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("detectedDocumentType", "NATIONAL_ID");
        docResult.put("scores", java.util.Map.of("documentScore", 95.0));
        docResult.put("pixelLevelCheck", java.util.Map.of("isTampered", false));
        docResult.put("extractedFields", java.util.Map.of("fullName", "Ahmad Syazwan", "idNumber", "940822-10-5819"));

        java.util.Map<String, Object> selfieResult = new java.util.LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 95.0);
        selfieResult.put("matchStatus", "MATCH");

        java.util.Map<String, Object> externalResult = new java.util.LinkedHashMap<>();
        externalResult.put("status", "SUCCESS");
        externalResult.put("isIdentityVerified", true);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("document", new ByteArrayResource("test-document-content".getBytes()) {
            @Override
            public String getFilename() {
                return "mykad.jpg";
            }
        }).contentType(MediaType.IMAGE_JPEG);
        builder.part("selfie", new ByteArrayResource("test-selfie-content".getBytes()) {
            @Override
            public String getFilename() {
                return "selfie.jpg";
            }
        }).contentType(MediaType.IMAGE_JPEG);
        builder.part("fullName", "Ahmad Syazwan");

        webTestClient.post()
                .uri("/api/v1/kyc/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.referenceId").isNotEmpty()
                .jsonPath("$.verifiedData.userId").isEqualTo("usr_1001")
                .jsonPath("$.verifiedData.fullName").isEqualTo("Ahmad Syazwan")
                .jsonPath("$.verifiedData.status").isEqualTo("APPROVED");

        org.mockito.ArgumentCaptor<KycProfile> profileCaptor = org.mockito.ArgumentCaptor.forClass(KycProfile.class);
        org.mockito.Mockito.verify(kycRepository).save(profileCaptor.capture());
        KycProfile saved = profileCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("john.doe@example.com", saved.email());
    }
}
