package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycControllerTest {

    @Mock
    private KycService kycService;

    @Mock
    private Auth0JwtService auth0JwtService;

    @InjectMocks
    private KycController kycController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(kycController).build();
    }

    @Test
    void getStatus_withValidJwt_shouldReturnOkAndStatus() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        KycStatusResponse response = new KycStatusResponse(KycStatus.APPROVED);

        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(kycService.getStatus(userId)).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.userId").doesNotExist()
                .jsonPath("$.fullName").doesNotExist()
                .jsonPath("$.email").doesNotExist();

        verify(auth0JwtService).extractUserId(authHeader);
        verify(kycService).getStatus(userId);
    }

    @Test
    void getStatus_withoutAuthorizationHeader_shouldReturnUnauthorized() {
        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getStatus_withInvalidToken_shouldReturnUnauthorized() {
        String authHeader = "Bearer invalid.token";
        when(auth0JwtService.extractUserId(authHeader)).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token")
        );

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getStatus_whenUserNotFoundInDb_shouldReturnNotFound() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_non_existent";

        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(kycService.getStatus(userId)).thenReturn(
                Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC profile not found"))
        );

        webTestClient.get()
                .uri("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
