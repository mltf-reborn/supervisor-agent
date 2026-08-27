package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.SelfieValidationClient;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain-specific orchestration tools exposed to the Google ADK Supervisor LLM Agent.
 * Enables the supervisor model to invoke worker models and external KYC APIs via Function Calling.
 */
@Component
public class KycSupervisorTools {

    private static final Logger log = LoggerFactory.getLogger(KycSupervisorTools.class);

    private final DocumentProcessingClient documentProcessingClient;
    private final SelfieValidationClient selfieValidationClient;
    private final ExternalKycClient externalKycClient;
    private final ObjectMapper objectMapper;

    public KycSupervisorTools(
            DocumentProcessingClient documentProcessingClient,
            SelfieValidationClient selfieValidationClient,
            ExternalKycClient externalKycClient
    ) {
        this.documentProcessingClient = documentProcessingClient;
        this.selfieValidationClient = selfieValidationClient;
        this.externalKycClient = externalKycClient;
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
}
