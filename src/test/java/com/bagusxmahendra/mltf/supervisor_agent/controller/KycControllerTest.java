package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycControllerTest {

    @Mock
    private KycService kycService;

    @InjectMocks
    private KycController kycController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(kycController).build();
    }

    @Test
    void getStatus_withUserIdQueryParam_shouldReturnOkAndStatus() {
        KycStatusResponse response = new KycStatusResponse(
                "usr_1001",
                KycStatus.APPROVED,
                "John Doe",
                "john.doe@example.com",
                "+1-555-0199",
                "NATIONAL_ID",
                12.5,
                "LOW",
                null,
                "Verified",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z")
        );

        when(kycService.getStatus("usr_1001")).thenReturn(Mono.just(response));

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
                .jsonPath("$.email").isEqualTo("john.doe@example.com")
                .jsonPath("$.riskScore").isEqualTo(12.5)
                .jsonPath("$.riskLevel").isEqualTo("LOW");
    }

    @Test
    void getStatus_withUserIdPathParam_shouldReturnOkAndStatus() {
        KycStatusResponse response = new KycStatusResponse(
                "usr_1002",
                KycStatus.IN_REVIEW,
                "Jane Smith",
                "jane.smith@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(kycService.getStatus("usr_1002")).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/api/v1/kyc/status/usr_1002")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("usr_1002")
                .jsonPath("$.status").isEqualTo("IN_REVIEW")
                .jsonPath("$.fullName").isEqualTo("Jane Smith");
    }

    @Test
    void getStatus_withEmailQueryParam_shouldReturnOkAndStatus() {
        KycStatusResponse response = new KycStatusResponse(
                "usr_1001",
                KycStatus.APPROVED,
                "John Doe",
                "john.doe@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(kycService.getStatusByEmail("john.doe@example.com")).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/api/v1/kyc/status?email=john.doe@example.com")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("usr_1001")
                .jsonPath("$.email").isEqualTo("john.doe@example.com");
    }

    @Test
    void getStatus_withoutParams_shouldReturnBadRequest() {
        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getStatus_whenUserNotFound_shouldReturnNotFound() {
        when(kycService.getStatus("non_existent")).thenReturn(
                Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC profile not found"))
        );

        webTestClient.get()
                .uri("/api/v1/kyc/status?userId=non_existent")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
