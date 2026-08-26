package com.bagusxmahendra.mltf.supervisor_agent;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestSpannerConfig.class)
class KycIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private KycRepository kycRepository;

    private WebTestClient webTestClient;
    private Algorithm algorithm;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
        this.algorithm = Algorithm.HMAC256("integration-test-secret");
    }

    private String generateToken(String subject, Date expiresAt) {
        return JWT.create()
                .withSubject(subject)
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
}
