package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.client.CaseManagementClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.GraphProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchDocumentItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessItemResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DynamicDocumentData;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisResult;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingService.class);
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_IN_REVIEW = "IN_REVIEW";
    private static final String STATUS_FLAGGED = "FLAGGED";
    private static final String DOC_STATUS_SUCCESS = "SUCCESS";
    private static final String DOC_STATUS_FAILED = "FAILED";
    private static final String DOC_STATUS_IN_REVIEW = "IN_REVIEW";

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final DocumentProcessingClient documentProcessingClient;
    private final GraphProcessingClient graphProcessingClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            GraphProcessingClient graphProcessingClient
    ) {
        this(
                loanApplicationRepository,
                applicationDocumentRepository,
                documentProcessingClient,
                graphProcessingClient,
                new ObjectMapper()
                        .findAndRegisterModules()
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            GraphProcessingClient graphProcessingClient,
            ObjectMapper objectMapper
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.documentProcessingClient = documentProcessingClient;
        this.graphProcessingClient = graphProcessingClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            ObjectMapper objectMapper
    ) {
        this(loanApplicationRepository, applicationDocumentRepository, documentProcessingClient, (GraphProcessingClient) null, objectMapper);
    }

    @Deprecated
    public BatchProcessingService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository applicationDocumentRepository,
            DocumentProcessingClient documentProcessingClient,
            CaseManagementClient caseManagementClient,
            ObjectMapper objectMapper
    ) {
        this(loanApplicationRepository, applicationDocumentRepository, documentProcessingClient, (GraphProcessingClient) null, objectMapper);
    }

    /**
     * Processes all applications currently in SUBMITTED status.
     * For each application, inspects each associated document, updates document status/details in the database,
     * processes all documents, and then decides the final application status (APPROVED / REJECTED / IN_REVIEW).
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
                        String aiAnalysisJson = buildAiAnalysisJson(List.of(), null);
                        return loanApplicationRepository.updateStatusAndAiAnalysis(transactionId, STATUS_REJECTED, aiAnalysisJson)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_REJECTED,
                                        "No documents found for submitted application",
                                        null,
                                        List.of(),
                                        null
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
                    String docStatus = determineDocumentStatus(response);
                    String docMessage = response != null && response.getMessage() != null && !response.getMessage().isBlank()
                            ? response.getMessage()
                            : defaultMessageForStatus(docStatus);

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
                    String docMessage = "Failed to process document: " + (err.getMessage() != null && !err.getMessage().isBlank() ? err.getMessage() : err.getClass().getSimpleName());
                    String processingDetailsJson;
                    try {
                        processingDetailsJson = objectMapper.writeValueAsString(Map.of(
                                "status", DOC_STATUS_IN_REVIEW,
                                "error", err.getMessage() != null ? err.getMessage() : "Document processing error",
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
        if (DOC_STATUS_FAILED.equalsIgnoreCase(status) || STATUS_REJECTED.equalsIgnoreCase(status)) {
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

    private String defaultMessageForStatus(String status) {
        if (DOC_STATUS_FAILED.equalsIgnoreCase(status) || STATUS_REJECTED.equalsIgnoreCase(status)) {
            return "Document verification failed";
        }
        if (DOC_STATUS_IN_REVIEW.equalsIgnoreCase(status)) {
            return "Document requires review";
        }
        return "Document verified successfully";
    }

    private Mono<BatchProcessItemResponse> evaluateAndFinalizeApplication(
            SubmittedApplication app,
            List<DocumentRecord> originalDocs,
            List<BatchDocumentItemResponse> docResults
    ) {
        String transactionId = app.transactionId();
        String userId = app.userId();

        boolean hasFailure = docResults.stream().anyMatch(d ->
                DOC_STATUS_FAILED.equalsIgnoreCase(d.getStatus()) ||
                STATUS_REJECTED.equalsIgnoreCase(d.getStatus())
        );
        boolean hasInReview = docResults.stream().anyMatch(d ->
                DOC_STATUS_IN_REVIEW.equalsIgnoreCase(d.getStatus())
        );

        if (hasFailure) {
            log.info("Application {} has rejected/failed documents. Skipping Graph Analysis and updating application status to REJECTED", transactionId);
            String aiAnalysisJson = buildAiAnalysisJson(docResults, null);
            return loanApplicationRepository.updateStatusAndAiAnalysis(transactionId, STATUS_REJECTED, aiAnalysisJson)
                    .thenReturn(new BatchProcessItemResponse(
                            transactionId,
                            userId,
                            STATUS_SUBMITTED,
                            STATUS_REJECTED,
                            "Application rejected due to document verification failure",
                            null,
                            docResults,
                            null
                    ));
        }

        // For non-rejected statuses (such as SUCCESS or IN_REVIEW), call Graph Analysis API
        log.info("Application {} passed initial document check without rejections. Invoking Graph Analysis API.", transactionId);
        return executeGraphAnalysis(app)
                .flatMap(graphResult -> {
                    String graphStatus = graphResult != null ? graphResult.status() : null;
                    boolean graphPassed = graphResult != null && graphResult.passed();
                    List<String> discrepancies = graphResult != null && graphResult.discrepancies() != null
                            ? graphResult.discrepancies()
                            : List.of();
                    String aiAnalysisJson = buildAiAnalysisJson(docResults, graphResult);

                    if (STATUS_REJECTED.equalsIgnoreCase(graphStatus)) {
                        String msg = "Application rejected by graph analysis: " + (discrepancies.isEmpty() ? "Fraud triangulation check failed" : String.join("; ", discrepancies));
                        log.info("Application {} rejected by Graph Analysis. Reason: {}", transactionId, msg);
                        return loanApplicationRepository.updateStatusAndAiAnalysis(transactionId, STATUS_REJECTED, aiAnalysisJson)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_REJECTED,
                                        msg,
                                        null,
                                        docResults,
                                        graphResult
                                ));
                    } else if (STATUS_FLAGGED.equalsIgnoreCase(graphStatus) || !graphPassed || hasInReview) {
                        String msg = hasInReview
                                ? "Application set to IN_REVIEW due to document review requirement"
                                : "Application flagged for manual review by graph analysis: " + (discrepancies.isEmpty() ? "Discrepancy detected" : String.join("; ", discrepancies));
                        log.info("Application {} set to IN_REVIEW. Reason: {}", transactionId, msg);
                        return loanApplicationRepository.updateStatusAndAiAnalysis(transactionId, STATUS_IN_REVIEW, aiAnalysisJson)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_IN_REVIEW,
                                        msg,
                                        null,
                                        docResults,
                                        graphResult
                                ));
                    } else {
                        log.info("Application {} passed all document verifications and graph analysis. Updating status to APPROVED", transactionId);
                        return loanApplicationRepository.updateStatusAndAiAnalysis(transactionId, STATUS_APPROVED, aiAnalysisJson)
                                .thenReturn(new BatchProcessItemResponse(
                                        transactionId,
                                        userId,
                                        STATUS_SUBMITTED,
                                        STATUS_APPROVED,
                                        "Application verified and approved successfully",
                                        null,
                                        docResults,
                                        graphResult
                                ));
                    }
                });
    }

    private String buildAiAnalysisJson(List<BatchDocumentItemResponse> docResults, GraphAnalysisResult graphResult) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("graphAnalysis", graphResult);
        map.put("documents", docResults != null ? docResults : List.of());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize ai_analysis payload for transaction: {}", e.getMessage());
            return "{}";
        }
    }

    private Mono<GraphAnalysisResult> executeGraphAnalysis(SubmittedApplication app) {
        if (graphProcessingClient == null) {
            log.warn("GraphProcessingClient is null. Skipping Graph Analysis call and assuming APPROVED.");
            return Mono.just(new GraphAnalysisResult(STATUS_APPROVED, "SALARY_TRIANGULATION", true, List.of()));
        }

        String transactionId = app.transactionId();
        String userId = app.userId();

        return loanApplicationRepository.getApplicationDetails(transactionId, userId)
                .defaultIfEmpty(Collections.emptyMap())
                .flatMap(appDetails ->
                    applicationDocumentRepository.findByTransactionId(transactionId)
                            .defaultIfEmpty(Collections.emptyList())
                            .flatMap(docRecords -> {
                                GraphAnalysisRequest request = buildGraphAnalysisRequest(transactionId, appDetails, docRecords);
                                return graphProcessingClient.analyzeGraph(request);
                            })
                )
                .onErrorResume(err -> {
                    log.warn("Error during graph analysis call for transaction {}: {}", transactionId, err.getMessage());
                    return Mono.just(new GraphAnalysisResult(
                            STATUS_FLAGGED,
                            "SALARY_TRIANGULATION",
                            false,
                            List.of("Graph analysis error: " + err.getMessage())
                    ));
                });
    }

    private GraphAnalysisRequest buildGraphAnalysisRequest(
            String transactionId,
            Map<String, Object> appDetails,
            List<DocumentRecord> docRecords
    ) {
        // 1. Flatten loan application information (application, applicant, property) to 1 level only
        Map<String, Object> flatLoanApplication = new LinkedHashMap<>();
        flatLoanApplication.put("applicationId", transactionId);

        if (appDetails != null) {
            for (Map.Entry<String, Object> entry : appDetails.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Map<?, ?> nestedMap) {
                    for (Map.Entry<?, ?> nestedEntry : nestedMap.entrySet()) {
                        if (nestedEntry.getKey() != null && nestedEntry.getValue() != null) {
                            String k = nestedEntry.getKey().toString();
                            flatLoanApplication.put(k, nestedEntry.getValue());
                            if ("full_name".equalsIgnoreCase(k) || "fullName".equalsIgnoreCase(k)) {
                                flatLoanApplication.put("applicantName", nestedEntry.getValue());
                            }
                        }
                    }
                } else if (val != null) {
                    flatLoanApplication.put(entry.getKey(), val);
                }
            }
        }

        // 2. Build list of documents with their extractedData
        List<DynamicDocumentData> documents = new ArrayList<>();
        if (docRecords != null) {
            for (DocumentRecord doc : docRecords) {
                Map<String, Object> extractedData = extractDataFromDocRecord(doc);
                String docType = inferDocumentType(doc, extractedData);
                documents.add(new DynamicDocumentData(docType, extractedData));
            }
        }

        return new GraphAnalysisRequest(flatLoanApplication, documents);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataFromDocRecord(DocumentRecord doc) {
        if (doc == null || doc.documentProcessingDetails() == null || doc.documentProcessingDetails().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> details = objectMapper.readValue(doc.documentProcessingDetails(), Map.class);
            if (details.containsKey("extractedFields") && details.get("extractedFields") instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return details;
        } catch (Exception e) {
            log.warn("Failed to parse documentProcessingDetails for docId {}: {}", doc.documentId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String inferDocumentType(DocumentRecord doc, Map<String, Object> extractedData) {
        if (doc != null && doc.documentFilename() != null) {
            String fn = doc.documentFilename().toUpperCase();
            if (fn.contains("PAYSLIP") || fn.contains("SALARY")) {
                return "PAYSLIP";
            }
            if (fn.contains("BANK") || fn.contains("STATEMENT")) {
                return "BANK_STATEMENT";
            }
        }
        if (extractedData != null) {
            if (extractedData.containsKey("grossSalary") || extractedData.containsKey("netSalary") || extractedData.containsKey("dateJoined")) {
                return "PAYSLIP";
            }
            if (extractedData.containsKey("bankName") || extractedData.containsKey("statementPeriod") || extractedData.containsKey("accountHolder")) {
                return "BANK_STATEMENT";
            }
        }
        return "DOCUMENT";
    }
}

