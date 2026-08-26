package com.bagusxmahendra.mltf.supervisor_agent;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestSpannerConfig.class)
class KycIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private KycRepository kycRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void getKycStatus_byUserIdQueryParam_shouldReturnOkAndStatus() {
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
                .uri("/api/v1/kyc/status?userId=usr_1001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.userId").isEqualTo("usr_1001")
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.fullName").isEqualTo("John Doe")
                .jsonPath("$.email").isEqualTo("john.doe@example.com");
    }

    @Test
    void getKycStatus_byUserIdPathParam_shouldReturnOkAndStatus() {
        KycProfile profile = new KycProfile(
                "usr_1002",
                "Jane Smith",
                "jane.smith@example.com",
                null, null, null, null, null, null, null, null, null, null, null,
                KycStatus.IN_REVIEW,
                35.0, "MEDIUM", null, null, null, null, null, null
        );

        when(kycRepository.findByUserId("usr_1002")).thenReturn(Mono.just(profile));

        webTestClient.get()
                .uri("/api/v1/kyc/status/usr_1002")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.userId").isEqualTo("usr_1002")
                .jsonPath("$.status").isEqualTo("IN_REVIEW");
    }

    @Test
    void getKycStatus_notFound_shouldReturn404() {
        when(kycRepository.findByUserId("usr_unknown")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/kyc/status?userId=usr_unknown")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
