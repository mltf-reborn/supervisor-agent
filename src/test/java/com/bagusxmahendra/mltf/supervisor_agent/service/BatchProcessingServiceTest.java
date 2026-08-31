package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.client.CaseManagementClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CaseResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.model.DocumentRecord;
import com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private CaseManagementClient caseManagementClient;

    private BatchProcessingService service;

    @BeforeEach
    void setUp() {
        service = new BatchProcessingService(
                loanApplicationRepository,
                applicationDocumentRepository,
                documentProcessingClient,
                caseManagementClient,
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
    }

    @Test
    void processSubmittedApplications_whenApplicationHasNoDocuments_shouldReject() {
        SubmittedApplication app = new SubmittedApplication("TXN-101", "usr_101", "Single Application", "SUBMITTED");
        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-101"))
                .thenReturn(Mono.just(Collections.emptyList()));
        when(loanApplicationRepository.updateStatus("TXN-101", "REJECTED"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getMessage()).contains("No documents found");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatus("TXN-101", "REJECTED");
    }

    @Test
    void processSubmittedApplications_whenDocumentsPass_shouldApproveApplication() {
        SubmittedApplication app = new SubmittedApplication("TXN-201", "usr_201", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-201", "DOC-01", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("SUCCESS");
        docResponse.setMessage("Document analyzed successfully");

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-201"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-201"), eq("DOC-01"), eq("SUCCESS"), any(), any()))
                .thenReturn(Mono.empty());
        when(loanApplicationRepository.updateStatus("TXN-201", "APPROVED"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-201");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("APPROVED");
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(1);
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatus("TXN-201", "APPROVED");
        verify(caseManagementClient, never()).createCase(any());
    }

    @Test
    void processSubmittedApplications_whenDocumentFailsOrIsTampered_shouldRejectApplication() {
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
        when(loanApplicationRepository.updateStatus("TXN-301", "REJECTED"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-301");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("REJECTED");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("FAILED");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatus("TXN-301", "REJECTED");
        verify(caseManagementClient, never()).createCase(any());
    }

    @Test
    void processSubmittedApplications_whenDocumentInReview_shouldCreateCaseAndSetInReview() {
        SubmittedApplication app = new SubmittedApplication("TXN-401", "usr_401", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-401", "DOC-03", "passport.pdf", "gs://bucket/passport.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );
        DocProcessingResponseDto docResponse = new DocProcessingResponseDto();
        docResponse.setStatus("IN_REVIEW");
        docResponse.setMessage("Document quality ambiguous, requires manual inspection");

        CaseResponse caseResponse = new CaseResponse(
                "CASE-12345", "usr_401", "LOAN_APPLICATION", "IN_PROGRESS",
                "gs://bucket/passport.pdf", null, null, null, null,
                50.0, "MEDIUM", null, "Remarks", null, Instant.now(), Instant.now()
        );

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-401"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/passport.pdf", "application/pdf", null))
                .thenReturn(Mono.just(docResponse));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-401"), eq("DOC-03"), eq("IN_REVIEW"), any(), any()))
                .thenReturn(Mono.empty());
        when(caseManagementClient.createCase(any(CreateCaseRequest.class)))
                .thenReturn(Mono.just(caseResponse));
        when(loanApplicationRepository.updateStatus("TXN-401", "IN_REVIEW"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-401");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getCaseId()).isEqualTo("CASE-12345");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("IN_REVIEW");
                })
                .verifyComplete();

        org.mockito.ArgumentCaptor<CreateCaseRequest> captor = org.mockito.ArgumentCaptor.forClass(CreateCaseRequest.class);
        verify(caseManagementClient).createCase(captor.capture());
        assertThat(captor.getValue().getCaseType()).isEqualTo("LOAN_APPLICATION");
        verify(loanApplicationRepository).updateStatus("TXN-401", "IN_REVIEW");
    }

    @Test
    void processSubmittedApplications_whenCallingDocProcessingApiFailsWithException_shouldSetStatusInReviewAndCreateCase() {
        SubmittedApplication app = new SubmittedApplication("TXN-501", "usr_501", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-501", "DOC-05", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );

        CaseResponse caseResponse = new CaseResponse(
                "CASE-99999", "usr_501", "LOAN_APPLICATION", "IN_PROGRESS",
                "gs://bucket/payslip.pdf", null, null, null, null,
                50.0, "MEDIUM", null, "Remarks", null, Instant.now(), Instant.now()
        );

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-501"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.error(new RuntimeException("503 Service Unavailable: /api/v1/doc/processing")));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-501"), eq("DOC-05"), eq("IN_REVIEW"), any(), any()))
                .thenReturn(Mono.empty());
        when(caseManagementClient.createCase(any(CreateCaseRequest.class)))
                .thenReturn(Mono.just(caseResponse));
        when(loanApplicationRepository.updateStatus("TXN-501", "IN_REVIEW"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-501");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getCaseId()).isEqualTo("CASE-99999");
                    assertThat(response.getResults().get(0).getDocuments()).hasSize(1);
                    assertThat(response.getResults().get(0).getDocuments().get(0).getStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getDocuments().get(0).getMessage()).contains("Failed to call Document Processing API");
                })
                .verifyComplete();

        verify(applicationDocumentRepository).updateDocumentProcessingResult(eq("TXN-501"), eq("DOC-05"), eq("IN_REVIEW"), any(), any());
        org.mockito.ArgumentCaptor<CreateCaseRequest> captor = org.mockito.ArgumentCaptor.forClass(CreateCaseRequest.class);
        verify(caseManagementClient).createCase(captor.capture());
        assertThat(captor.getValue().getCaseType()).isEqualTo("LOAN_APPLICATION");
        verify(loanApplicationRepository).updateStatus("TXN-501", "IN_REVIEW");
    }

    @Test
    void processSubmittedApplications_whenCaseManagementServiceFails_shouldStillSetInReview() {
        SubmittedApplication app = new SubmittedApplication("TXN-601", "usr_601", "Single Application", "SUBMITTED");
        DocumentRecord doc = new DocumentRecord(
                "TXN-601", "DOC-06", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf",
                "PENDING", null, null, Instant.now()
        );

        when(loanApplicationRepository.findApplicationsByStatus("SUBMITTED"))
                .thenReturn(Mono.just(List.of(app)));
        when(applicationDocumentRepository.findByTransactionId("TXN-601"))
                .thenReturn(Mono.just(List.of(doc)));
        when(documentProcessingClient.processDocument("gs://bucket/payslip.pdf", "application/pdf", null))
                .thenReturn(Mono.error(new RuntimeException("503 Service Unavailable")));
        when(applicationDocumentRepository.updateDocumentProcessingResult(eq("TXN-601"), eq("DOC-06"), eq("IN_REVIEW"), any(), any()))
                .thenReturn(Mono.empty());
        when(caseManagementClient.createCase(any(CreateCaseRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Case Management Service down")));
        when(loanApplicationRepository.updateStatus("TXN-601", "IN_REVIEW"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processSubmittedApplications())
                .assertNext(response -> {
                    assertThat(response.getTotalProcessed()).isEqualTo(1);
                    assertThat(response.getResults().get(0).getTransactionId()).isEqualTo("TXN-601");
                    assertThat(response.getResults().get(0).getFinalStatus()).isEqualTo("IN_REVIEW");
                    assertThat(response.getResults().get(0).getCaseId()).isNull();
                    assertThat(response.getResults().get(0).getMessage()).contains("Warning: Case creation failed");
                })
                .verifyComplete();

        verify(loanApplicationRepository).updateStatus("TXN-601", "IN_REVIEW");
    }
}
