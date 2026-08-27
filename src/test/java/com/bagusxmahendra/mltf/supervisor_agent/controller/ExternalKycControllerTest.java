package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalKycControllerTest {

    @Mock
    private ExternalKycClient externalKycClient;

    private ExternalKycController controller;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        controller = new ExternalKycController(externalKycClient);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void verifyExternalKycPost_withValidRequest_shouldReturnSuccess() {
        ExternalKycClient realClient = new ExternalKycClient(org.springframework.web.reactive.function.client.WebClient.builder(), new com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties());
        when(externalKycClient.generateMockKycData(any(), any(), any(), any()))
                .thenReturn(realClient.generateMockKycData("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian"));

        ExternalKycRequest request = new ExternalKycRequest("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.idNumber").isEqualTo("940822-10-5819")
                .jsonPath("$.fullName").isEqualTo("AHMAD SYAZWAN")
                .jsonPath("$.isIdentityVerified").isEqualTo(true)
                .jsonPath("$.isBlacklisted").isEqualTo(false)
                .jsonPath("$.amlSanctionsStatus").isEqualTo("PASS");
    }

    @Test
    void verifyExternalKycGet_withQueryParams_shouldReturnSuccess() {
        ExternalKycClient realClient = new ExternalKycClient(org.springframework.web.reactive.function.client.WebClient.builder(), new com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties());
        when(externalKycClient.generateMockKycData(any(), any(), any(), any()))
                .thenReturn(realClient.generateMockKycData("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian"));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/external/kyc")
                        .queryParam("idNumber", "940822-10-5819")
                        .queryParam("fullName", "AHMAD SYAZWAN")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.isIdentityVerified").isEqualTo(true);
    }

    @Test
    void verifyExternalKycPost_withFraudTrigger_shouldReturnSuspicious() {
        ExternalKycClient realClient = new ExternalKycClient(org.springframework.web.reactive.function.client.WebClient.builder(), new com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties());
        when(externalKycClient.generateMockKycData(any(), any(), any(), any()))
                .thenReturn(realClient.generateMockKycData("FRAUD-12345", "Fake Person", "1990-01-01", "Malaysian"));

        ExternalKycRequest request = new ExternalKycRequest("FRAUD-12345", "Fake Person", "1990-01-01", "Malaysian");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUSPICIOUS")
                .jsonPath("$.isBlacklisted").isEqualTo(true)
                .jsonPath("$.amlSanctionsStatus").isEqualTo("HIT");
    }
}
