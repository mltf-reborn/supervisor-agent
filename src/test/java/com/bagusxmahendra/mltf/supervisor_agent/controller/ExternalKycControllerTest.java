package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

class ExternalKycControllerTest {

    private ExternalKycClient externalKycClient;
    private ExternalKycController controller;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        externalKycClient = new ExternalKycClient(WebClient.builder(), new SupervisorAgentProperties());
        controller = new ExternalKycController(externalKycClient);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void verifyExternalKycPost_withExactMatchRequest_shouldReturnSuccess() {
        ExternalKycRequest request = new ExternalKycRequest("940822-10-5819", "BAGUS MAHENDRA WICAKSONO", "1994-08-22", "Malaysian");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.idNumber").isEqualTo("940822-10-5819")
                .jsonPath("$.fullName").isEqualTo("BAGUS MAHENDRA WICAKSONO")
                .jsonPath("$.isIdentityVerified").isEqualTo(true)
                .jsonPath("$.isBlacklisted").isEqualTo(false)
                .jsonPath("$.amlSanctionsStatus").isEqualTo("PASS");
    }

    @Test
    void verifyExternalKycPost_whenNameMismatch_shouldReturnInReview() {
        ExternalKycRequest request = new ExternalKycRequest("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("IN_REVIEW")
                .jsonPath("$.registryStatus").isEqualTo("NAME_MISMATCH")
                .jsonPath("$.isIdentityVerified").isEqualTo(false);
    }

    @Test
    void verifyExternalKycPost_whenIdNotFound_shouldReturnInReview() {
        ExternalKycRequest request = new ExternalKycRequest("UNKNOWN-99999", "ANY NAME", "1994-08-22", "Malaysian");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("IN_REVIEW")
                .jsonPath("$.registryStatus").isEqualTo("NOT_FOUND")
                .jsonPath("$.isIdentityVerified").isEqualTo(false);
    }

    @Test
    void verifyExternalKycGet_withExactQueryParams_shouldReturnSuccess() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/external/kyc")
                        .queryParam("idNumber", "940822-10-5819")
                        .queryParam("fullName", "BAGUS MAHENDRA WICAKSONO")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.isIdentityVerified").isEqualTo(true);
    }

    @Test
    void verifyExternalKycPost_withFraudTrigger_shouldReturnSuspicious() {
        ExternalKycRequest request = new ExternalKycRequest("FRAUD-12345", "ROBERT JOHNSON", "1985-02-18", "Malaysian");

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

    @Test
    void verifyExternalKycPost_withSecondMockData_shouldReturnJohnDoeRecord() {
        ExternalKycRequest request = new ExternalKycRequest("880512-14-5123", "JOHN DOE", "1988-05-12", "American");

        webTestClient.post()
                .uri("/api/v1/external/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.idNumber").isEqualTo("880512-14-5123")
                .jsonPath("$.fullName").isEqualTo("JOHN DOE")
                .jsonPath("$.dateOfBirth").isEqualTo("1988-05-12")
                .jsonPath("$.isIdentityVerified").isEqualTo(true)
                .jsonPath("$.isBlacklisted").isEqualTo(false);
    }

    @Test
    void externalKycClient_shouldLoad3RecordsFromJson() {
        org.junit.jupiter.api.Assertions.assertEquals(3, externalKycClient.getMockKycRecords().size());
        org.junit.jupiter.api.Assertions.assertEquals("940822-10-5819", externalKycClient.getMockKycRecords().get(0).getIdNumber());
        org.junit.jupiter.api.Assertions.assertEquals("880512-14-5123", externalKycClient.getMockKycRecords().get(1).getIdNumber());
        org.junit.jupiter.api.Assertions.assertEquals("FRAUD-12345", externalKycClient.getMockKycRecords().get(2).getIdNumber());
    }
}
