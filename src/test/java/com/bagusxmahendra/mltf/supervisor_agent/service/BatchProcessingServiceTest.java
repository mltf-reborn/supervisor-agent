package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.GraphProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisResult;
import com.bagusxmahendra.mltf.supervisor_agent.model.DocumentRecord;
import com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchProcessingServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private ApplicationDocumentRepository applicationDocumentRepository;

    @Mock
    private DocumentProcessingClient documentProcessingClient;

    @Mock
    private GraphProcessingClient graphProcessingClient;

    private BatchProcessingService service;

    @BeforeEach
    void setUp() {
        service = new BatchProcessingService(
                loanApplicationRepository,
                applicationDocumentRepository,
                documentProcessingClient,
                graphProcessingClient,
                new ObjectMapper()
        );
    }

    @Test
    void processSubmittedApplications_whenNoSubmittedApplications_shouldReturnZeroProcessed() {
        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(Collections.emptyList()));

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo("SUCCESS");
                    assertThat(response.getMessage()).isEqualTo("No transactions found for processing");
                    assertThat(response.getTotalProcessed()).isEqualTo(0);
                    assertThat(response.getResults()).isEmpty();
                })
                .verifyComplete();

        verify(loanApplicationRepository).findApplicationsByStatus("SUBMITTED");
        verify(applicationDocumentRepository, never()).findByTransactionId(any());
        verify(graphProcessingClient, never()).analyzeGraph(any());
    }

    @Test
    void processSubmittedApplications_whenApplicationHasNoDocuments_shouldRejectWithoutGraphAnalysis() {
        SubmittedApplication app = new SubmittedApplication("TXN-101", "usr_101", "Single Application", "SUBMITTED");
        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-101"))
                .thenReturn(Mono.just(Collections.emptyList()));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-101"), eq("REJECTED"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getMessage()).contains("No documents found");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNull();
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-101"), eq("REJECTED"), any());
        verify(graphProcessingClient, never()).analyzeGraph(any());
    }

    @Test
    void processSubmittedApplications_whenDocumentsPassAndGraphPasses_shouldApproveApplication() {
        SubmittedApplication app = new SubmittedApplication("TXN-201", "usr_201", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-201", "DOC-01", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("SUCCESS");
        docResponse.setMessage("Document analyzed successfully");
        docResponse.setExtractedFields(Map.of("netSalary", "14147.65", "companyName", "HOLYCOW SDN BHD"));

        DocumentRecord updatedDoc = new DocumentRecord(
                "TXN-201", "DOC-01", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "SUCCESS", "Document analyzed successfully", "{\"extractedFields\":{\"netSalary\":\"14147.65\",\"companyName\":\"HOLYCOW SDN BHD\"}}", Instant.now()
        );

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-201"))
                .thenReturn(Mono.just(List.of(doc)))
                .thenReturn(Mono.just(List.of(updatedDoc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-201"), eq("DOC-01"), eq("SUCCESS"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.getApplicationDetails("TXN-201", "usr_201"))
                .thenReturn(Mono.just(Map.of(
                        "application", Map.of("application_type", "Single Application"),
                        "applicant", Map.of("full_name", "Diana Prince")
                )));
        when(graphProcessingClient.analyzeGraph(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(new GraphAnalysisResult("APPROVED", "SALARY_TRIANGULATION", true, List.of())));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-201"), eq("APPROVED"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-201");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("APPROVED");
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(1);
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("SUCCESS");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNotNull();
                    assertThat(response.getResults().get(0).getGraphAnalysis().status()).isEqualTo("APPROVED");
                    assertThat(response.getResults().get(0).getGraphAnalysis().passed()).isTrue();
                    assertThat(response.getResults().get(0).getCaseId()).isNull();
                })
                .verifyComplete();

        ArgumentCaptor<String> aiAnalysisCaptor = ArgumentCaptor.forClass(String.class);
        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-201"), eq("APPROVED"), aiAnalysisCaptor.capture());
        assertThat(aiAnalysisCaptor.getValue()).contains("\"graphAnalysis\"");
        assertThat(aiAnalysisCaptor.getValue()).contains("\"documents\"");
        assertThat(aiAnalysisCaptor.getValue()).contains("SALARY_TRIANGULATION");

        ArgumentCaptor<GraphAnalysisRequest> graphReqCaptor = ArgumentCaptor.forClass(GraphAnalysisRequest.class);
        verify(graphProcessingClient).analyzeGraph(graphReqCaptor.capture());
        assertThat(graphReqCaptor.getValue().loanApplication().get("applicationId")).isEqualTo("TXN-201");
        assertThat(graphReqCaptor.getValue().loanApplication().get("applicantName")).isEqualTo("Diana Prince");
        assertThat(graphReqCaptor.getValue().documents()).hasSize(1);
        assertThat(graphReqCaptor.getValue().documents().get(0).documentType()).isEqualTo("PAYSLIP");
    }

    @Test
    void processSubmittedApplications_whenDocumentFailsOrIsTampered_shouldSkipGraphAnalysisAndRejectApplication() {
        SubmittedApplication app = new SubmittedApplication("TXN-301", "usr_301", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-301", "DOC-02", "id.pdf", "gs://bucket/id.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("SUCCESS");
        docResponse.setPixelLevelCheck(Map.of("isTampered", true));

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-301"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/id.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-301"), eq("DOC-02"), eq("FAILED"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-301"), eq("REJECTED"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-301");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("FAILED");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNull();
                    assertThat(response.getResults().get(0).getCaseId()).isNull();
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-301"), eq("REJECTED"), any());
        verify(graphProcessingClient, never()).analyzeGraph(any());
    }

    @Test
    void processSubmittedApplications_whenDocumentInReview_shouldStillCallGraphAnalysisAndSetInReview() {
        SubmittedApplication app = new SubmittedApplication("TXN-401", "usr_401", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-401", "DOC-03", "passport.pdf", "gs://bucket/passport.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("IN_REVIEW");
        docResponse.setMessage("Document quality ambiguous, requires manual inspection");

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-401"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/passport.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-401"), eq("DOC-03"), eq("IN_REVIEW"), eq("Document quality ambiguous, requires manual inspection"), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.getApplicationDetails("TXN-401", "usr_401"))
                .thenReturn(Mono.just(Map.of("application", Map.of("id", "TXN-401"))));
        when(graphProcessingClient.analyzeGraph(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(new GraphAnalysisResult("APPROVED", "SALARY_TRIANGULATION", true, List.of())));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-401"), eq("IN_REVIEW"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-401");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getCaseId()).isNull();
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(1);
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getMessage()).isEqualTo("Document quality ambiguous, requires manual inspection");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNotNull();
                    assertThat(response.getResults().get(0).getGraphAnalysis().status()).isEqualTo("APPROVED");
                })
                .verifyComplete();

        verify(graphProcessingClient).analyzeGraph(any());
        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-401"), eq("IN_REVIEW"), any());
    }

    @Test
    void processSubmittedApplications_whenGraphAnalysisFlagged_shouldSetApplicationToInReview() {
        SubmittedApplication app = new SubmittedApplication("TXN-501", "usr_501", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-501", "DOC-05", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("SUCCESS");

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-501"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-501"), eq("DOC-05"), eq("SUCCESS"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.getApplicationDetails("TXN-501", "usr_501"))
                .thenReturn(Mono.just(Map.of("applicant", Map.of("full_name", "John Doe"))));
        when(graphProcessingClient.analyzeGraph(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(new GraphAnalysisResult("FLAGGED", "SALARY_TRIANGULATION", false, List.of("Salary variance exceeded 5%"))));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-501"), eq("IN_REVIEW"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getMessage()).contains("Salary variance exceeded 5%");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNotNull();
                    assertThat(response.getResults().get(0).getGraphAnalysis().status()).isEqualTo("FLAGGED");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-501"), eq("IN_REVIEW"), any());
    }

    @Test
    void processSubmittedApplications_whenGraphAnalysisRejected_shouldRejectApplication() {
        SubmittedApplication app = new SubmittedApplication("TXN-601", "usr_601", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-601", "DOC-06", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("SUCCESS");

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-601"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-601"), eq("DOC-06"), eq("SUCCESS"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.getApplicationDetails("TXN-601", "usr_601"))
                .thenReturn(Mono.just(Map.of("applicant", Map.of("full_name", "John Doe"))));
        when(graphProcessingClient.analyzeGraph(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(new GraphAnalysisResult("REJECTED", "SALARY_TRIANGULATION", false, List.of("Identity mismatch detected"))));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-601"), eq("REJECTED"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getMessage()).contains("Identity mismatch detected");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNotNull();
                    assertThat(response.getResults().get(0).getGraphAnalysis().status()).isEqualTo("REJECTED");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-601"), eq("REJECTED"), any());
    }

    @Test
    void processSubmittedApplications_whenCallingDocProcessingApiFailsWithException_shouldMarkDocInReviewAndInvokeGraphAnalysis() {
        SubmittedApplication app = new SubmittedApplication("TXN-501", "usr_501", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-501", "DOC-05", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-501"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.error(new RuntimeException("503 Service Unavailable: /api/v1/doc/processing")));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-501"), eq("DOC-05"), eq("IN_REVIEW"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.getApplicationDetails("TXN-501", "usr_501"))
                .thenReturn(Mono.just(Map.of()));
        when(graphProcessingClient.analyzeGraph(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(new GraphAnalysisResult("APPROVED", "SALARY_TRIANGULATION", true, List.of())));
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-501"), eq("IN_REVIEW"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-501");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getCaseId()).isNull();
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(1);
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getMessage()).contains("503 Service Unavailable");
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNotNull();
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-501"), eq("IN_REVIEW"), any());
    }

    @Test
    void processSubmittedApplications_whenMultipleDocs_oneRejectedOneInReview_shouldSkipGraphAndRejectLoanApplication() {
        SubmittedApplication app = new SubmittedApplication("TXN-701", "usr_701", "Multi-doc Application", "SUBMITTED");
        DocumentRecord doc1 = new DocumentRecord("TXN-701", "DOC-A", "id.pdf", "gs://bucket/id.pdf", "application/pdf", "PENDING", null, null, Instant.now());
        DocumentRecord doc2 = new DocumentRecord("TXN-701", "DOC-B", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf", "PENDING", null, null, Instant.now());

        DocProcessingResponseDto doc1Response = new DocProcessingResponseDto();
        doc1Response.setStatus("FAILED");
        doc1Response.setMessage("Forgery detected");

        DocProcessingResponseDto doc2Response = new DocProcessingResponseDto();
        doc2Response.setStatus("IN_REVIEW");
        doc2Response.setMessage("Needs manual check");

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-701"))
                .thenReturn(Mono.just(List.of(doc1, doc2)));
        when(documentProcessingClient.processDocument("gs://bucket/id.pdf", "application/pdf", null))
                .thenReturn(Mono.just(doc1Response));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.just(doc2Response));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-701"), eq("DOC-A"), eq("FAILED"), eq("Forgery detected"), any()))
                .thenReturn(Mono.empty());
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-701"), eq("DOC-B"), eq("IN_REVIEW"), eq("Needs manual check"), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.updateStatusAndAiAnalysis(eq("TXN-701"), eq("REJECTED"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(2);
                    assertThat(response.getResults().get(0).getGraphAnalysis()).isNull();
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatusAndAiAnalysis(eq("TXN-701"), eq("REJECTED"), any());
        verify(graphProcessingClient, never()).analyzeGraph(any());
    }
}
