package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchDocumentItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessResponse;
import com.bagusxmahendra.mltf.supervisor_agent.service.BatchProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchProcessingControllerTest {

    @Mock
    private BatchProcessingService batchProcessingService;

    @InjectMocks
    private BatchProcessingController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void processBatch_post_whenNoTransactions_shouldReturnSuccessZeroProcessed() {
        when(batchProcessingService.processSubmittedApplications())
                .thenReturn(Mono.just(BatchProcessResponse.noTransactionsFound()));

        webTestClient.post()
                .uri("/api/v1/batch/process")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.message").isEqualTo("No transactions found for processing")
                .jsonPath("$.totalProcessed").isEqualTo(0)
                .jsonPath("$.results").isEmpty();

        verify(batchProcessingService).processSubmittedApplications();
    }

    @Test
    void processBatch_post_whenTransactionsProcessed_shouldReturnDetailedResults() {
        BatchProcessItemResponse item = new BatchProcessItemResponse(
                "TXN-123",
                "usr_1001",
                "SUBMITTED",
                "APPROVED",
                "Application verified and approved successfully",
                null,
                List.of(new BatchDocumentItemResponse("DOC-1", "payslip.pdf", "SUCCESS", "Document verified successfully"))
        );
        BatchProcessResponse response = new BatchProcessResponse(
                "SUCCESS",
                "Processed 1 SUBMITTED application(s)",
                1,
                List.of(item),
                Instant.now()
        );

        when(batchProcessingService.processSubmittedApplications())
                .thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/api/v1/batch/process")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.totalProcessed").isEqualTo(1)
                .jsonPath("$.results[0].transactionId").isEqualTo("TXN-123")
                .jsonPath("$.results[0].previousStatus").isEqualTo("SUBMITTED")
                .jsonPath("$.results[0].finalStatus").isEqualTo("APPROVED")
                .jsonPath("$.results[0].documents[0].filename").isEqualTo("payslip.pdf")
                .jsonPath("$.results[0].documents[0].status").isEqualTo("SUCCESS");

        verify(batchProcessingService).processSubmittedApplications();
    }

    @Test
    void processBatch_get_shouldAlsoWork() {
        when(batchProcessingService.processSubmittedApplications())
                .thenReturn(Mono.just(BatchProcessResponse.noTransactionsFound()));

        webTestClient.get()
                .uri("/api/v1/batch/process")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.totalProcessed").isEqualTo(0);

        verify(batchProcessingService).processSubmittedApplications();
    }
}
