package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.client.CaseManagementClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchDocumentItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.model.DocumentRecord;
import com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class BatchProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingService.class);
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_IN_REVIEW = "IN_REVIEW";
    private static final String DOC_STATUS_SUCCESS = "SUCCESS";
    private static final String DOC_STATUS_FAILED = "FAILED";
    private static final String DOC_STATUS_IN_REVIEW = "IN_REVIEW";

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final DocumentProcessingClient documentProcessingClient;
    private final CaseManagementClient caseManagementClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            CaseManagementClient caseManagementClient
    ) {
        this(
                loanApplicationRepository,
                applicationDocumentRepository,
                documentProcessingClient,
                caseManagementClient,
                new ObjectMapper()
                        .findAndRegisterModules()
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            CaseManagementClient caseManagementClient,
            ObjectMapper objectMapper
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.documentProcessingClient = documentProcessingClient;
        this.caseManagementClient = caseManagementClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Processes all applications currently in SUBMITTED status.
     * For each application, inspects each associated document, updates document status/details,
     * updates the application status (APPROVED / REJECTED / IN_REVIEW), and triggers Case Management if IN_REVIEW.
     *
     * @return Mono of BatchProcessResponse summarizing all processed transactions.
     */
    public Mono<BatchProcessResponse> processSubmittedApplications() {
        log.info("Starting batch verification job for SUBMITTED applications");

        return loanApplicationRepository.findApplicationsByStatus(STATUS_SUBMITTED)
                .flatMap(applications -> {
                    if (applications == null || applications.isEmpty()) {
                        log.info("No applications found with status SUBMITTED");
                        return Mono.just(BatchProcessResponse.noTransactionsFound());
                    }

                    log.info("Found {} SUBMITTED application(s) to process in batch", applications.size());

                    return Flux.fromIterable(applications)
                            .concatMap(this::processSingleApplication)
                            .collectList()
                            .map(results -> new BatchProcessResponse(
                                    "SUCCESS",
                                    "Processed " + results.size() + " SUBMITTED application(s)",
                                    results.size(),
                                    results,
                                    Instant.now()
                            ));
                });
    }

    private Mono<BatchProcessItemResponse> processSingleApplication(SubmittedApplication app) {
        String transactionId = app.transactionId();
        String userId = app.userId();
        log.info("Batch verifying application transactionId: {}, userId: {}", transactionId, userId);

        return applicationDocumentRepository.findByTransactionId(transactionId)
                .flatMap(documents -> {
                    if (documents == null || documents.isEmpty()) {
                        log.warn("Application {} has no documents. Rejecting application.", transactionId);
                        return loanApplicationRepository.updateStatus(transactionId, STATUS_REJECTED)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_REJECTED,
                                        "No documents found for submitted application",
                                        null,
                                        List.of()
                                ));
                    }

                    return Flux.fromIterable(documents)
                            .concatMap(doc -> processSingleDocument(transactionId, doc))
                            .collectList()
                            .flatMap(docResults -> evaluateAndFinalizeApplication(app, documents, docResults));
                });
    }

    private Mono<BatchDocumentItemResponse> processSingleDocument(String transactionId, DocumentRecord doc) {
        log.info("Checking document id: {}, filename: {}, gcsUrl: {}",
                doc.documentId(), doc.documentFilename(), doc.gcsUrl());

        return documentProcessingClient.processDocument(doc.gcsUrl(), doc.contentType(), null)
                .flatMap(response -> {
                    String docStatus = response != null && response.getStatus() != null && !response.getStatus().isBlank()
                            ? response.getStatus()
                            : determineDocumentStatus(response);
                    if (response != null && response.isTampered()) {
                        docStatus = DOC_STATUS_FAILED;
                    }

                    String docMessage = response != null ? response.getMessage() : null;

                    String processingDetailsJson;
                    try {
                        processingDetailsJson = objectMapper.writeValueAsString(response);
                    } catch (Exception e) {
                        processingDetailsJson = "{}";
                    }

                    return applicationDocumentRepository.updateDocumentProcessingResult(
                            transactionId,
                            doc.documentId(),
                            docStatus,
                            docMessage,
                            processingDetailsJson
                    ).thenReturn(new BatchDocumentItemResponse(
                            doc.documentId(),
                            doc.documentFilename(),
                            docStatus,
                            docMessage
                    ));
                })
                .onErrorResume(err -> {
                    log.warn("Document Processing API call failed for docId: {}, transactionId: {}. Marking as IN_REVIEW. Error: {}",
                            doc.documentId(), transactionId, err.getMessage());
                    String docStatus = DOC_STATUS_IN_REVIEW;
                    String docMessage = "Failed to call Document Processing API (/api/v1/doc/processing): " + err.getMessage();
                    String processingDetailsJson;
                    try {
                        processingDetailsJson = objectMapper.writeValueAsString(java.util.Map.of(
                                "status", DOC_STATUS_IN_REVIEW,
                                "error", err.getMessage() != null ? err.getMessage() : "API call failed",
                                "gcsUrl", doc.gcsUrl() != null ? doc.gcsUrl() : ""
                        ));
                    } catch (Exception e) {
                        processingDetailsJson = "{\"status\":\"IN_REVIEW\"}";
                    }

                    return applicationDocumentRepository.updateDocumentProcessingResult(
                            transactionId,
                            doc.documentId(),
                            docStatus,
                            docMessage,
                            processingDetailsJson
                    ).thenReturn(new BatchDocumentItemResponse(
                            doc.documentId(),
                            doc.documentFilename(),
                            docStatus,
                            docMessage
                    ));
                });
    }

    private String determineDocumentStatus(DocProcessingResponseDto response) {
        if (response == null) {
            return DOC_STATUS_IN_REVIEW;
        }
        if (response.isTampered()) {
            return DOC_STATUS_FAILED;
        }
        String status = response.getStatus();
        if (status == null || status.isBlank()) {
            return DOC_STATUS_IN_REVIEW;
        }
        if (DOC_STATUS_FAILED.equalsIgnoreCase(status)) {
            return DOC_STATUS_FAILED;
        }
        if (DOC_STATUS_IN_REVIEW.equalsIgnoreCase(status)) {
            return DOC_STATUS_IN_REVIEW;
        }
        if (DOC_STATUS_SUCCESS.equalsIgnoreCase(status)) {
            return DOC_STATUS_SUCCESS;
        }
        return status.toUpperCase();
    }

    private Mono<BatchProcessItemResponse> evaluateAndFinalizeApplication(
            SubmittedApplication app,
            List<DocumentRecord> originalDocs,
            List<BatchDocumentItemResponse> docResults
    ) {
        String transactionId = app.transactionId();
        String userId = app.userId();

        boolean hasFailure = docResults.stream().anyMatch(d -> DOC_STATUS_FAILED.equalsIgnoreCase(d.getStatus()));
        boolean hasInReview = docResults.stream().anyMatch(d -> DOC_STATUS_IN_REVIEW.equalsIgnoreCase(d.getStatus()));

        if (hasFailure) {
            log.info("Application {} failed document verification. Updating status to REJECTED", transactionId);
            return loanApplicationRepository.updateStatus(transactionId, STATUS_REJECTED)
                    .thenReturn(new BatchProcessItemResponse(
                            transactionId,
                            userId,
                            STATUS_SUBMITTED,
                            STATUS_REJECTED,
                            "Application rejected due to document verification failure",
                            null,
                            docResults
                    ));
        } else if (hasInReview) {
            log.info("Application {} has documents in IN_REVIEW status. Escalating to Case Management Service", transactionId);
            String firstDocUrl = originalDocs.stream()
                    .map(DocumentRecord::gcsUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);

            CreateCaseRequest caseRequest = new CreateCaseRequest();
            caseRequest.setUserId(userId != null && !userId.isBlank() ? userId : "applicant");
            caseRequest.setCaseType("LOAN_APPLICATION");
            caseRequest.setCaseStatus("IN_PROGRESS");
            caseRequest.setDocumentUrl(firstDocUrl);
            caseRequest.setRemarks("Application " + transactionId + " submitted documents require human verification.");
            caseRequest.setRiskScore(50.0);
            caseRequest.setRiskLevel("MEDIUM");

            return caseManagementClient.createCase(caseRequest)
                    .flatMap(caseResponse -> {
                        String caseId = caseResponse != null ? caseResponse.caseId() : null;
                        return loanApplicationRepository.updateStatus(transactionId, STATUS_IN_REVIEW)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_IN_REVIEW,
                                        "Application set to IN_REVIEW. Created case in Case Management Service: " + caseId,
                                        caseId,
                                        docResults
                                ));
                    })
                    .onErrorResume(err -> {
                        log.warn("Case Management Service error for application {}: {}. Updating status to IN_REVIEW.", transactionId, err.getMessage());
                        return loanApplicationRepository.updateStatus(transactionId, STATUS_IN_REVIEW)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_IN_REVIEW,
                                        "Application set to IN_REVIEW. Warning: Case creation failed: " + err.getMessage(),
                                        null,
                                        docResults
                                ));
                    });
        } else {
            log.info("Application {} passed all document verifications. Updating status to APPROVED", transactionId);
            return loanApplicationRepository.updateStatus(transactionId, STATUS_APPROVED)
                    .thenReturn(new BatchProcessItemResponse(
                            transactionId,
                            userId,
                            STATUS_SUBMITTED,
                            STATUS_APPROVED,
                            "Application verified and approved successfully",
                            null,
                            docResults
                    ));
        }
    }
}
