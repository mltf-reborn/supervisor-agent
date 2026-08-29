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
import java.util.List;
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
     * Tool 2: Check similarity between extracted document data and existing application records.
     */
    @Schema(
            name = "checkDataSimilarity",
            description = "Checks similarity between extracted document data and existing records in database tables (application, applicant, property) BEFORE saving. Returns similarity evaluation and detects conflicting information."
    )
    public Map<String, Object> checkDataSimilarity(
            @Schema(name = "transactionId", description = "Transaction / Application ID (e.g. TXN-12345)") String transactionId,
            @Schema(name = "applicantData", description = "Map of extracted applicant fields (full_name, id_no, etc.)") Map<String, Object> applicantData,
            @Schema(name = "applicationData", description = "Map of extracted application fields (bank_selection, facility_type, etc.)") Map<String, Object> applicationData,
            @Schema(name = "propertyData", description = "Map of extracted property fields (spa_price_rm, property_address, etc.)") Map<String, Object> propertyData
    ) {
        return checkDataSimilarity(transactionId, applicantData, applicationData, propertyData, null);
    }

    public Map<String, Object> checkDataSimilarity(
            String transactionId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData,
            List<String> ignoredFields
    ) {
        log.info("Executing LoanApplication Tool [checkDataSimilarity] for transactionId: {}, custom ignored fields: {}", transactionId, ignoredFields);
        try {
            return loanApplicationRepository.checkSimilarity(transactionId, applicantData, applicationData, propertyData, ignoredFields)
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool checkDataSimilarity: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("hasConflict", true);
            errorMap.put("error", e.getMessage());
            errorMap.put("message", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 3: Save Application details to application table.
     */
    @Schema(
            name = "saveApplication",
            description = "Saves or updates application and loan facility details into the 'application' database table."
    )
    public Map<String, Object> saveApplication(
            @Schema(name = "transactionId", description = "Transaction / Application ID (e.g. TXN-12345)") String transactionId,
            @Schema(name = "userId", description = "User / Applicant ID (e.g. usr_1001)") String userId,
            @Schema(name = "bankSelection", description = "Selected bank name") String bankSelection,
            @Schema(name = "applicationType", description = "Application type (e.g. HOME_LOAN, REFINANCING)") String applicationType,
            @Schema(name = "status", description = "Application status (e.g. NEW, SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED)") String status,
            @Schema(name = "facilityType", description = "Loan facility type (e.g. Conventional, Islamic)") String facilityType,
            @Schema(name = "facilityPurpose", description = "Purpose of the facility (e.g. Purchase completed property, refinancing)") String facilityPurpose,
            @Schema(name = "marketingConsent", description = "Marketing consent (e.g. YES, NO)") String marketingConsent,
            @Schema(name = "applicationDate", description = "Date of application (YYYY-MM-DD)") String applicationDate
    ) {
        log.info("Executing LoanApplication Tool [saveApplication] for transactionId: {}, userId: {}", transactionId, userId);
        Map<String, Object> applicationData = new LinkedHashMap<>();
        if (bankSelection != null) applicationData.put("bank_selection", bankSelection);
        if (applicationType != null) applicationData.put("application_type", applicationType);
        if (status != null) applicationData.put("status", status);
        if (facilityType != null) applicationData.put("facility_type", facilityType);
        if (facilityPurpose != null) applicationData.put("facility_purpose", facilityPurpose);
        if (marketingConsent != null) applicationData.put("marketing_consent", marketingConsent);
        if (applicationDate != null && !applicationDate.trim().isEmpty()) {
            applicationData.put("application_date", applicationDate.trim());
        }

        return saveApplication(transactionId, userId, applicationData);
    }

    public Map<String, Object> saveApplication(String transactionId, String userId, Map<String, Object> applicationData) {
        try {
            loanApplicationRepository.saveApplication(transactionId, userId, applicationData)
                    .block(Duration.ofSeconds(10));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Application data saved successfully");
            result.put("transactionId", transactionId);
            return result;
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool saveApplication: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 3: Save Applicant details to applicant table.
     */
    @Schema(
            name = "saveApplicant",
            description = "Saves or updates applicant personal details, employment, contact, and spouse information into the 'applicant' database table."
    )
    public Map<String, Object> saveApplicant(
            @Schema(name = "transactionId", description = "Transaction / Application ID (e.g. TXN-12345)") String transactionId,
            @Schema(name = "applicantId", description = "Applicant ID / User ID (MUST use the User ID provided in the user prompt)") String applicantId,
            @Schema(name = "role", description = "Role of applicant (e.g. Primary, Joint, Guarantor)") String role,
            @Schema(name = "fullName", description = "Full name as in identity document") String fullName,
            @Schema(name = "idType", description = "Identity card type (e.g. NRIC, PASSPORT, MYKAD)") String idType,
            @Schema(name = "idNo", description = "Identity card / passport number") String idNo,
            @Schema(name = "nationality", description = "Country of nationality or citizenship") String nationality,
            @Schema(name = "race", description = "Race / ethnicity (e.g. Malay, Chinese, Indian)") String race,
            @Schema(name = "bumiputeraStatus", description = "Bumiputera status indicator (true/false)") Boolean bumiputeraStatus,
            @Schema(name = "gender", description = "Gender (e.g. MALE, FEMALE)") String gender,
            @Schema(name = "maritalStatus", description = "Marital status (e.g. SINGLE, MARRIED)") String maritalStatus,
            @Schema(name = "dateOfBirth", description = "Date of birth (YYYY-MM-DD)") String dateOfBirth,
            @Schema(name = "dependentsCount", description = "Number of dependents") Long dependentsCount,
            @Schema(name = "educationLevel", description = "Highest education level") String educationLevel,
            @Schema(name = "mobilePhone", description = "Mobile contact phone number") String mobilePhone,
            @Schema(name = "residentialPhone", description = "Residential phone number") String residentialPhone,
            @Schema(name = "email", description = "Email address") String email,
            @Schema(name = "permAddress", description = "Permanent address") String permAddress,
            @Schema(name = "permPostcode", description = "Permanent postcode") String permPostcode,
            @Schema(name = "permCity", description = "Permanent city") String permCity,
            @Schema(name = "permState", description = "Permanent state") String permState,
            @Schema(name = "mailAddress", description = "Mailing / correspondence address") String mailAddress,
            @Schema(name = "mailPostcode", description = "Mailing postcode") String mailPostcode,
            @Schema(name = "employmentStatus", description = "Employment status (e.g. EMPLOYED, SELF_EMPLOYED)") String employmentStatus,
            @Schema(name = "employerName", description = "Employer / company name") String employerName,
            @Schema(name = "natureOfBusiness", description = "Nature of business / industry") String natureOfBusiness,
            @Schema(name = "occupation", description = "Occupation / job title") String occupation,
            @Schema(name = "jobPosition", description = "Job position / designation") String jobPosition,
            @Schema(name = "lengthOfServiceYears", description = "Length of service in years") Double lengthOfServiceYears,
            @Schema(name = "monthlyGrossRm", description = "Monthly gross income in RM") Double monthlyGrossRm,
            @Schema(name = "annualGrossRm", description = "Annual gross income in RM") Double annualGrossRm,
            @Schema(name = "emergencyName", description = "Emergency contact name") String emergencyName,
            @Schema(name = "emergencyRelationship", description = "Relationship to emergency contact") String emergencyRelationship,
            @Schema(name = "emergencyPhone", description = "Emergency contact phone") String emergencyPhone,
            @Schema(name = "spouseFullName", description = "Spouse full name") String spouseFullName,
            @Schema(name = "spouseIdNo", description = "Spouse IC / passport number") String spouseIdNo,
            @Schema(name = "spouseMobile", description = "Spouse mobile phone") String spouseMobile,
            @Schema(name = "spouseEmployer", description = "Spouse employer name") String spouseEmployer,
            @Schema(name = "spouseMonthlyGrossRm", description = "Spouse monthly gross income in RM") Double spouseMonthlyGrossRm
    ) {
        log.info("Executing LoanApplication Tool [saveApplicant] for transactionId: {}, applicantId: {}", transactionId, applicantId);
        Map<String, Object> applicantData = new LinkedHashMap<>();
        if (role != null) applicantData.put("role", role);
        if (fullName != null) applicantData.put("full_name", fullName);
        if (idType != null) applicantData.put("id_type", idType);
        if (idNo != null) applicantData.put("id_no", idNo);
        if (nationality != null) applicantData.put("nationality", nationality);
        if (race != null) applicantData.put("race", race);
        if (bumiputeraStatus != null) applicantData.put("bumiputera_status", bumiputeraStatus);
        if (gender != null) applicantData.put("gender", gender);
        if (maritalStatus != null) applicantData.put("marital_status", maritalStatus);
        if (dateOfBirth != null) applicantData.put("date_of_birth", dateOfBirth);
        if (dependentsCount != null) applicantData.put("dependents_count", dependentsCount);
        if (educationLevel != null) applicantData.put("education_level", educationLevel);
        if (mobilePhone != null) applicantData.put("mobile_phone", mobilePhone);
        if (residentialPhone != null) applicantData.put("residential_phone", residentialPhone);
        if (email != null) applicantData.put("email", email);
        if (permAddress != null) applicantData.put("perm_address", permAddress);
        if (permPostcode != null) applicantData.put("perm_postcode", permPostcode);
        if (permCity != null) applicantData.put("perm_city", permCity);
        if (permState != null) applicantData.put("perm_state", permState);
        if (mailAddress != null) applicantData.put("mail_address", mailAddress);
        if (mailPostcode != null) applicantData.put("mail_postcode", mailPostcode);
        if (employmentStatus != null) applicantData.put("employment_status", employmentStatus);
        if (employerName != null) applicantData.put("employer_name", employerName);
        if (natureOfBusiness != null) applicantData.put("nature_of_business", natureOfBusiness);
        if (occupation != null) applicantData.put("occupation", occupation);
        if (jobPosition != null) applicantData.put("job_position", jobPosition);
        if (lengthOfServiceYears != null) applicantData.put("length_of_service_years", lengthOfServiceYears);
        if (monthlyGrossRm != null) applicantData.put("monthly_gross_rm", monthlyGrossRm);
        if (annualGrossRm != null) applicantData.put("annual_gross_rm", annualGrossRm);
        if (emergencyName != null) applicantData.put("emergency_name", emergencyName);
        if (emergencyRelationship != null) applicantData.put("emergency_relationship", emergencyRelationship);
        if (emergencyPhone != null) applicantData.put("emergency_phone", emergencyPhone);
        if (spouseFullName != null) applicantData.put("spouse_full_name", spouseFullName);
        if (spouseIdNo != null) applicantData.put("spouse_id_no", spouseIdNo);
        if (spouseMobile != null) applicantData.put("spouse_mobile", spouseMobile);
        if (spouseEmployer != null) applicantData.put("spouse_employer", spouseEmployer);
        if (spouseMonthlyGrossRm != null) applicantData.put("spouse_monthly_gross_rm", spouseMonthlyGrossRm);

        return saveApplicant(transactionId, applicantId, applicantData);
    }

    public Map<String, Object> saveApplicant(String transactionId, String applicantId, Map<String, Object> applicantData) {
        try {
            loanApplicationRepository.saveApplicant(transactionId, applicantId, applicantData)
                    .block(Duration.ofSeconds(10));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Applicant data saved successfully");
            result.put("transactionId", transactionId);
            result.put("applicantId", applicantId);
            return result;
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool saveApplicant: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Tool 4: Save Property details to property table.
     */
    @Schema(
            name = "saveProperty",
            description = "Saves or updates property, collateral, valuation, and land title details into the 'property' database table."
    )
    public Map<String, Object> saveProperty(
            @Schema(name = "transactionId", description = "Transaction / Application ID (e.g. TXN-12345)") String transactionId,
            @Schema(name = "propertyId", description = "Unique property ID (e.g. PROP-12345)") String propertyId,
            @Schema(name = "propertyType", description = "Property type (e.g. Condominium, Terrace, Semi-D, Bungalow)") String propertyType,
            @Schema(name = "propertyStatus", description = "Property status (e.g. Completed, Under Construction, Subsale)") String propertyStatus,
            @Schema(name = "developerName", description = "Property developer name") String developerName,
            @Schema(name = "projectName", description = "Housing / project development name") String projectName,
            @Schema(name = "contractorName", description = "Contractor / builder name") String contractorName,
            @Schema(name = "spaPriceRm", description = "Sales and Purchase Agreement (SPA) price in RM") Double spaPriceRm,
            @Schema(name = "openMarketRm", description = "Open market valuation in RM") Double openMarketRm,
            @Schema(name = "renovationValueRm", description = "Renovation value in RM") Double renovationValueRm,
            @Schema(name = "propertyAddress", description = "Property street / unit address") String propertyAddress,
            @Schema(name = "propertyPostcode", description = "Property postcode") String propertyPostcode,
            @Schema(name = "propertyCity", description = "Property city / town") String propertyCity,
            @Schema(name = "propertyState", description = "Property state") String propertyState,
            @Schema(name = "titleNumber", description = "Land / strata title number") String titleNumber,
            @Schema(name = "titleType", description = "Title tenure type (e.g. Freehold, Leasehold, Strata)") String titleType,
            @Schema(name = "lotNumber", description = "Lot / parcel / unit number") String lotNumber,
            @Schema(name = "mukim", description = "Mukim / sub-district") String mukim,
            @Schema(name = "district", description = "District / daerah") String district,
            @Schema(name = "isOwnerOccupied", description = "Whether property is owner-occupied (true/false)") Boolean isOwnerOccupied,
            @Schema(name = "isFirstTimeBuyer", description = "Whether buyer is a first-time home buyer (true/false)") Boolean isFirstTimeBuyer
    ) {
        log.info("Executing LoanApplication Tool [saveProperty] for transactionId: {}, propertyId: {}", transactionId, propertyId);
        Map<String, Object> propertyData = new LinkedHashMap<>();
        if (propertyType != null) propertyData.put("property_type", propertyType);
        if (propertyStatus != null) propertyData.put("property_status", propertyStatus);
        if (developerName != null) propertyData.put("developer_name", developerName);
        if (projectName != null) propertyData.put("project_name", projectName);
        if (contractorName != null) propertyData.put("contractor_name", contractorName);
        if (spaPriceRm != null) propertyData.put("spa_price_rm", spaPriceRm);
        if (openMarketRm != null) propertyData.put("open_market_rm", openMarketRm);
        if (renovationValueRm != null) propertyData.put("renovation_value_rm", renovationValueRm);
        if (propertyAddress != null) propertyData.put("property_address", propertyAddress);
        if (propertyPostcode != null) propertyData.put("property_postcode", propertyPostcode);
        if (propertyCity != null) propertyData.put("property_city", propertyCity);
        if (propertyState != null) propertyData.put("property_state", propertyState);
        if (titleNumber != null) propertyData.put("title_number", titleNumber);
        if (titleType != null) propertyData.put("title_type", titleType);
        if (lotNumber != null) propertyData.put("lot_number", lotNumber);
        if (mukim != null) propertyData.put("mukim", mukim);
        if (district != null) propertyData.put("district", district);
        if (isOwnerOccupied != null) propertyData.put("is_owner_occupied", isOwnerOccupied);
        if (isFirstTimeBuyer != null) propertyData.put("is_first_time_buyer", isFirstTimeBuyer);

        return saveProperty(transactionId, propertyId, propertyData);
    }

    public Map<String, Object> saveProperty(String transactionId, String propertyId, Map<String, Object> propertyData) {
        try {
            loanApplicationRepository.saveProperty(transactionId, propertyId, propertyData)
                    .block(Duration.ofSeconds(10));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Property data saved successfully");
            result.put("transactionId", transactionId);
            if (propertyId != null) result.put("propertyId", propertyId);
            return result;
        } catch (Exception e) {
            log.error("Error executing LoanApplication tool saveProperty: {}", e.getMessage(), e);
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("status", "FAILED");
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }

    /**
     * Composite helper method: Save Extracted Application Data across applicant, application, and property tables.
     */
    public Map<String, Object> saveApplicationData(
            String applicationId,
            String userId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    ) {
        log.info("Executing LoanApplication composite saveApplicationData for applicationId: {}, userId: {}", applicationId, userId);
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
