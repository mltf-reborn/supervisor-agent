package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
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
 * Domain-specific orchestration tools exposed to the LoanApplicationAgent.
 * Provides validateDocument, saveApplicationData, and saveDocument.
 */
@Component
public class LoanApplicationTools {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationTools.class);

    private final DocumentProcessingClient documentProcessingClient;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ObjectMapper objectMapper;

    public LoanApplicationTools(
            DocumentProcessingClient documentProcessingClient,
            ApplicationDocumentRepository applicationDocumentRepository,
            LoanApplicationRepository loanApplicationRepository
    ) {
        this.documentProcessingClient = documentProcessingClient;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Tool 1: Validate Document via /api/v1/doc/processing.
     */
    @Schema(
            name = "validateDocument",
            description = "Validates an uploaded loan application document at the given GCS URL by calling forensic document processing. Inspects tampering, computes confidence scores, and extracts key fields."
    )
    public Map<String, Object> validateDocument(
            @Schema(name = "gcsUrl", description = "Google Cloud Storage URL of the document, e.g. gs://bucket/application/document.pdf") String gcsUrl,
            @Schema(name = "mimeType", description = "MIME type of document (e.g. application/pdf, image/jpeg, image/png)") String mimeType,
            @Schema(name = "customPrompt", description = "Optional inspection prompt") String customPrompt
    ) {
        log.info("Executing LoanApplication Tool [validateDocument] for GCS URL: {}", gcsUrl);
        try {
            DocProcessingResponseDto response = documentProcessingClient.processDocument(gcsUrl, mimeType, customPrompt)
                    .block(Duration.ofSeconds(60));

            if (response == null) {
                return Map.of("status", "FAILED", "error", "No response received from Document Processing Agent");
            }
            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool validateDocument: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 2: Save Extracted Application Data to applicant, application, and property tables.
     */
    @Schema(
            name = "saveApplicationData",
            description = "Saves extracted fields from valid documents into the Spanner database tables (applicant, application, and property). For example, saves race and nationality to the applicant table."
    )
    public Map<String, Object> saveApplicationData(
            @Schema(name = "applicationId", description = "Transaction / Application ID (e.g. TXN-12345)") String applicationId,
            @Schema(name = "userId", description = "User / Applicant ID (e.g. usr_1001)") String userId,
            @Schema(name = "applicantData", description = "Map of applicant fields to save, such as race, nationality, gender, occupation, etc.") Map<String, Object> applicantData,
            @Schema(name = "applicationData", description = "Map of application fields to update, such as facility_type, facility_purpose, bank_selection, etc.") Map<String, Object> applicationData,
            @Schema(name = "propertyData", description = "Map of property fields to save, such as spa_price_rm, property_address, project_name, etc.") Map<String, Object> propertyData
    ) {
        log.info("Executing LoanApplication Tool [saveApplicationData] for applicationId: {}, userId: {}", applicationId, userId);
        try {
            loanApplicationRepository.updateApplicationData(
                    applicationId,
                    userId,
                    applicantData,
                    applicationData,
                    propertyData
            ).block(Duration.ofSeconds(10));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Application data saved successfully");
            result.put("applicationId", applicationId);
            return result;
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool saveApplicationData: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 3: Save Document in database tablename document.
     */
    @Schema(
            name = "saveDocument",
            description = "Saves document metadata and processing details into the database table document."
    )
    public Map<String, Object> saveDocument(
            @Schema(name = "applicationId", description = "Application ID (transaction_id)") String applicationId,
            @Schema(name = "documentId", description = "Unique document ID (e.g. DOC-12345)") String documentId,
            @Schema(name = "filename", description = "Original uploaded filename") String filename,
            @Schema(name = "gcsUrl", description = "GCS URL where file is stored") String gcsUrl,
            @Schema(name = "contentType", description = "MIME / Content Type") String contentType,
            @Schema(name = "status", description = "Document status (e.g. SUCCESS, FAILED, IN_REVIEW)") String status,
            @Schema(name = "message", description = "Document status description or remarks") String message,
            @Schema(name = "processingDetails", description = "JSON string containing full processing response or details") String processingDetails
    ) {
        log.info("Executing LoanApplication Tool [saveDocument] for docId: {}, appId: {}", documentId, applicationId);
        try {
            applicationDocumentRepository.save(
                    applicationId,
                    documentId,
                    filename,
                    gcsUrl,
                    contentType,
                    status != null ? status : "SUCCESS",
                    message != null ? message : "Document processed",
                    processingDetails != null ? processingDetails : "{}"
            ).block(Duration.ofSeconds(10));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("documentId", documentId);
            result.put("message", "Document saved to database table document successfully");
            return result;
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool saveDocument: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }
}
