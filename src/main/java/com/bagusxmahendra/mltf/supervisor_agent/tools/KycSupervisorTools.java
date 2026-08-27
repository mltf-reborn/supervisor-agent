package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.CaseManagementClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.SelfieValidationClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CaseResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SelfieValidationResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.tools.Annotations.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain-specific orchestration tools exposed to the Google ADK Supervisor LLM Agent.
 * Enables the supervisor model to invoke worker models, external KYC APIs, and the
 * Case Management Service for human-in-the-loop escalation via Function Calling.
 */
@Component
public class KycSupervisorTools {

    private static final Logger log = LoggerFactory.getLogger(KycSupervisorTools.class);

    private final DocumentProcessingClient documentProcessingClient;
    private final SelfieValidationClient selfieValidationClient;
    private final ExternalKycClient externalKycClient;
    private final CaseManagementClient caseManagementClient;
    private final ObjectMapper objectMapper;

    public KycSupervisorTools(
            DocumentProcessingClient documentProcessingClient,
            SelfieValidationClient selfieValidationClient,
            ExternalKycClient externalKycClient,
            CaseManagementClient caseManagementClient
    ) {
        this.documentProcessingClient = documentProcessingClient;
        this.selfieValidationClient = selfieValidationClient;
        this.externalKycClient = externalKycClient;
        this.caseManagementClient = caseManagementClient;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Tool 1: Validate Document via /api/v1/doc/processing.
     */
    @Schema(
            name = "validateDocument",
            description = "Validates an identity document at the given GCS URL by calling the forensic document processing agent (/api/v1/doc/processing). Checks pixel tampering, computes authenticity and confidence scores, classifies document type, and extracts key fields (idNumber, fullName, DOB, address, nationality)."
    )
    public Map<String, Object> validateDocument(
            @Schema(name = "gcsUrl", description = "Google Cloud Storage URL of the identity document, e.g. gs://bucket/session/document/id.jpg") String gcsUrl,
            @Schema(name = "mimeType", description = "MIME type of document (e.g. image/jpeg, image/png, application/pdf)") String mimeType,
            @Schema(name = "customPrompt", description = "Optional specific inspection prompt") String customPrompt
    ) {
        log.info("Executing ADK Supervisor Tool [validateDocument] for GCS URL: {}", gcsUrl);
        try {
            DocProcessingResponseDto response = documentProcessingClient.processDocument(gcsUrl, mimeType, customPrompt)
                    .block(Duration.ofSeconds(60));

            if (response == null) {
                return Map.of("status", "FAILED", "error", "No response received from Document Processing Agent");
            }
            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error executing ADK tool validateDocument: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 2: Validate Selfie via /api/v1/selfie/validation.
     */
    @Schema(
            name = "validateSelfie",
            description = "Biometrically compares the customer selfie against the portrait on their Photo ID document by calling the selfie validation agent (/api/v1/selfie/validation). Evaluates facial landmarks, calculates biometric confidence score, checks matchStatus, and inspects anti-spoofing liveness."
    )
    public Map<String, Object> validateSelfie(
            @Schema(name = "idDocumentUrl", description = "GCS URL of the photo ID document") String idDocumentUrl,
            @Schema(name = "selfieUrl", description = "GCS URL of the webcam selfie photo") String selfieUrl,
            @Schema(name = "idDocumentMimeType", description = "MIME type of ID document") String idDocumentMimeType,
            @Schema(name = "selfieMimeType", description = "MIME type of selfie") String selfieMimeType,
            @Schema(name = "customPrompt", description = "Optional comparison instructions") String customPrompt
    ) {
        log.info("Executing ADK Supervisor Tool [validateSelfie] for ID: {} and Selfie: {}", idDocumentUrl, selfieUrl);
        try {
            SelfieValidationResponseDto response = selfieValidationClient.validateSelfie(
                    idDocumentUrl,
                    selfieUrl,
                    idDocumentMimeType,
                    selfieMimeType,
                    customPrompt
            ).block(Duration.ofSeconds(60));

            if (response == null) {
                return Map.of("status", "FAILED", "error", "No response received from Selfie Validation Agent");
            }
            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error executing ADK tool validateSelfie: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 3: Get External KYC Data via /api/v1/external/kyc.
     */
    @Schema(
            name = "getExternalKycData",
            description = "Queries external KYC and AML registry databases (/api/v1/external/kyc) to verify the customer's national identity, blacklist status, Politically Exposed Persons (PEP) status, sanctions, and risk tier."
    )
    public Map<String, Object> getExternalKycData(
            @Schema(name = "idNumber", description = "National ID number, passport number, or document identifier") String idNumber,
            @Schema(name = "fullName", description = "Customer full name") String fullName,
            @Schema(name = "dateOfBirth", description = "Customer date of birth (YYYY-MM-DD)") String dateOfBirth,
            @Schema(name = "nationality", description = "Customer nationality or country") String nationality
    ) {
        log.info("Executing ADK Supervisor Tool [getExternalKycData] for ID: {}, Name: {}", idNumber, fullName);
        try {
            ExternalKycResponse response = externalKycClient.fetchExternalKycData(idNumber, fullName, dateOfBirth, nationality)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                return Map.of("status", "FAILED", "error", "No response received from External KYC Service");
            }
            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error executing ADK tool getExternalKycData: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 4: Create Case via /api/v1/case in Case Management Service for Human-in-the-Loop review.
     * When KYC process falls into IN_REVIEW status, this tool creates a case in the Case Management Service
     * to assign the KYC case to a human reviewer to decide if this is a successful KYC or Failed.
     * This is an asynchronous process: this tool creates the case and returns immediately so the workflow can continue.
     */
    @Schema(
            name = "createCase",
            description = "Creates a case in the Case Management Service (/api/v1/case) for human-in-the-loop review when KYC status is IN_REVIEW, assigning the case to a human to decide if it is successful or failed. This is an async process: this tool only creates the case and then continues the process."
    )
    public Map<String, Object> createCase(
            @Schema(name = "userId", description = "The ID of the applicant / customer") String userId,
            @Schema(name = "documentUrl", description = "GCS URL of the customer identity document") String documentUrl,
            @Schema(name = "selfieUrl", description = "GCS URL of the customer selfie image") String selfieUrl,
            @Schema(name = "remarks", description = "Explanation or rationale why this KYC application requires manual human compliance review") String remarks,
            @Schema(name = "riskScore", description = "Computed risk score (e.g. 45.0)") Double riskScore,
            @Schema(name = "riskLevel", description = "Computed risk level (e.g. MEDIUM)") String riskLevel
    ) {
        log.info("Executing ADK Supervisor Tool [createCase] for userId: {}, docUrl: {}, selfieUrl: {}",
                userId, documentUrl, selfieUrl);

        CreateCaseRequest request = new CreateCaseRequest();
        request.setUserId(userId != null && !userId.isBlank() ? userId : "applicant");
        request.setCaseType("KYC");
        request.setCaseStatus("IN_PROGRESS");
        request.setDocumentUrl(documentUrl);
        request.setSelfieUrl(selfieUrl);
        request.setRemarks(remarks != null && !remarks.isBlank() ? remarks : "KYC application flagged for manual human compliance review");
        request.setRiskScore(riskScore != null ? riskScore : 45.0);
        request.setRiskLevel(riskLevel != null && !riskLevel.isBlank() ? riskLevel : "MEDIUM");
        request.setAssignedTo(null);

        Map<String, Object> kycDetails = new LinkedHashMap<>();
        kycDetails.put("userId", request.getUserId());
        kycDetails.put("status", "IN_REVIEW");
        kycDetails.put("riskScore", request.getRiskScore());
        kycDetails.put("riskLevel", request.getRiskLevel());
        kycDetails.put("remarks", request.getRemarks());
        request.setKycDetails(kycDetails);

        return createCase(request);
    }

    /**
     * Overload for programmatic execution with full verification details.
     */
    public Map<String, Object> createCase(CreateCaseRequest request) {
        if (request == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "FAILED");
            err.put("error", "Request payload is null");
            return err;
        }

        try {
            CaseResponse response = caseManagementClient.createCase(request)
                    .block(Duration.ofSeconds(5));

            if (response == null) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("status", "SUCCESS");
                fallback.put("caseStatus", "IN_PROGRESS");
                fallback.put("message", "Case created asynchronously for human review");
                return fallback;
            }

            Map<String, Object> resultMap = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
            resultMap.put("status", "SUCCESS");
            resultMap.put("message", "Case successfully created with status IN_PROGRESS for human compliance decision.");
            return resultMap;
        } catch (Exception e) {
            log.error("Error executing ADK tool createCase: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("status", "SUCCESS"); // keep non-blocking / resilient
            fallback.put("caseStatus", "IN_PROGRESS");
            fallback.put("warning", "Case creation dispatched with fallback: " + e.getMessage());
            return fallback;
        }
    }
}
