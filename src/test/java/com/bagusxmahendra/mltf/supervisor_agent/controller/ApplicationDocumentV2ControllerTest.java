package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.ApplicationDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentV2ControllerTest {

    @Mock
    private Auth0JwtService auth0JwtService;

    @Mock
    private ApplicationDocumentService applicationDocumentService;

    @InjectMocks
    private ApplicationDocumentV2Controller controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void uploadDocument_withValidJwtAndMultipartFile_shouldReturnSuccess() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        String applicationId = "TXN-123";
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("document", new ByteArrayResource("test-doc-content".getBytes()) {
            @Override
            public String getFilename() {
                return "salary_slip.pdf";
            }
        }).contentType(MediaType.APPLICATION_PDF);

        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(applicationDocumentService.uploadAndStoreWithoutAnalysis(eq(applicationId), eq(userId), any()))
                .thenReturn(Mono.just(Map.of("status", "success", "documentId", "DOC-123")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/application/document")
                        .queryParam("applicationID", applicationId).build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("success")
                .jsonPath("$.documentId").isEqualTo("DOC-123");

        verify(auth0JwtService).extractUserId(authHeader);
        verify(applicationDocumentService).uploadAndStoreWithoutAnalysis(eq(applicationId), eq(userId), any());
    }

    @Test
    void uploadDocument_withoutAuthorizationHeader_shouldReturnUnauthorized() {
        String applicationId = "TXN-123";
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("document", new ByteArrayResource("test-doc-content".getBytes()) {
            @Override
            public String getFilename() {
                return "salary_slip.pdf";
            }
        }).contentType(MediaType.APPLICATION_PDF);

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/application/document")
                        .queryParam("applicationID", applicationId).build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isUnauthorized();

        verify(auth0JwtService, never()).extractUserId(any());
        verify(applicationDocumentService, never()).uploadAndStoreWithoutAnalysis(any(), any(), any());
    }

    @Test
    void uploadDocument_withInvalidApplication_shouldReturnNotFound() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        String applicationId = "TXN-INVALID";
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("document", new ByteArrayResource("test-doc-content".getBytes()) {
            @Override
            public String getFilename() {
                return "salary_slip.pdf";
            }
        }).contentType(MediaType.APPLICATION_PDF);

        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(applicationDocumentService.uploadAndStoreWithoutAnalysis(eq(applicationId), eq(userId), any()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found for customer")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/application/document")
                        .queryParam("applicationID", applicationId).build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isNotFound();

        verify(auth0JwtService).extractUserId(authHeader);
        verify(applicationDocumentService).uploadAndStoreWithoutAnalysis(eq(applicationId), eq(userId), any());
    }
}
