package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.LoanApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationControllerTest {

    @Mock
    private LoanApplicationService loanApplicationService;

    @Mock
    private Auth0JwtService auth0JwtService;

    @InjectMocks
    private LoanApplicationController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void create_withValidJwt_shouldReturnTransactionId() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(loanApplicationService.createMortgageLoan(userId))
                .thenReturn(Mono.just(new LoanApplicationResponse("TXN-123")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/application")
                        .queryParam("action", "create").build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.transactionId").isEqualTo("TXN-123");

        verify(auth0JwtService).extractUserId(authHeader);
        verify(loanApplicationService).createMortgageLoan(userId);
    }

    @Test
    void getApplications_withValidJwt_shouldReturnCustomerApplications() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(loanApplicationService.getApplications(userId)).thenReturn(Mono.just(List.of(
                new ApplicationSummaryResponse(
                        "TXN-123", "20 April 2026", "Financing of Property",
                        "Super Green Home", "RM 1,230,000", "Single Application", "In Review"
                )
        )));

        webTestClient.get()
                .uri("/api/v1/application")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].applicationReferenceNumber").isEqualTo("TXN-123")
                .jsonPath("$[0].dateApplied").isEqualTo("20 April 2026")
                .jsonPath("$[0].propertyProject").isEqualTo("Super Green Home")
                .jsonPath("$[0].propertyPrice").isEqualTo("RM 1,230,000")
                .jsonPath("$[0].applicationStatus").isEqualTo("In Review");

        verify(auth0JwtService).extractUserId(authHeader);
        verify(loanApplicationService).getApplications(userId);
    }
}