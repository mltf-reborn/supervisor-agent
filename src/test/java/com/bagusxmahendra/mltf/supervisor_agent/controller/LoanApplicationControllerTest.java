package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.ApplicationDocumentService;
import com.bagusxmahendra.mltf.supervisor_agent.service.LoanApplicationService;
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
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationControllerTest {

    @Mock
    private LoanApplicationService loanApplicationService;

    @Mock
    private Auth0JwtService auth0JwtService;

    @Mock
    private ApplicationDocumentService applicationDocumentService;

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

    @Test
    void getAllApplicationsForOps_shouldReturnAllApplicationsWithDocumentsAndProcessingDetails() {
        java.util.Map<String, Object> doc = java.util.Map.of(
                "document_id", "DOC-001",
                "document_filename", "salary_slip.pdf",
                "document_status", "SUCCESS",
                "document_processing_details", "{\"grossSalary\": 5000}"
        );
        java.util.Map<String, Object> appItem = java.util.Map.of(
                "transaction_id", "TXN-123",
                "user_id", "usr_1001",
                "documents", List.of(doc)
        );
        when(loanApplicationService.getAllLoanApplications()).thenReturn(Mono.just(List.of(appItem)));

        webTestClient.get()
                .uri("/api/v1/application/all")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].transaction_id").isEqualTo("TXN-123")
                .jsonPath("$[0].documents[0].document_id").isEqualTo("DOC-001")
                .jsonPath("$[0].documents[0].document_processing_details").isEqualTo("{\"grossSalary\": 5000}");

        verify(loanApplicationService).getAllLoanApplications();
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
        when(applicationDocumentService.uploadAndProcess(eq(applicationId), eq(userId), any()))
                .thenReturn(Mono.just(new ApplicationDocumentResponse(
                        "salary_slip.pdf",
                        "DOC-123",
                        "SUCCESS",
                        "Document processed successfully"
                )));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/application/document")
                        .queryParam("applicationID", applicationId).build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.documentFilename").isEqualTo("salary_slip.pdf")
                .jsonPath("$.documentId").isEqualTo("DOC-123")
                .jsonPath("$.documentStatus").isEqualTo("SUCCESS");

        verify(auth0JwtService).extractUserId(authHeader);
        verify(applicationDocumentService).uploadAndProcess(eq(applicationId), eq(userId), any());
    }

    @Test
    void getApplicationStatus_withValidJwt_shouldReturnInquiryResponse() {
        String authHeader = "Bearer mock.jwt.token";
        String userId = "usr_1001";
        String applicationId = "TXN-123";

        when(auth0JwtService.extractUserId(authHeader)).thenReturn(userId);
        when(loanApplicationService.getApplicationInquiry(applicationId, userId))
                .thenReturn(Mono.just(new com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse(
                        applicationId,
                        "PROCESSING",
                        List.of(new com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentItem(
                                "DOC-123",
                                "salary_slip.pdf",
                                "SUCCESS",
                                "Document processed successfully"
                        ))
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/application/status")
                        .queryParam("applicationID", applicationId).build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.applicationID").isEqualTo("TXN-123")
                .jsonPath("$.status").isEqualTo("PROCESSING")
                .jsonPath("$.documents[0].id").isEqualTo("DOC-123")
                .jsonPath("$.documents[0].status").isEqualTo("SUCCESS");

        verify(auth0JwtService).extractUserId(authHeader);
        verify(loanApplicationService).getApplicationInquiry(applicationId, userId);
    }
}
