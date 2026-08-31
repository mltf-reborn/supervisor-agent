package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentItem;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SpannerLoanApplicationRepository implements LoanApplicationRepository {

    private static final Logger log = LoggerFactory.getLogger(SpannerLoanApplicationRepository.class);

    private final DatabaseClient databaseClient;
    private final SupervisorAgentProperties properties;
    private double similarityThreshold = 0.80;

    @Autowired
    public SpannerLoanApplicationRepository(
            DatabaseClient databaseClient,
            SupervisorAgentProperties properties
    ) {
        this.databaseClient = databaseClient;
        this.properties = properties != null ? properties : new SupervisorAgentProperties();
        if (this.properties.getSimilarityThreshold() > 0) {
            this.similarityThreshold = this.properties.getSimilarityThreshold();
        }
    }

    public SpannerLoanApplicationRepository(DatabaseClient databaseClient) {
        this(databaseClient, null);
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public Mono<Boolean> existsByUserIdAndStatus(String userId, String status) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                            "SELECT transaction_id FROM application " +
                                    "WHERE user_id = @userId AND status = @status LIMIT 1")
                    .bind("userId").to(userId)
                    .bind("status").to(status)
                    .build();

            try (ResultSet resultSet = databaseClient.singleUse().executeQuery(statement)) {
                return resultSet.next();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> existsByTransactionIdAndUserId(String transactionId, String userId) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                            "SELECT transaction_id FROM application " +
                                    "WHERE transaction_id = @transactionId AND user_id = @userId LIMIT 1")
                    .bind("transactionId").to(transactionId)
                    .bind("userId").to(userId)
                    .build();

            try (ResultSet resultSet = databaseClient.singleUse().executeQuery(statement)) {
                return resultSet.next();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<ApplicationSummaryResponse>> findSummariesByUserId(String userId) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                            "SELECT a.transaction_id, a.application_date, a.facility_purpose, " +
                                    "p.project_name, p.spa_price_rm, a.application_type, a.status " +
                                    "FROM application a LEFT JOIN property p " +
                                    "ON a.transaction_id = p.transaction_id " +
                                    "WHERE a.user_id = @userId ORDER BY a.application_date DESC")
                    .bind("userId").to(userId)
                    .build();

            List<ApplicationSummaryResponse> applications = new ArrayList<>();
            try (ResultSet resultSet = databaseClient.singleUse().executeQuery(statement)) {
                while (resultSet.next()) {
                    com.google.cloud.spanner.Struct row = resultSet.getCurrentRowAsStruct();
                    applications.add(ApplicationSummaryResponseMapper.from(row));
                }
            }
            return applications;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ApplicationInquiryResponse> findInquiryByTransactionIdAndUserId(String transactionId, String userId) {
        return Mono.fromCallable(() -> {
            Statement applicationStatement = Statement.newBuilder(
                            "SELECT transaction_id, status FROM application " +
                                    "WHERE transaction_id = @transactionId AND user_id = @userId")
                    .bind("transactionId").to(transactionId)
                    .bind("userId").to(userId)
                    .build();

            try (ResultSet appResultSet = databaseClient.singleUse().executeQuery(applicationStatement)) {
                if (!appResultSet.next()) {
                    return null;
                }

                String status = appResultSet.getCurrentRowAsStruct().getString("status");
                Statement docsStatement = Statement.newBuilder(
                                "SELECT document_id, document_filename, document_status, document_message " +
                                        "FROM document WHERE transaction_id = @transactionId ORDER BY created_at ASC")
                        .bind("transactionId").to(transactionId)
                        .build();

                List<ApplicationDocumentItem> documents = new ArrayList<>();
                try (ResultSet docResultSet = databaseClient.singleUse().executeQuery(docsStatement)) {
                    while (docResultSet.next()) {
                        com.google.cloud.spanner.Struct row = docResultSet.getCurrentRowAsStruct();
                        documents.add(new ApplicationDocumentItem(
                                row.getString("document_id"),
                                row.isNull("document_filename") ? null : row.getString("document_filename"),
                                row.isNull("document_status") ? null : row.getString("document_status"),
                                row.isNull("document_message") ? null : row.getString("document_message")
                        ));
                    }
                }

                return new ApplicationInquiryResponse(transactionId, status, documents);
            }
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Map<String, Object>> getApplicationDetails(String transactionId, String userId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>();

            // 1. Get Application
            Statement appStatement = Statement.newBuilder(
                    "SELECT bank_selection, application_type, status, facility_type, facility_purpose, " +
                    "facilities_required, refinancing_bank, joint_relationship, marketing_consent, docs_enclosed, " +
                    "ftfc_category, signatures, application_date, ai_analysis FROM application WHERE transaction_id = @transactionId AND user_id = @userId")
                    .bind("transactionId").to(transactionId)
                    .bind("userId").to(userId)
                    .build();

            Map<String, Object> appData = new LinkedHashMap<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(appStatement)) {
                if (rs.next()) {
                    com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                    appData.put("bank_selection", row.isNull("bank_selection") ? "" : row.getString("bank_selection"));
                    appData.put("application_type", row.isNull("application_type") ? "" : row.getString("application_type"));
                    appData.put("status", row.isNull("status") ? "" : row.getString("status"));
                    appData.put("facility_type", row.isNull("facility_type") ? "" : row.getString("facility_type"));
                    appData.put("facility_purpose", row.isNull("facility_purpose") ? "" : row.getString("facility_purpose"));
                    appData.put("facilities_required", row.isNull("facilities_required") ? "" : row.getString("facilities_required"));
                    appData.put("refinancing_bank", row.isNull("refinancing_bank") ? "" : row.getString("refinancing_bank"));
                    appData.put("joint_relationship", row.isNull("joint_relationship") ? "" : row.getString("joint_relationship"));
                    appData.put("marketing_consent", row.isNull("marketing_consent") ? "" : row.getString("marketing_consent"));
                    appData.put("docs_enclosed", row.isNull("docs_enclosed") ? "" : row.getString("docs_enclosed"));
                    appData.put("ftfc_category", row.isNull("ftfc_category") ? "" : row.getString("ftfc_category"));
                    appData.put("signatures", row.isNull("signatures") ? "" : row.getString("signatures"));
                    appData.put("application_date", row.isNull("application_date") ? "" : row.getDate("application_date").toString());
                    appData.put("ai_analysis", row.isNull("ai_analysis") ? "" : row.getString("ai_analysis"));
                } else {
                    return null; // Not found or not authorized
                }
            }
            result.put("application", appData);

            // 2. Get Applicants (Primary and Joint)
            Statement applicantStatement = Statement.newBuilder(
                    "SELECT role, salutation, full_name, id_type, id_no, other_id_type, nationality, race, " +
                    "country_of_origin, bumiputera_status, gender, marital_status, date_of_birth, age, " +
                    "dependents_count, schooling_children_count, education_level, resident_type, mobile_phone, " +
                    "residential_phone, email, residence_type, perm_address, perm_address_line2, perm_postcode, " +
                    "perm_city, perm_state, perm_country, length_of_stay_years, length_of_stay_months, mail_address, " +
                    "mail_address_line2, mail_postcode, mail_city, mail_state, mail_country, employment_status, " +
                    "employer_name, employer_address, employer_address_line2, employer_postcode, employer_city, " +
                    "employer_state, employer_country, office_phone, direct_line, email_work, nature_of_business, " +
                    "nature_of_business_specify, occupation, job_position, date_joined, length_of_service_years, " +
                    "length_of_service_months, prev_employment_status, prev_employer_name, prev_nature_of_business, " +
                    "prev_occupation, prev_position, prev_phone, prev_service_years, prev_service_months, " +
                    "monthly_gross_rm, other_monthly_income_rm, annual_gross_rm, other_annual_income_rm, " +
                    "emergency_name, emergency_relationship, emergency_phone, emergency_phone_home, emergency_email, " +
                    "spouse_salutation, spouse_full_name, spouse_id_type, spouse_id_no, spouse_other_id_type, " +
                    "spouse_nationality, spouse_race, spouse_country_of_origin, spouse_bumiputera_status, " +
                    "spouse_gender, spouse_date_of_birth, spouse_age, spouse_mobile, spouse_residential_phone, " +
                    "spouse_email, spouse_employer, spouse_nature_of_business, spouse_occupation, spouse_position, " +
                    "spouse_general_line, spouse_service_years, spouse_monthly_gross_rm, spouse_annual_gross_rm, " +
                    "other_commitments, close_relatives, close_relations_staff, close_relations_relative " +
                    "FROM applicant WHERE transaction_id = @transactionId")
                    .bind("transactionId").to(transactionId)
                    .build();

            Map<String, Object> primaryApplicantData = new LinkedHashMap<>();
            Map<String, Object> jointApplicantData = null;
            try (ResultSet rs = databaseClient.singleUse().executeQuery(applicantStatement)) {
                while (rs.next()) {
                    com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                    Map<String, Object> appMap = mapApplicantStruct(row);
                    String role = row.isNull("role") ? "Primary" : row.getString("role");
                    if ("Joint".equalsIgnoreCase(role)) {
                        jointApplicantData = appMap;
                    } else if (primaryApplicantData.isEmpty()) {
                        primaryApplicantData = appMap;
                    }
                }
            }
            result.put("applicant", primaryApplicantData);
            if (jointApplicantData != null) {
                result.put("joint_applicant", jointApplicantData);
            }

            // 3. Get Property
            Statement propertyStatement = Statement.newBuilder(
                    "SELECT property_type, property_sub_type, property_status, construction_stage, developer_name, " +
                    "project_name, relationship_to_developer, phase_code, contractor_name, spa_price_rm, open_market_rm, " +
                    "renovation_value_rm, property_address, property_address_line2, property_postcode, property_city, " +
                    "property_state, property_country, title_number, title_type, lot_number, mukim, district, " +
                    "state_geran, is_owner_occupied, is_first_time_buyer, gross_purchase_price_rm, discount_rm, " +
                    "rebate_rm, adjustment_rm, developer_benefits_rm, net_purchase_price_rm " +
                    "FROM property WHERE transaction_id = @transactionId")
                    .bind("transactionId").to(transactionId)
                    .build();

            Map<String, Object> propertyData = new LinkedHashMap<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(propertyStatement)) {
                if (rs.next()) {
                    com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                    propertyData.put("property_type", row.isNull("property_type") ? "" : row.getString("property_type"));
                    propertyData.put("property_sub_type", row.isNull("property_sub_type") ? "" : row.getString("property_sub_type"));
                    propertyData.put("property_status", row.isNull("property_status") ? "" : row.getString("property_status"));
                    propertyData.put("construction_stage", row.isNull("construction_stage") ? "" : row.getString("construction_stage"));
                    propertyData.put("developer_name", row.isNull("developer_name") ? "" : row.getString("developer_name"));
                    propertyData.put("project_name", row.isNull("project_name") ? "" : row.getString("project_name"));
                    propertyData.put("relationship_to_developer", row.isNull("relationship_to_developer") ? "" : row.getString("relationship_to_developer"));
                    propertyData.put("phase_code", row.isNull("phase_code") ? "" : row.getString("phase_code"));
                    propertyData.put("contractor_name", row.isNull("contractor_name") ? "" : row.getString("contractor_name"));
                    propertyData.put("spa_price_rm", row.isNull("spa_price_rm") ? null : row.getBigDecimal("spa_price_rm"));
                    propertyData.put("open_market_rm", row.isNull("open_market_rm") ? null : row.getBigDecimal("open_market_rm"));
                    propertyData.put("renovation_value_rm", row.isNull("renovation_value_rm") ? null : row.getBigDecimal("renovation_value_rm"));
                    propertyData.put("property_address", row.isNull("property_address") ? "" : row.getString("property_address"));
                    propertyData.put("property_address_line2", row.isNull("property_address_line2") ? "" : row.getString("property_address_line2"));
                    propertyData.put("property_postcode", row.isNull("property_postcode") ? "" : row.getString("property_postcode"));
                    propertyData.put("property_city", row.isNull("property_city") ? "" : row.getString("property_city"));
                    propertyData.put("property_state", row.isNull("property_state") ? "" : row.getString("property_state"));
                    propertyData.put("property_country", row.isNull("property_country") ? "" : row.getString("property_country"));
                    propertyData.put("title_number", row.isNull("title_number") ? "" : row.getString("title_number"));
                    propertyData.put("title_type", row.isNull("title_type") ? "" : row.getString("title_type"));
                    propertyData.put("lot_number", row.isNull("lot_number") ? "" : row.getString("lot_number"));
                    propertyData.put("mukim", row.isNull("mukim") ? "" : row.getString("mukim"));
                    propertyData.put("district", row.isNull("district") ? "" : row.getString("district"));
                    propertyData.put("state_geran", row.isNull("state_geran") ? "" : row.getString("state_geran"));
                    propertyData.put("is_owner_occupied", row.isNull("is_owner_occupied") ? null : row.getBoolean("is_owner_occupied"));
                    propertyData.put("is_first_time_buyer", row.isNull("is_first_time_buyer") ? null : row.getBoolean("is_first_time_buyer"));
                    propertyData.put("gross_purchase_price_rm", row.isNull("gross_purchase_price_rm") ? null : row.getBigDecimal("gross_purchase_price_rm"));
                    propertyData.put("discount_rm", row.isNull("discount_rm") ? null : row.getBigDecimal("discount_rm"));
                    propertyData.put("rebate_rm", row.isNull("rebate_rm") ? null : row.getBigDecimal("rebate_rm"));
                    propertyData.put("adjustment_rm", row.isNull("adjustment_rm") ? null : row.getBigDecimal("adjustment_rm"));
                    propertyData.put("developer_benefits_rm", row.isNull("developer_benefits_rm") ? null : row.getBigDecimal("developer_benefits_rm"));
                    propertyData.put("net_purchase_price_rm", row.isNull("net_purchase_price_rm") ? null : row.getBigDecimal("net_purchase_price_rm"));
                }
            }
            result.put("property", propertyData);

            return result;
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Void> create(String transactionId, String userId, String applicationType, KycProfile kycProfile) {
        return Mono.fromRunnable(() -> {
            Mutation applicationMutation = Mutation.newInsertBuilder("application")
                    .set("transaction_id").to(transactionId)
                    .set("user_id").to(userId)
                    .set("application_type").to(applicationType)
                    .set("status").to("NEW")
                    .set("application_date").to(com.google.cloud.Date.fromYearMonthDay(
                            java.time.LocalDate.now().getYear(),
                            java.time.LocalDate.now().getMonthValue(),
                            java.time.LocalDate.now().getDayOfMonth()))
                    .set("created_at").to(Value.COMMIT_TIMESTAMP)
                    .build();

            Mutation.WriteBuilder applicantBuilder = Mutation.newInsertBuilder("applicant")
                    .set("transaction_id").to(transactionId)
                    .set("applicant_id").to(userId)
                    .set("role").to("Primary")
                    .set("full_name").to(kycProfile.fullName())
                    .set("id_type").to(kycProfile.idCardType())
                    .set("id_no").to(kycProfile.idCardNumber())
                    .set("nationality").to(kycProfile.nationality())
                    .set("mobile_phone").to(kycProfile.phoneNumber())
                    .set("email").to(kycProfile.email())
                    .set("perm_address").to(kycProfile.address())
                    .set("perm_postcode").to(kycProfile.postalCode())
                    .set("perm_city").to(kycProfile.city())
                    .set("occupation").to(kycProfile.occupation())
                    .set("monthly_gross_rm").to(kycProfile.monthlyIncome());

            if (kycProfile.dateOfBirth() != null) {
                applicantBuilder.set("date_of_birth").to(com.google.cloud.Date.fromYearMonthDay(
                        kycProfile.dateOfBirth().getYear(),
                        kycProfile.dateOfBirth().getMonthValue(),
                        kycProfile.dateOfBirth().getDayOfMonth()));
            }

            Mutation applicantMutation = applicantBuilder.build();

            databaseClient.write(java.util.List.of(applicationMutation, applicantMutation));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Boolean> deleteByTransactionIdAndUserId(String transactionId, String userId) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                            "DELETE FROM application WHERE transaction_id = @transactionId AND user_id = @userId")
                    .bind("transactionId").to(transactionId)
                    .bind("userId").to(userId)
                    .build();

            long deletedRows = databaseClient.readWriteTransaction()
                    .run(transaction -> transaction.executeUpdate(statement));
            return deletedRows > 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateApplicationData(
            String transactionId,
            String userId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    ) {
        return Mono.fromRunnable(() -> {
            List<Mutation> mutations = new ArrayList<>();

            // 1. Applicant table update/insert (Primary)
            if (applicantData != null && !applicantData.isEmpty()) {
                String applicantId = extractString(applicantData, "applicant_id", "applicantId", "user_id", "userId");
                if (applicantId == null || applicantId.isBlank()) {
                    applicantId = userId;
                }

                // Check if primary applicant record exists
                Statement checkApplicant = Statement.newBuilder(
                                "SELECT * FROM applicant " +
                                "WHERE transaction_id = @transactionId AND applicant_id = @applicantId")
                        .bind("transactionId").to(transactionId)
                        .bind("applicantId").to(applicantId)
                        .build();

                boolean applicantExists = false;
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApplicant)) {
                    if (rs.next()) {
                        applicantExists = true;
                        validateApplicantSimilarity(rs.getCurrentRowAsStruct(), applicantData);
                    }
                }

                mutations.add(buildApplicantMutation(transactionId, applicantId, "Primary", applicantExists, applicantData));

                // Check if joint applicant data is also present
                @SuppressWarnings("unchecked")
                Map<String, Object> jointData = (Map<String, Object>) applicantData.getOrDefault("joint_applicant", 
                        applicantData.get("jointApplicant"));
                if (jointData != null && !jointData.isEmpty()) {
                    String jointApplicantId = extractString(jointData, "applicant_id", "applicantId");
                    if (jointApplicantId == null || jointApplicantId.isBlank()) {
                        jointApplicantId = applicantId + "_joint";
                    }

                    Statement checkJoint = Statement.newBuilder(
                                    "SELECT * FROM applicant " +
                                    "WHERE transaction_id = @transactionId AND applicant_id = @applicantId")
                            .bind("transactionId").to(transactionId)
                            .bind("applicantId").to(jointApplicantId)
                            .build();

                    boolean jointExists = false;
                    try (ResultSet rs = databaseClient.singleUse().executeQuery(checkJoint)) {
                        if (rs.next()) {
                            jointExists = true;
                        }
                    }
                    mutations.add(buildApplicantMutation(transactionId, jointApplicantId, "Joint", jointExists, jointData));
                }
            }

            // 2. Application table update
            if (applicationData != null && !applicationData.isEmpty()) {
                Statement checkApp = Statement.newBuilder(
                                "SELECT * FROM application WHERE transaction_id = @transactionId")
                        .bind("transactionId").to(transactionId)
                        .build();

                boolean appExists = false;
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApp)) {
                    if (rs.next()) {
                        appExists = true;
                        validateApplicationSimilarity(rs.getCurrentRowAsStruct(), applicationData);
                    }
                }

                mutations.add(buildApplicationMutation(transactionId, userId, appExists, applicationData));
            }

            // 3. Property table update/insert
            if (propertyData != null && !propertyData.isEmpty()) {
                String propertyId = extractString(propertyData, "property_id", "propertyId");
                
                if (propertyId == null || propertyId.isBlank()) {
                    Statement checkProperty = Statement.newBuilder(
                                    "SELECT property_id FROM property WHERE transaction_id = @transactionId LIMIT 1")
                            .bind("transactionId").to(transactionId)
                            .build();

                    try (ResultSet rs = databaseClient.singleUse().executeQuery(checkProperty)) {
                        if (rs.next()) {
                            propertyId = rs.getString("property_id");
                        }
                    }
                }

                boolean propertyExists = false;
                if (propertyId != null && !propertyId.isBlank()) {
                    Statement checkSpecificProperty = Statement.newBuilder(
                                    "SELECT * FROM property " +
                                    "WHERE transaction_id = @transactionId AND property_id = @propertyId")
                            .bind("transactionId").to(transactionId)
                            .bind("propertyId").to(propertyId)
                            .build();
                    try (ResultSet rs = databaseClient.singleUse().executeQuery(checkSpecificProperty)) {
                        if (rs.next()) {
                            propertyExists = true;
                            validatePropertySimilarity(rs.getCurrentRowAsStruct(), propertyData);
                        }
                    }
                }

                if (propertyId == null || propertyId.isBlank()) {
                    propertyId = "PROP-" + UUID.randomUUID().toString();
                    propertyExists = false;
                }

                mutations.add(buildPropertyMutation(transactionId, propertyId, propertyExists, propertyData));
            }

            if (!mutations.isEmpty()) {
                log.info("Executing {} database mutations to update application tables for transaction: {}",
                        mutations.size(), transactionId);
                databaseClient.write(mutations);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> saveApplication(String transactionId, String userId, Map<String, Object> applicationData) {
        return Mono.fromRunnable(() -> {
            if (applicationData == null || applicationData.isEmpty()) {
                return;
            }

            Statement checkApp = Statement.newBuilder(
                            "SELECT * FROM application WHERE transaction_id = @transactionId")
                    .bind("transactionId").to(transactionId)
                    .build();

            boolean exists = false;
            try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApp)) {
                if (rs.next()) {
                    exists = true;
                    validateApplicationSimilarity(rs.getCurrentRowAsStruct(), applicationData);
                }
            }

            log.info("Executing database mutation to update 'application' table for transaction: {}", transactionId);
            databaseClient.write(List.of(buildApplicationMutation(transactionId, userId, exists, applicationData)));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> saveApplicant(String transactionId, String applicantId, Map<String, Object> applicantData) {
        return Mono.fromRunnable(() -> {
            if (applicantData == null || applicantData.isEmpty()) {
                return;
            }
            String resolvedApplicantId = applicantId;
            if (resolvedApplicantId == null || resolvedApplicantId.isBlank()) {
                resolvedApplicantId = extractString(applicantData, "applicant_id", "applicantId", "user_id", "userId");
            }
            if (resolvedApplicantId == null || resolvedApplicantId.isBlank()) {
                Statement checkApplicantAny = Statement.newBuilder(
                                "SELECT applicant_id FROM applicant WHERE transaction_id = @transactionId LIMIT 1")
                        .bind("transactionId").to(transactionId)
                        .build();
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApplicantAny)) {
                    if (rs.next()) {
                        resolvedApplicantId = rs.getString("applicant_id");
                    }
                }
            }
            if (resolvedApplicantId == null || resolvedApplicantId.isBlank()) {
                Statement checkUser = Statement.newBuilder(
                                "SELECT user_id FROM application WHERE transaction_id = @transactionId LIMIT 1")
                        .bind("transactionId").to(transactionId)
                        .build();
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkUser)) {
                    if (rs.next()) {
                        resolvedApplicantId = rs.getString("user_id");
                    }
                }
            }
            if (resolvedApplicantId == null || resolvedApplicantId.isBlank()) {
                resolvedApplicantId = "usr_primary";
            }

            Statement checkApplicant = Statement.newBuilder(
                            "SELECT * FROM applicant " +
                            "WHERE transaction_id = @transactionId AND applicant_id = @applicantId")
                    .bind("transactionId").to(transactionId)
                    .bind("applicantId").to(resolvedApplicantId)
                    .build();

            boolean applicantExists = false;
            try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApplicant)) {
                if (rs.next()) {
                    applicantExists = true;
                    validateApplicantSimilarity(rs.getCurrentRowAsStruct(), applicantData);
                }
            }

            String role = extractString(applicantData, "role");
            if (role == null || role.isBlank()) {
                role = "Primary";
            }

            log.info("Executing database mutation to update 'applicant' table for transaction: {}, applicantId: {}", transactionId, resolvedApplicantId);
            databaseClient.write(List.of(buildApplicantMutation(transactionId, resolvedApplicantId, role, applicantExists, applicantData)));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> saveProperty(String transactionId, String propertyId, Map<String, Object> propertyData) {
        return Mono.fromRunnable(() -> {
            if (propertyData == null || propertyData.isEmpty()) {
                return;
            }
            String resolvedPropertyId = propertyId;
            if (resolvedPropertyId == null || resolvedPropertyId.isBlank()) {
                resolvedPropertyId = extractString(propertyData, "property_id", "propertyId");
            }

            // Check if property record exists for transaction_id
            if (resolvedPropertyId == null || resolvedPropertyId.isBlank()) {
                Statement checkProperty = Statement.newBuilder(
                                "SELECT property_id FROM property WHERE transaction_id = @transactionId LIMIT 1")
                        .bind("transactionId").to(transactionId)
                        .build();

                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkProperty)) {
                    if (rs.next()) {
                        resolvedPropertyId = rs.getString("property_id");
                    }
                }
            }

            boolean propertyExists = false;
            if (resolvedPropertyId != null && !resolvedPropertyId.isBlank()) {
                Statement checkSpecific = Statement.newBuilder(
                                "SELECT * FROM property " +
                                "WHERE transaction_id = @transactionId AND property_id = @propertyId")
                        .bind("transactionId").to(transactionId)
                        .bind("propertyId").to(resolvedPropertyId)
                        .build();
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkSpecific)) {
                    if (rs.next()) {
                        propertyExists = true;
                        validatePropertySimilarity(rs.getCurrentRowAsStruct(), propertyData);
                    }
                }
            }

            if (resolvedPropertyId == null || resolvedPropertyId.isBlank()) {
                resolvedPropertyId = "PROP-" + UUID.randomUUID().toString();
                propertyExists = false;
            }

            log.info("Executing database mutation to update 'property' table for transaction: {}, propertyId: {}", transactionId, resolvedPropertyId);
            databaseClient.write(List.of(buildPropertyMutation(transactionId, resolvedPropertyId, propertyExists, propertyData)));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Map<String, Object> mapApplicantStruct(com.google.cloud.spanner.Struct row) {
        Map<String, Object> applicantData = new LinkedHashMap<>();
        applicantData.put("role", row.isNull("role") ? "" : row.getString("role"));
        applicantData.put("salutation", row.isNull("salutation") ? "" : row.getString("salutation"));
        applicantData.put("full_name", row.isNull("full_name") ? "" : row.getString("full_name"));
        applicantData.put("id_type", row.isNull("id_type") ? "" : row.getString("id_type"));
        applicantData.put("id_no", row.isNull("id_no") ? "" : row.getString("id_no"));
        applicantData.put("other_id_type", row.isNull("other_id_type") ? "" : row.getString("other_id_type"));
        applicantData.put("nationality", row.isNull("nationality") ? "" : row.getString("nationality"));
        applicantData.put("race", row.isNull("race") ? "" : row.getString("race"));
        applicantData.put("country_of_origin", row.isNull("country_of_origin") ? "" : row.getString("country_of_origin"));
        applicantData.put("bumiputera_status", row.isNull("bumiputera_status") ? null : row.getBoolean("bumiputera_status"));
        applicantData.put("gender", row.isNull("gender") ? "" : row.getString("gender"));
        applicantData.put("marital_status", row.isNull("marital_status") ? "" : row.getString("marital_status"));
        applicantData.put("date_of_birth", row.isNull("date_of_birth") ? "" : row.getDate("date_of_birth").toString());
        applicantData.put("age", row.isNull("age") ? null : row.getLong("age"));
        applicantData.put("dependents_count", row.isNull("dependents_count") ? null : row.getLong("dependents_count"));
        applicantData.put("schooling_children_count", row.isNull("schooling_children_count") ? null : row.getLong("schooling_children_count"));
        applicantData.put("education_level", row.isNull("education_level") ? "" : row.getString("education_level"));
        applicantData.put("resident_type", row.isNull("resident_type") ? "" : row.getString("resident_type"));
        applicantData.put("mobile_phone", row.isNull("mobile_phone") ? "" : row.getString("mobile_phone"));
        applicantData.put("residential_phone", row.isNull("residential_phone") ? "" : row.getString("residential_phone"));
        applicantData.put("email", row.isNull("email") ? "" : row.getString("email"));
        applicantData.put("residence_type", row.isNull("residence_type") ? "" : row.getString("residence_type"));
        applicantData.put("perm_address", row.isNull("perm_address") ? "" : row.getString("perm_address"));
        applicantData.put("perm_address_line2", row.isNull("perm_address_line2") ? "" : row.getString("perm_address_line2"));
        applicantData.put("perm_postcode", row.isNull("perm_postcode") ? "" : row.getString("perm_postcode"));
        applicantData.put("perm_city", row.isNull("perm_city") ? "" : row.getString("perm_city"));
        applicantData.put("perm_state", row.isNull("perm_state") ? "" : row.getString("perm_state"));
        applicantData.put("perm_country", row.isNull("perm_country") ? "" : row.getString("perm_country"));
        applicantData.put("length_of_stay_years", row.isNull("length_of_stay_years") ? null : row.getBigDecimal("length_of_stay_years"));
        applicantData.put("length_of_stay_months", row.isNull("length_of_stay_months") ? null : row.getBigDecimal("length_of_stay_months"));
        applicantData.put("mail_address", row.isNull("mail_address") ? "" : row.getString("mail_address"));
        applicantData.put("mail_address_line2", row.isNull("mail_address_line2") ? "" : row.getString("mail_address_line2"));
        applicantData.put("mail_postcode", row.isNull("mail_postcode") ? "" : row.getString("mail_postcode"));
        applicantData.put("mail_city", row.isNull("mail_city") ? "" : row.getString("mail_city"));
        applicantData.put("mail_state", row.isNull("mail_state") ? "" : row.getString("mail_state"));
        applicantData.put("mail_country", row.isNull("mail_country") ? "" : row.getString("mail_country"));
        applicantData.put("employment_status", row.isNull("employment_status") ? "" : row.getString("employment_status"));
        applicantData.put("employer_name", row.isNull("employer_name") ? "" : row.getString("employer_name"));
        applicantData.put("employer_address", row.isNull("employer_address") ? "" : row.getString("employer_address"));
        applicantData.put("employer_address_line2", row.isNull("employer_address_line2") ? "" : row.getString("employer_address_line2"));
        applicantData.put("employer_postcode", row.isNull("employer_postcode") ? "" : row.getString("employer_postcode"));
        applicantData.put("employer_city", row.isNull("employer_city") ? "" : row.getString("employer_city"));
        applicantData.put("employer_state", row.isNull("employer_state") ? "" : row.getString("employer_state"));
        applicantData.put("employer_country", row.isNull("employer_country") ? "" : row.getString("employer_country"));
        applicantData.put("office_phone", row.isNull("office_phone") ? "" : row.getString("office_phone"));
        applicantData.put("direct_line", row.isNull("direct_line") ? "" : row.getString("direct_line"));
        applicantData.put("email_work", row.isNull("email_work") ? "" : row.getString("email_work"));
        applicantData.put("nature_of_business", row.isNull("nature_of_business") ? "" : row.getString("nature_of_business"));
        applicantData.put("nature_of_business_specify", row.isNull("nature_of_business_specify") ? "" : row.getString("nature_of_business_specify"));
        applicantData.put("occupation", row.isNull("occupation") ? "" : row.getString("occupation"));
        applicantData.put("job_position", row.isNull("job_position") ? "" : row.getString("job_position"));
        applicantData.put("date_joined", row.isNull("date_joined") ? "" : row.getDate("date_joined").toString());
        applicantData.put("length_of_service_years", row.isNull("length_of_service_years") ? null : row.getBigDecimal("length_of_service_years"));
        applicantData.put("length_of_service_months", row.isNull("length_of_service_months") ? null : row.getBigDecimal("length_of_service_months"));
        applicantData.put("prev_employment_status", row.isNull("prev_employment_status") ? "" : row.getString("prev_employment_status"));
        applicantData.put("prev_employer_name", row.isNull("prev_employer_name") ? "" : row.getString("prev_employer_name"));
        applicantData.put("prev_nature_of_business", row.isNull("prev_nature_of_business") ? "" : row.getString("prev_nature_of_business"));
        applicantData.put("prev_occupation", row.isNull("prev_occupation") ? "" : row.getString("prev_occupation"));
        applicantData.put("prev_position", row.isNull("prev_position") ? "" : row.getString("prev_position"));
        applicantData.put("prev_phone", row.isNull("prev_phone") ? "" : row.getString("prev_phone"));
        applicantData.put("prev_service_years", row.isNull("prev_service_years") ? null : row.getBigDecimal("prev_service_years"));
        applicantData.put("prev_service_months", row.isNull("prev_service_months") ? null : row.getBigDecimal("prev_service_months"));
        applicantData.put("monthly_gross_rm", row.isNull("monthly_gross_rm") ? null : row.getBigDecimal("monthly_gross_rm"));
        applicantData.put("other_monthly_income_rm", row.isNull("other_monthly_income_rm") ? null : row.getBigDecimal("other_monthly_income_rm"));
        applicantData.put("annual_gross_rm", row.isNull("annual_gross_rm") ? null : row.getBigDecimal("annual_gross_rm"));
        applicantData.put("other_annual_income_rm", row.isNull("other_annual_income_rm") ? null : row.getBigDecimal("other_annual_income_rm"));
        applicantData.put("emergency_name", row.isNull("emergency_name") ? "" : row.getString("emergency_name"));
        applicantData.put("emergency_relationship", row.isNull("emergency_relationship") ? "" : row.getString("emergency_relationship"));
        applicantData.put("emergency_phone", row.isNull("emergency_phone") ? "" : row.getString("emergency_phone"));
        applicantData.put("emergency_phone_home", row.isNull("emergency_phone_home") ? "" : row.getString("emergency_phone_home"));
        applicantData.put("emergency_email", row.isNull("emergency_email") ? "" : row.getString("emergency_email"));
        applicantData.put("spouse_salutation", row.isNull("spouse_salutation") ? "" : row.getString("spouse_salutation"));
        applicantData.put("spouse_full_name", row.isNull("spouse_full_name") ? "" : row.getString("spouse_full_name"));
        applicantData.put("spouse_id_type", row.isNull("spouse_id_type") ? "" : row.getString("spouse_id_type"));
        applicantData.put("spouse_id_no", row.isNull("spouse_id_no") ? "" : row.getString("spouse_id_no"));
        applicantData.put("spouse_other_id_type", row.isNull("spouse_other_id_type") ? "" : row.getString("spouse_other_id_type"));
        applicantData.put("spouse_nationality", row.isNull("spouse_nationality") ? "" : row.getString("spouse_nationality"));
        applicantData.put("spouse_race", row.isNull("spouse_race") ? "" : row.getString("spouse_race"));
        applicantData.put("spouse_country_of_origin", row.isNull("spouse_country_of_origin") ? "" : row.getString("spouse_country_of_origin"));
        applicantData.put("spouse_bumiputera_status", row.isNull("spouse_bumiputera_status") ? null : row.getBoolean("spouse_bumiputera_status"));
        applicantData.put("spouse_gender", row.isNull("spouse_gender") ? "" : row.getString("spouse_gender"));
        applicantData.put("spouse_date_of_birth", row.isNull("spouse_date_of_birth") ? "" : row.getDate("spouse_date_of_birth").toString());
        applicantData.put("spouse_age", row.isNull("spouse_age") ? null : row.getLong("spouse_age"));
        applicantData.put("spouse_mobile", row.isNull("spouse_mobile") ? "" : row.getString("spouse_mobile"));
        applicantData.put("spouse_residential_phone", row.isNull("spouse_residential_phone") ? "" : row.getString("spouse_residential_phone"));
        applicantData.put("spouse_email", row.isNull("spouse_email") ? "" : row.getString("spouse_email"));
        applicantData.put("spouse_employer", row.isNull("spouse_employer") ? "" : row.getString("spouse_employer"));
        applicantData.put("spouse_nature_of_business", row.isNull("spouse_nature_of_business") ? "" : row.getString("spouse_nature_of_business"));
        applicantData.put("spouse_occupation", row.isNull("spouse_occupation") ? "" : row.getString("spouse_occupation"));
        applicantData.put("spouse_position", row.isNull("spouse_position") ? "" : row.getString("spouse_position"));
        applicantData.put("spouse_general_line", row.isNull("spouse_general_line") ? "" : row.getString("spouse_general_line"));
        applicantData.put("spouse_service_years", row.isNull("spouse_service_years") ? null : row.getBigDecimal("spouse_service_years"));
        applicantData.put("spouse_monthly_gross_rm", row.isNull("spouse_monthly_gross_rm") ? null : row.getBigDecimal("spouse_monthly_gross_rm"));
        applicantData.put("spouse_annual_gross_rm", row.isNull("spouse_annual_gross_rm") ? null : row.getBigDecimal("spouse_annual_gross_rm"));
        applicantData.put("other_commitments", row.isNull("other_commitments") ? "" : row.getString("other_commitments"));
        applicantData.put("close_relatives", row.isNull("close_relatives") ? "" : row.getString("close_relatives"));
        applicantData.put("close_relations_staff", row.isNull("close_relations_staff") ? null : row.getBoolean("close_relations_staff"));
        applicantData.put("close_relations_relative", row.isNull("close_relations_relative") ? null : row.getBoolean("close_relations_relative"));
        return applicantData;
    }

    private Mutation buildApplicantMutation(String transactionId, String applicantId, String role, boolean exists, Map<String, Object> applicantData) {
        Mutation.WriteBuilder b = exists
                ? Mutation.newUpdateBuilder("applicant")
                : Mutation.newInsertBuilder("applicant");

        b.set("transaction_id").to(transactionId);
        b.set("applicant_id").to(applicantId);

        String specifiedRole = extractString(applicantData, "role");
        if (specifiedRole != null && !specifiedRole.isBlank()) {
            b.set("role").to(specifiedRole.trim());
        } else if (role != null && !role.isBlank()) {
            b.set("role").to(role.trim());
        } else if (!exists) {
            b.set("role").to("Primary");
        }

        setIfPresent(b, "salutation", extractString(applicantData, "salutation"));
        setIfPresent(b, "full_name", extractString(applicantData, "full_name", "fullName", "name"));
        setIfPresent(b, "id_type", extractString(applicantData, "id_type", "idType"));
        setIfPresent(b, "id_no", extractString(applicantData, "id_no", "idNo", "idNumber", "newNric", "oldNric", "passportNo", "otherIdNo"));
        setIfPresent(b, "other_id_type", extractString(applicantData, "other_id_type", "otherIdType"));
        setIfPresent(b, "nationality", extractString(applicantData, "nationality"));
        setIfPresent(b, "race", extractString(applicantData, "race"));
        setIfPresent(b, "country_of_origin", extractString(applicantData, "country_of_origin", "countryOfOrigin"));
        setIfPresent(b, "bumiputera_status", extractBoolean(applicantData, "bumiputera_status", "bumiputeraStatus", "isBumiputera"));
        setIfPresent(b, "gender", extractString(applicantData, "gender", "sex"));
        setIfPresent(b, "marital_status", extractString(applicantData, "marital_status", "maritalStatus"));
        setIfPresent(b, "date_of_birth", extractDate(applicantData, "date_of_birth", "dateOfBirth", "dob"));
        setIfPresent(b, "age", extractLong(applicantData, "age"));
        setIfPresent(b, "dependents_count", extractLong(applicantData, "dependents_count", "dependentsCount"));
        setIfPresent(b, "schooling_children_count", extractLong(applicantData, "schooling_children_count", "schoolingChildrenCount"));
        setIfPresent(b, "education_level", extractString(applicantData, "education_level", "educationLevel"));
        setIfPresent(b, "resident_type", extractString(applicantData, "resident_type", "residentType"));
        setIfPresent(b, "mobile_phone", extractString(applicantData, "mobile_phone", "mobilePhone", "phoneNumber", "phoneMobile", "mobile"));
        setIfPresent(b, "residential_phone", extractString(applicantData, "residential_phone", "residentialPhone", "phoneHome", "homePhone"));
        setIfPresent(b, "email", extractString(applicantData, "email"));
        setIfPresent(b, "residence_type", extractString(applicantData, "residence_type", "residenceType"));
        setIfPresent(b, "perm_address", extractString(applicantData, "perm_address", "permAddress", "address", "addressLine1", "address_line1"));
        setIfPresent(b, "perm_address_line2", extractString(applicantData, "perm_address_line2", "permAddressLine2", "addressLine2", "address_line2"));
        setIfPresent(b, "perm_postcode", extractString(applicantData, "perm_postcode", "permPostcode", "postalCode", "postcode"));
        setIfPresent(b, "perm_city", extractString(applicantData, "perm_city", "permCity", "city"));
        setIfPresent(b, "perm_state", extractString(applicantData, "perm_state", "permState", "state"));
        setIfPresent(b, "perm_country", extractString(applicantData, "perm_country", "permCountry", "country"));
        setIfPresent(b, "length_of_stay_years", extractBigDecimal(applicantData, "length_of_stay_years", "lengthOfStayYears"));
        setIfPresent(b, "length_of_stay_months", extractBigDecimal(applicantData, "length_of_stay_months", "lengthOfStayMonths"));
        setIfPresent(b, "mail_address", extractString(applicantData, "mail_address", "mailAddress", "mailingAddress", "mailingAddressLine1", "mailing_address_line1"));
        setIfPresent(b, "mail_address_line2", extractString(applicantData, "mail_address_line2", "mailAddressLine2", "mailingAddressLine2", "mailing_address_line2"));
        setIfPresent(b, "mail_postcode", extractString(applicantData, "mail_postcode", "mailPostcode", "mailingPostcode"));
        setIfPresent(b, "mail_city", extractString(applicantData, "mail_city", "mailCity", "mailingCity"));
        setIfPresent(b, "mail_state", extractString(applicantData, "mail_state", "mailState", "mailingState"));
        setIfPresent(b, "mail_country", extractString(applicantData, "mail_country", "mailCountry", "mailingCountry"));
        setIfPresent(b, "employment_status", extractString(applicantData, "employment_status", "employmentStatus"));
        setIfPresent(b, "employer_name", extractString(applicantData, "employer_name", "employerName"));
        setIfPresent(b, "employer_address", extractString(applicantData, "employer_address", "employerAddress", "employerAddressLine1", "employer_address_line1"));
        setIfPresent(b, "employer_address_line2", extractString(applicantData, "employer_address_line2", "employerAddressLine2", "employer_address_line2"));
        setIfPresent(b, "employer_postcode", extractString(applicantData, "employer_postcode", "employerPostcode"));
        setIfPresent(b, "employer_city", extractString(applicantData, "employer_city", "employerCity"));
        setIfPresent(b, "employer_state", extractString(applicantData, "employer_state", "employerState"));
        setIfPresent(b, "employer_country", extractString(applicantData, "employer_country", "employerCountry"));
        setIfPresent(b, "office_phone", extractString(applicantData, "office_phone", "officePhone"));
        setIfPresent(b, "direct_line", extractString(applicantData, "direct_line", "directLine"));
        setIfPresent(b, "email_work", extractString(applicantData, "email_work", "emailWork", "workEmail"));
        setIfPresent(b, "nature_of_business", extractString(applicantData, "nature_of_business", "natureOfBusiness"));
        setIfPresent(b, "nature_of_business_specify", extractString(applicantData, "nature_of_business_specify", "natureOfBusinessSpecify"));
        setIfPresent(b, "occupation", extractString(applicantData, "occupation"));
        setIfPresent(b, "job_position", extractString(applicantData, "job_position", "jobPosition", "position"));
        setIfPresent(b, "date_joined", extractDate(applicantData, "date_joined", "dateJoined"));
        setIfPresent(b, "length_of_service_years", extractBigDecimal(applicantData, "length_of_service_years", "lengthOfServiceYears", "serviceYears"));
        setIfPresent(b, "length_of_service_months", extractBigDecimal(applicantData, "length_of_service_months", "lengthOfServiceMonths", "serviceMonths"));
        setIfPresent(b, "prev_employment_status", extractString(applicantData, "prev_employment_status", "prevEmploymentStatus"));
        setIfPresent(b, "prev_employer_name", extractString(applicantData, "prev_employer_name", "prevEmployerName"));
        setIfPresent(b, "prev_nature_of_business", extractString(applicantData, "prev_nature_of_business", "prevNatureOfBusiness"));
        setIfPresent(b, "prev_occupation", extractString(applicantData, "prev_occupation", "prevOccupation"));
        setIfPresent(b, "prev_position", extractString(applicantData, "prev_position", "prevPosition"));
        setIfPresent(b, "prev_phone", extractString(applicantData, "prev_phone", "prevPhone"));
        setIfPresent(b, "prev_service_years", extractBigDecimal(applicantData, "prev_service_years", "prevServiceYears"));
        setIfPresent(b, "prev_service_months", extractBigDecimal(applicantData, "prev_service_months", "prevServiceMonths"));
        setIfPresent(b, "monthly_gross_rm", extractBigDecimal(applicantData, "monthly_gross_rm", "monthlyGrossRm", "monthlyGrossIncome", "monthlyIncome", "grossIncome"));
        setIfPresent(b, "other_monthly_income_rm", extractBigDecimal(applicantData, "other_monthly_income_rm", "otherMonthlyIncomeRm", "otherMonthlyIncome"));
        setIfPresent(b, "annual_gross_rm", extractBigDecimal(applicantData, "annual_gross_rm", "annualGrossRm", "annualGrossIncome", "annualIncome"));
        setIfPresent(b, "other_annual_income_rm", extractBigDecimal(applicantData, "other_annual_income_rm", "otherAnnualIncomeRm", "otherAnnualIncome"));
        setIfPresent(b, "emergency_name", extractString(applicantData, "emergency_name", "emergencyName", "emergencyFullName"));
        setIfPresent(b, "emergency_relationship", extractString(applicantData, "emergency_relationship", "emergencyRelationship"));
        setIfPresent(b, "emergency_phone", extractString(applicantData, "emergency_phone", "emergencyPhone", "emergencyMobilePhone"));
        setIfPresent(b, "emergency_phone_home", extractString(applicantData, "emergency_phone_home", "emergencyPhoneHome"));
        setIfPresent(b, "emergency_email", extractString(applicantData, "emergency_email", "emergencyEmail"));
        setIfPresent(b, "spouse_salutation", extractString(applicantData, "spouse_salutation", "spouseSalutation"));
        setIfPresent(b, "spouse_full_name", extractString(applicantData, "spouse_full_name", "spouseFullName", "spouseName"));
        setIfPresent(b, "spouse_id_type", extractString(applicantData, "spouse_id_type", "spouseIdType"));
        setIfPresent(b, "spouse_id_no", extractString(applicantData, "spouse_id_no", "spouseIdNo"));
        setIfPresent(b, "spouse_other_id_type", extractString(applicantData, "spouse_other_id_type", "spouseOtherIdType"));
        setIfPresent(b, "spouse_nationality", extractString(applicantData, "spouse_nationality", "spouseNationality"));
        setIfPresent(b, "spouse_race", extractString(applicantData, "spouse_race", "spouseRace"));
        setIfPresent(b, "spouse_country_of_origin", extractString(applicantData, "spouse_country_of_origin", "spouseCountryOfOrigin"));
        setIfPresent(b, "spouse_bumiputera_status", extractBoolean(applicantData, "spouse_bumiputera_status", "spouseBumiputeraStatus"));
        setIfPresent(b, "spouse_gender", extractString(applicantData, "spouse_gender", "spouseGender"));
        setIfPresent(b, "spouse_date_of_birth", extractDate(applicantData, "spouse_date_of_birth", "spouseDateOfBirth", "spouseDob"));
        setIfPresent(b, "spouse_age", extractLong(applicantData, "spouse_age", "spouseAge"));
        setIfPresent(b, "spouse_mobile", extractString(applicantData, "spouse_mobile", "spouseMobile", "spousePhoneMobile"));
        setIfPresent(b, "spouse_residential_phone", extractString(applicantData, "spouse_residential_phone", "spouseResidentialPhone", "spousePhoneHome"));
        setIfPresent(b, "spouse_email", extractString(applicantData, "spouse_email", "spouseEmail"));
        setIfPresent(b, "spouse_employer", extractString(applicantData, "spouse_employer", "spouseEmployer"));
        setIfPresent(b, "spouse_nature_of_business", extractString(applicantData, "spouse_nature_of_business", "spouseNatureOfBusiness"));
        setIfPresent(b, "spouse_occupation", extractString(applicantData, "spouse_occupation", "spouseOccupation"));
        setIfPresent(b, "spouse_position", extractString(applicantData, "spouse_position", "spousePosition"));
        setIfPresent(b, "spouse_general_line", extractString(applicantData, "spouse_general_line", "spouseGeneralLine"));
        setIfPresent(b, "spouse_service_years", extractBigDecimal(applicantData, "spouse_service_years", "spouseServiceYears"));
        setIfPresent(b, "spouse_monthly_gross_rm", extractBigDecimal(applicantData, "spouse_monthly_gross_rm", "spouseMonthlyGrossRm", "spouseMonthlyGrossIncome"));
        setIfPresent(b, "spouse_annual_gross_rm", extractBigDecimal(applicantData, "spouse_annual_gross_rm", "spouseAnnualGrossRm", "spouseAnnualGrossIncome"));
        setIfPresent(b, "other_commitments", extractString(applicantData, "other_commitments", "otherCommitments"));
        setIfPresent(b, "close_relatives", extractString(applicantData, "close_relatives", "closeRelatives"));
        setIfPresent(b, "close_relations_staff", extractBoolean(applicantData, "close_relations_staff", "closeRelationsStaff"));
        setIfPresent(b, "close_relations_relative", extractBoolean(applicantData, "close_relations_relative", "closeRelationsRelative"));

        return b.build();
    }

    private Mutation buildApplicationMutation(String transactionId, String userId, boolean exists, Map<String, Object> applicationData) {
        Mutation.WriteBuilder b = exists
                ? Mutation.newUpdateBuilder("application")
                : Mutation.newInsertBuilder("application");

        b.set("transaction_id").to(transactionId);
        if (!exists) {
            b.set("user_id").to(userId);
            String appType = extractString(applicationData, "application_type", "applicationType", "applicationCategory");
            b.set("application_type").to((appType != null && !appType.isBlank()) ? appType.trim() : "HOME_LOAN");
            String status = extractString(applicationData, "status", "applicationStatus");
            b.set("status").to((status != null && !status.isBlank()) ? status.trim() : "NEW");
            b.set("created_at").to(Value.COMMIT_TIMESTAMP);
        }

        setIfPresent(b, "bank_selection", extractString(applicationData, "bank_selection", "bankSelection", "bank"));
        setIfPresent(b, "application_type", extractString(applicationData, "application_type", "applicationType", "applicationCategory"));
        setIfPresent(b, "status", extractString(applicationData, "status", "applicationStatus"));
        setIfPresent(b, "facility_type", extractString(applicationData, "facility_type", "facilityType"));
        setIfPresent(b, "facility_purpose", extractString(applicationData, "facility_purpose", "facilityPurpose", "purposeOfFacility"));
        setIfPresent(b, "facilities_required", extractString(applicationData, "facilities_required", "facilitiesRequired"));
        setIfPresent(b, "refinancing_bank", extractString(applicationData, "refinancing_bank", "refinancingBank"));
        setIfPresent(b, "joint_relationship", extractString(applicationData, "joint_relationship", "jointRelationship"));
        setIfPresent(b, "marketing_consent", extractString(applicationData, "marketing_consent", "marketingConsent", "consentMarketing"));
        setIfPresent(b, "docs_enclosed", extractString(applicationData, "docs_enclosed", "docsEnclosed"));
        setIfPresent(b, "ftfc_category", extractString(applicationData, "ftfc_category", "ftfcCategory"));
        setIfPresent(b, "signatures", extractString(applicationData, "signatures"));
        setIfPresent(b, "application_date", extractDate(applicationData, "application_date", "applicationDate"));
        setIfPresent(b, "ai_analysis", extractString(applicationData, "ai_analysis", "aiAnalysis"));

        return b.build();
    }

    private Mutation buildPropertyMutation(String transactionId, String propertyId, boolean exists, Map<String, Object> propertyData) {
        Mutation.WriteBuilder b = exists
                ? Mutation.newUpdateBuilder("property")
                : Mutation.newInsertBuilder("property");

        b.set("transaction_id").to(transactionId);
        b.set("property_id").to(propertyId);

        setIfPresent(b, "property_type", extractString(propertyData, "property_type", "propertyType"));
        setIfPresent(b, "property_sub_type", extractString(propertyData, "property_sub_type", "propertySubType"));
        setIfPresent(b, "property_status", extractString(propertyData, "property_status", "propertyStatus"));
        setIfPresent(b, "construction_stage", extractString(propertyData, "construction_stage", "constructionStage"));
        setIfPresent(b, "developer_name", extractString(propertyData, "developer_name", "developerName"));
        setIfPresent(b, "project_name", extractString(propertyData, "project_name", "projectName"));
        setIfPresent(b, "relationship_to_developer", extractString(propertyData, "relationship_to_developer", "relationshipToDeveloper"));
        setIfPresent(b, "phase_code", extractString(propertyData, "phase_code", "phaseCode"));
        setIfPresent(b, "contractor_name", extractString(propertyData, "contractor_name", "contractorName"));
        setIfPresent(b, "spa_price_rm", extractBigDecimal(propertyData, "spa_price_rm", "spaPriceRm", "spaPrice", "price"));
        setIfPresent(b, "open_market_rm", extractBigDecimal(propertyData, "open_market_rm", "openMarketRm", "openMarketValue", "marketValue"));
        setIfPresent(b, "renovation_value_rm", extractBigDecimal(propertyData, "renovation_value_rm", "renovationValueRm", "renovationValue"));
        setIfPresent(b, "property_address", extractString(propertyData, "property_address", "propertyAddress", "address", "addressLine1", "address_line1"));
        setIfPresent(b, "property_address_line2", extractString(propertyData, "property_address_line2", "propertyAddressLine2", "addressLine2", "address_line2"));
        setIfPresent(b, "property_postcode", extractString(propertyData, "property_postcode", "propertyPostcode", "postcode", "postalCode"));
        setIfPresent(b, "property_city", extractString(propertyData, "property_city", "propertyCity", "city"));
        setIfPresent(b, "property_state", extractString(propertyData, "property_state", "propertyState", "state"));
        setIfPresent(b, "property_country", extractString(propertyData, "property_country", "propertyCountry", "country"));
        setIfPresent(b, "title_number", extractString(propertyData, "title_number", "titleNumber"));
        setIfPresent(b, "title_type", extractString(propertyData, "title_type", "titleType"));
        setIfPresent(b, "lot_number", extractString(propertyData, "lot_number", "lotNumber"));
        setIfPresent(b, "mukim", extractString(propertyData, "mukim"));
        setIfPresent(b, "district", extractString(propertyData, "district"));
        setIfPresent(b, "state_geran", extractString(propertyData, "state_geran", "stateGeran"));
        setIfPresent(b, "is_owner_occupied", extractBoolean(propertyData, "is_owner_occupied", "isOwnerOccupied"));
        setIfPresent(b, "is_first_time_buyer", extractBoolean(propertyData, "is_first_time_buyer", "isFirstTimeBuyer", "isFirstTimePurchaser"));
        setIfPresent(b, "gross_purchase_price_rm", extractBigDecimal(propertyData, "gross_purchase_price_rm", "grossPurchasePriceRm", "grossPurchasePrice"));
        setIfPresent(b, "discount_rm", extractBigDecimal(propertyData, "discount_rm", "discountRm", "discount"));
        setIfPresent(b, "rebate_rm", extractBigDecimal(propertyData, "rebate_rm", "rebateRm", "rebate"));
        setIfPresent(b, "adjustment_rm", extractBigDecimal(propertyData, "adjustment_rm", "adjustmentRm", "adjustment"));
        setIfPresent(b, "developer_benefits_rm", extractBigDecimal(propertyData, "developer_benefits_rm", "developerBenefitsRm", "developerBenefits"));
        setIfPresent(b, "net_purchase_price_rm", extractBigDecimal(propertyData, "net_purchase_price_rm", "netPurchasePriceRm", "netPurchasePrice"));

        return b.build();
    }

    private void validateApplicationSimilarity(com.google.cloud.spanner.Struct row, Map<String, Object> incoming) {
        List<String> conflicts = new ArrayList<>();
        if (!row.isNull("bank_selection")) validateSimilarity("application", "bank_selection", row.getString("bank_selection"), extractString(incoming, "bank_selection", "bankSelection", "bank"), conflicts);
        if (!row.isNull("application_type")) validateSimilarity("application", "application_type", row.getString("application_type"), extractString(incoming, "application_type", "applicationType"), conflicts);
        if (!row.isNull("status")) validateSimilarity("application", "status", row.getString("status"), extractString(incoming, "status", "applicationStatus"), conflicts);
        if (!row.isNull("facility_type")) validateSimilarity("application", "facility_type", row.getString("facility_type"), extractString(incoming, "facility_type", "facilityType"), conflicts);
        if (!row.isNull("facility_purpose")) validateSimilarity("application", "facility_purpose", row.getString("facility_purpose"), extractString(incoming, "facility_purpose", "facilityPurpose"), conflicts);
        if (!row.isNull("marketing_consent")) validateSimilarity("application", "marketing_consent", row.getString("marketing_consent"), extractString(incoming, "marketing_consent", "marketingConsent"), conflicts);
        if (!row.isNull("application_date")) validateSimilarity("application", "application_date", row.getDate("application_date").toString(), extractDate(incoming, "application_date", "applicationDate"), conflicts);
        throwIfConflicts("application", conflicts);
    }

    private void validateApplicantSimilarity(com.google.cloud.spanner.Struct row, Map<String, Object> incoming) {
        List<String> conflicts = new ArrayList<>();
        if (!row.isNull("role")) validateSimilarity("applicant", "role", row.getString("role"), extractString(incoming, "role"), conflicts);
        if (!row.isNull("full_name")) validateSimilarity("applicant", "full_name", row.getString("full_name"), extractString(incoming, "full_name", "fullName", "name"), conflicts);
        if (!row.isNull("id_type")) validateSimilarity("applicant", "id_type", row.getString("id_type"), extractString(incoming, "id_type", "idType"), conflicts);
        if (!row.isNull("id_no")) validateSimilarity("applicant", "id_no", row.getString("id_no"), extractString(incoming, "id_no", "idNo", "idNumber"), conflicts);
        if (!row.isNull("nationality")) validateSimilarity("applicant", "nationality", row.getString("nationality"), extractString(incoming, "nationality"), conflicts);
        if (!row.isNull("race")) validateSimilarity("applicant", "race", row.getString("race"), extractString(incoming, "race"), conflicts);
        if (!row.isNull("bumiputera_status")) validateSimilarity("applicant", "bumiputera_status", row.getBoolean("bumiputera_status"), extractBoolean(incoming, "bumiputera_status", "bumiputeraStatus", "isBumiputera"), conflicts);
        if (!row.isNull("gender")) validateSimilarity("applicant", "gender", row.getString("gender"), extractString(incoming, "gender", "sex"), conflicts);
        if (!row.isNull("marital_status")) validateSimilarity("applicant", "marital_status", row.getString("marital_status"), extractString(incoming, "marital_status", "maritalStatus"), conflicts);
        if (!row.isNull("date_of_birth")) validateSimilarity("applicant", "date_of_birth", row.getDate("date_of_birth").toString(), extractDate(incoming, "date_of_birth", "dateOfBirth", "dob"), conflicts);
        if (!row.isNull("dependents_count")) validateSimilarity("applicant", "dependents_count", row.getLong("dependents_count"), extractLong(incoming, "dependents_count", "dependentsCount"), conflicts);
        if (!row.isNull("education_level")) validateSimilarity("applicant", "education_level", row.getString("education_level"), extractString(incoming, "education_level", "educationLevel"), conflicts);
        if (!row.isNull("mobile_phone")) validateSimilarity("applicant", "mobile_phone", row.getString("mobile_phone"), extractString(incoming, "mobile_phone", "mobilePhone", "phoneNumber", "mobile"), conflicts);
        if (!row.isNull("residential_phone")) validateSimilarity("applicant", "residential_phone", row.getString("residential_phone"), extractString(incoming, "residential_phone", "residentialPhone"), conflicts);
        if (!row.isNull("email")) validateSimilarity("applicant", "email", row.getString("email"), extractString(incoming, "email"), conflicts);
        if (!row.isNull("perm_address")) validateSimilarity("applicant", "perm_address", row.getString("perm_address"), extractString(incoming, "perm_address", "permAddress", "address"), conflicts);
        if (!row.isNull("perm_postcode")) validateSimilarity("applicant", "perm_postcode", row.getString("perm_postcode"), extractString(incoming, "perm_postcode", "permPostcode", "postalCode", "postcode"), conflicts);
        if (!row.isNull("perm_city")) validateSimilarity("applicant", "perm_city", row.getString("perm_city"), extractString(incoming, "perm_city", "permCity", "city"), conflicts);
        if (!row.isNull("perm_state")) validateSimilarity("applicant", "perm_state", row.getString("perm_state"), extractString(incoming, "perm_state", "permState", "state"), conflicts);
        if (!row.isNull("mail_address")) validateSimilarity("applicant", "mail_address", row.getString("mail_address"), extractString(incoming, "mail_address", "mailAddress", "mailingAddress"), conflicts);
        if (!row.isNull("mail_postcode")) validateSimilarity("applicant", "mail_postcode", row.getString("mail_postcode"), extractString(incoming, "mail_postcode", "mailPostcode", "mailingPostcode"), conflicts);
        if (!row.isNull("employment_status")) validateSimilarity("applicant", "employment_status", row.getString("employment_status"), extractString(incoming, "employment_status", "employmentStatus"), conflicts);
        if (!row.isNull("employer_name")) validateSimilarity("applicant", "employer_name", row.getString("employer_name"), extractString(incoming, "employer_name", "employerName"), conflicts);
        if (!row.isNull("nature_of_business")) validateSimilarity("applicant", "nature_of_business", row.getString("nature_of_business"), extractString(incoming, "nature_of_business", "natureOfBusiness"), conflicts);
        if (!row.isNull("occupation")) validateSimilarity("applicant", "occupation", row.getString("occupation"), extractString(incoming, "occupation"), conflicts);
        if (!row.isNull("job_position")) validateSimilarity("applicant", "job_position", row.getString("job_position"), extractString(incoming, "job_position", "jobPosition", "position"), conflicts);
        if (!row.isNull("length_of_service_years")) validateSimilarity("applicant", "length_of_service_years", row.getBigDecimal("length_of_service_years"), extractBigDecimal(incoming, "length_of_service_years", "lengthOfServiceYears"), conflicts);
        if (!row.isNull("monthly_gross_rm")) validateSimilarity("applicant", "monthly_gross_rm", row.getBigDecimal("monthly_gross_rm"), extractBigDecimal(incoming, "monthly_gross_rm", "monthlyGrossRm", "monthlyIncome", "grossIncome"), conflicts);
        if (!row.isNull("annual_gross_rm")) validateSimilarity("applicant", "annual_gross_rm", row.getBigDecimal("annual_gross_rm"), extractBigDecimal(incoming, "annual_gross_rm", "annualGrossRm", "annualIncome"), conflicts);
        if (!row.isNull("emergency_name")) validateSimilarity("applicant", "emergency_name", row.getString("emergency_name"), extractString(incoming, "emergency_name", "emergencyName"), conflicts);
        if (!row.isNull("emergency_relationship")) validateSimilarity("applicant", "emergency_relationship", row.getString("emergency_relationship"), extractString(incoming, "emergency_relationship", "emergencyRelationship"), conflicts);
        if (!row.isNull("emergency_phone")) validateSimilarity("applicant", "emergency_phone", row.getString("emergency_phone"), extractString(incoming, "emergency_phone", "emergencyPhone"), conflicts);
        if (!row.isNull("spouse_full_name")) validateSimilarity("applicant", "spouse_full_name", row.getString("spouse_full_name"), extractString(incoming, "spouse_full_name", "spouseFullName", "spouseName"), conflicts);
        if (!row.isNull("spouse_id_no")) validateSimilarity("applicant", "spouse_id_no", row.getString("spouse_id_no"), extractString(incoming, "spouse_id_no", "spouseIdNo"), conflicts);
        if (!row.isNull("spouse_mobile")) validateSimilarity("applicant", "spouse_mobile", row.getString("spouse_mobile"), extractString(incoming, "spouse_mobile", "spouseMobile"), conflicts);
        if (!row.isNull("spouse_employer")) validateSimilarity("applicant", "spouse_employer", row.getString("spouse_employer"), extractString(incoming, "spouse_employer", "spouseEmployer"), conflicts);
        if (!row.isNull("spouse_monthly_gross_rm")) validateSimilarity("applicant", "spouse_monthly_gross_rm", row.getBigDecimal("spouse_monthly_gross_rm"), extractBigDecimal(incoming, "spouse_monthly_gross_rm", "spouseMonthlyGrossRm"), conflicts);
        throwIfConflicts("applicant", conflicts);
    }

    private void validatePropertySimilarity(com.google.cloud.spanner.Struct row, Map<String, Object> incoming) {
        List<String> conflicts = new ArrayList<>();
        if (!row.isNull("property_type")) validateSimilarity("property", "property_type", row.getString("property_type"), extractString(incoming, "property_type", "propertyType"), conflicts);
        if (!row.isNull("property_status")) validateSimilarity("property", "property_status", row.getString("property_status"), extractString(incoming, "property_status", "propertyStatus"), conflicts);
        if (!row.isNull("developer_name")) validateSimilarity("property", "developer_name", row.getString("developer_name"), extractString(incoming, "developer_name", "developerName"), conflicts);
        if (!row.isNull("project_name")) validateSimilarity("property", "project_name", row.getString("project_name"), extractString(incoming, "project_name", "projectName"), conflicts);
        if (!row.isNull("contractor_name")) validateSimilarity("property", "contractor_name", row.getString("contractor_name"), extractString(incoming, "contractor_name", "contractorName"), conflicts);
        if (!row.isNull("spa_price_rm")) validateSimilarity("property", "spa_price_rm", row.getBigDecimal("spa_price_rm"), extractBigDecimal(incoming, "spa_price_rm", "spaPriceRm", "spaPrice", "price"), conflicts);
        if (!row.isNull("open_market_rm")) validateSimilarity("property", "open_market_rm", row.getBigDecimal("open_market_rm"), extractBigDecimal(incoming, "open_market_rm", "openMarketRm", "openMarketValue"), conflicts);
        if (!row.isNull("renovation_value_rm")) validateSimilarity("property", "renovation_value_rm", row.getBigDecimal("renovation_value_rm"), extractBigDecimal(incoming, "renovation_value_rm", "renovationValueRm"), conflicts);
        if (!row.isNull("property_address")) validateSimilarity("property", "property_address", row.getString("property_address"), extractString(incoming, "property_address", "propertyAddress", "address"), conflicts);
        if (!row.isNull("property_postcode")) validateSimilarity("property", "property_postcode", row.getString("property_postcode"), extractString(incoming, "property_postcode", "propertyPostcode", "postcode", "postalCode"), conflicts);
        if (!row.isNull("property_city")) validateSimilarity("property", "property_city", row.getString("property_city"), extractString(incoming, "property_city", "propertyCity", "city"), conflicts);
        if (!row.isNull("property_state")) validateSimilarity("property", "property_state", row.getString("property_state"), extractString(incoming, "property_state", "propertyState", "state"), conflicts);
        if (!row.isNull("title_number")) validateSimilarity("property", "title_number", row.getString("title_number"), extractString(incoming, "title_number", "titleNumber"), conflicts);
        if (!row.isNull("title_type")) validateSimilarity("property", "title_type", row.getString("title_type"), extractString(incoming, "title_type", "titleType"), conflicts);
        if (!row.isNull("lot_number")) validateSimilarity("property", "lot_number", row.getString("lot_number"), extractString(incoming, "lot_number", "lotNumber"), conflicts);
        if (!row.isNull("mukim")) validateSimilarity("property", "mukim", row.getString("mukim"), extractString(incoming, "mukim"), conflicts);
        if (!row.isNull("district")) validateSimilarity("property", "district", row.getString("district"), extractString(incoming, "district"), conflicts);
        if (!row.isNull("is_owner_occupied")) validateSimilarity("property", "is_owner_occupied", row.getBoolean("is_owner_occupied"), extractBoolean(incoming, "is_owner_occupied", "isOwnerOccupied"), conflicts);
        if (!row.isNull("is_first_time_buyer")) validateSimilarity("property", "is_first_time_buyer", row.getBoolean("is_first_time_buyer"), extractBoolean(incoming, "is_first_time_buyer", "isFirstTimeBuyer"), conflicts);
        throwIfConflicts("property", conflicts);
    }

    private void validateSimilarity(String table, String field, Object existingVal, Object incomingVal, List<String> conflicts) {
        if (!this.properties.isSimilarityCheckEnabled()) {
            return;
        }
        if (this.properties.isFieldIgnored(field)) {
            return;
        }
        if (existingVal == null || incomingVal == null) {
            return;
        }
        if (isEmptyOrZeroOrNA(existingVal)) {
            return;
        }
        String sExisting = existingVal.toString().trim();
        String sIncoming = incomingVal.toString().trim();
        if (sExisting.isEmpty() || sIncoming.isEmpty()) {
            return;
        }

        double similarity = computeSimilarity(existingVal, incomingVal);
        if (similarity < this.similarityThreshold) {
            String errorMsg = String.format(
                    "Conflicting data in document for %s field '%s': existing value '%s' vs incoming value '%s' (similarity %.1f%% is below threshold %.1f%%)",
                    table, field, sExisting, sIncoming, similarity * 100.0, this.similarityThreshold * 100.0
            );
            conflicts.add(errorMsg);
        }
    }

    private void throwIfConflicts(String table, List<String> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }
        if (conflicts.size() == 1) {
            log.warn(conflicts.get(0));
            throw new IllegalArgumentException(conflicts.get(0));
        }
        StringBuilder sb = new StringBuilder(String.format("Multiple conflicts detected in document for %s (%d conflicts): ", table, conflicts.size()));
        for (int i = 0; i < conflicts.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(String.format("[%d] %s", i + 1, conflicts.get(i)));
        }
        String summary = sb.toString();
        log.warn(summary);
        throw new IllegalArgumentException(summary);
    }

    @Override
    public Mono<Map<String, Object>> checkSimilarity(
            String transactionId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    ) {
        return checkSimilarity(transactionId, applicantData, applicationData, propertyData, null);
    }

    @Override
    public Mono<Map<String, Object>> checkSimilarity(
            String transactionId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData,
            List<String> ignoredFields
    ) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> conflicts = new ArrayList<>();
            List<Map<String, Object>> matches = new ArrayList<>();

            // 1. Check Application table
            if (applicationData != null && !applicationData.isEmpty()) {
                Statement checkApp = Statement.newBuilder(
                                "SELECT bank_selection, application_type, status, facility_type, facility_purpose, " +
                                "marketing_consent, application_date FROM application WHERE transaction_id = @transactionId")
                        .bind("transactionId").to(transactionId)
                        .build();

                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApp)) {
                    if (rs.next()) {
                        com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                        if (!row.isNull("bank_selection")) checkFieldSimilarity("application", "bank_selection", row.getString("bank_selection"), extractString(applicationData, "bank_selection", "bankSelection", "bank"), ignoredFields, conflicts, matches);
                        if (!row.isNull("application_type")) checkFieldSimilarity("application", "application_type", row.getString("application_type"), extractString(applicationData, "application_type", "applicationType"), ignoredFields, conflicts, matches);
                        if (!row.isNull("status")) checkFieldSimilarity("application", "status", row.getString("status"), extractString(applicationData, "status", "applicationStatus"), ignoredFields, conflicts, matches);
                        if (!row.isNull("facility_type")) checkFieldSimilarity("application", "facility_type", row.getString("facility_type"), extractString(applicationData, "facility_type", "facilityType"), ignoredFields, conflicts, matches);
                        if (!row.isNull("facility_purpose")) checkFieldSimilarity("application", "facility_purpose", row.getString("facility_purpose"), extractString(applicationData, "facility_purpose", "facilityPurpose"), ignoredFields, conflicts, matches);
                        if (!row.isNull("marketing_consent")) checkFieldSimilarity("application", "marketing_consent", row.getString("marketing_consent"), extractString(applicationData, "marketing_consent", "marketingConsent"), ignoredFields, conflicts, matches);
                        if (!row.isNull("application_date")) checkFieldSimilarity("application", "application_date", row.getDate("application_date").toString(), extractDate(applicationData, "application_date", "applicationDate"), ignoredFields, conflicts, matches);
                    }
                }
            }

            // 2. Check Applicant table
            if (applicantData != null && !applicantData.isEmpty()) {
                String applicantId = extractString(applicantData, "applicant_id", "applicantId", "user_id", "userId");
                Statement checkApplicant;
                if (applicantId != null && !applicantId.isBlank()) {
                    checkApplicant = Statement.newBuilder(
                                    "SELECT role, full_name, id_type, id_no, nationality, race, bumiputera_status, gender, " +
                                    "marital_status, date_of_birth, dependents_count, education_level, mobile_phone, " +
                                    "residential_phone, email, perm_address, perm_postcode, perm_city, perm_state, " +
                                    "mail_address, mail_postcode, employment_status, employer_name, nature_of_business, " +
                                    "occupation, job_position, length_of_service_years, monthly_gross_rm, annual_gross_rm, " +
                                    "emergency_name, emergency_relationship, emergency_phone, spouse_full_name, spouse_id_no, " +
                                    "spouse_mobile, spouse_employer, spouse_monthly_gross_rm, other_commitments, close_relatives FROM applicant " +
                                    "WHERE transaction_id = @transactionId AND applicant_id = @applicantId")
                            .bind("transactionId").to(transactionId)
                            .bind("applicantId").to(applicantId)
                            .build();
                } else {
                    checkApplicant = Statement.newBuilder(
                                    "SELECT role, full_name, id_type, id_no, nationality, race, bumiputera_status, gender, " +
                                    "marital_status, date_of_birth, dependents_count, education_level, mobile_phone, " +
                                    "residential_phone, email, perm_address, perm_postcode, perm_city, perm_state, " +
                                    "mail_address, mail_postcode, employment_status, employer_name, nature_of_business, " +
                                    "occupation, job_position, length_of_service_years, monthly_gross_rm, annual_gross_rm, " +
                                    "emergency_name, emergency_relationship, emergency_phone, spouse_full_name, spouse_id_no, " +
                                    "spouse_mobile, spouse_employer, spouse_monthly_gross_rm, other_commitments, close_relatives FROM applicant " +
                                    "WHERE transaction_id = @transactionId LIMIT 1")
                            .bind("transactionId").to(transactionId)
                            .build();
                }

                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApplicant)) {
                    if (rs.next()) {
                        com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                        if (!row.isNull("role")) checkFieldSimilarity("applicant", "role", row.getString("role"), extractString(applicantData, "role"), ignoredFields, conflicts, matches);
                        if (!row.isNull("full_name")) checkFieldSimilarity("applicant", "full_name", row.getString("full_name"), extractString(applicantData, "full_name", "fullName", "name"), ignoredFields, conflicts, matches);
                        if (!row.isNull("id_type")) checkFieldSimilarity("applicant", "id_type", row.getString("id_type"), extractString(applicantData, "id_type", "idType"), ignoredFields, conflicts, matches);
                        if (!row.isNull("id_no")) checkFieldSimilarity("applicant", "id_no", row.getString("id_no"), extractString(applicantData, "id_no", "idNo", "idNumber"), ignoredFields, conflicts, matches);
                        if (!row.isNull("nationality")) checkFieldSimilarity("applicant", "nationality", row.getString("nationality"), extractString(applicantData, "nationality"), ignoredFields, conflicts, matches);
                        if (!row.isNull("race")) checkFieldSimilarity("applicant", "race", row.getString("race"), extractString(applicantData, "race"), ignoredFields, conflicts, matches);
                        if (!row.isNull("bumiputera_status")) checkFieldSimilarity("applicant", "bumiputera_status", row.getBoolean("bumiputera_status"), extractBoolean(applicantData, "bumiputera_status", "bumiputeraStatus", "isBumiputera"), ignoredFields, conflicts, matches);
                        if (!row.isNull("gender")) checkFieldSimilarity("applicant", "gender", row.getString("gender"), extractString(applicantData, "gender", "sex"), ignoredFields, conflicts, matches);
                        if (!row.isNull("marital_status")) checkFieldSimilarity("applicant", "marital_status", row.getString("marital_status"), extractString(applicantData, "marital_status", "maritalStatus"), ignoredFields, conflicts, matches);
                        if (!row.isNull("date_of_birth")) checkFieldSimilarity("applicant", "date_of_birth", row.getDate("date_of_birth").toString(), extractDate(applicantData, "date_of_birth", "dateOfBirth", "dob"), ignoredFields, conflicts, matches);
                        if (!row.isNull("dependents_count")) checkFieldSimilarity("applicant", "dependents_count", row.getLong("dependents_count"), extractLong(applicantData, "dependents_count", "dependentsCount"), ignoredFields, conflicts, matches);
                        if (!row.isNull("education_level")) checkFieldSimilarity("applicant", "education_level", row.getString("education_level"), extractString(applicantData, "education_level", "educationLevel"), ignoredFields, conflicts, matches);
                        if (!row.isNull("mobile_phone")) checkFieldSimilarity("applicant", "mobile_phone", row.getString("mobile_phone"), extractString(applicantData, "mobile_phone", "mobilePhone", "phoneNumber", "mobile"), ignoredFields, conflicts, matches);
                        if (!row.isNull("residential_phone")) checkFieldSimilarity("applicant", "residential_phone", row.getString("residential_phone"), extractString(applicantData, "residential_phone", "residentialPhone"), ignoredFields, conflicts, matches);
                        if (!row.isNull("email")) checkFieldSimilarity("applicant", "email", row.getString("email"), extractString(applicantData, "email"), ignoredFields, conflicts, matches);
                        if (!row.isNull("perm_address")) checkFieldSimilarity("applicant", "perm_address", row.getString("perm_address"), extractString(applicantData, "perm_address", "permAddress", "address"), ignoredFields, conflicts, matches);
                        if (!row.isNull("perm_postcode")) checkFieldSimilarity("applicant", "perm_postcode", row.getString("perm_postcode"), extractString(applicantData, "perm_postcode", "permPostcode", "postalCode", "postcode"), ignoredFields, conflicts, matches);
                        if (!row.isNull("perm_city")) checkFieldSimilarity("applicant", "perm_city", row.getString("perm_city"), extractString(applicantData, "perm_city", "permCity", "city"), ignoredFields, conflicts, matches);
                        if (!row.isNull("perm_state")) checkFieldSimilarity("applicant", "perm_state", row.getString("perm_state"), extractString(applicantData, "perm_state", "permState", "state"), ignoredFields, conflicts, matches);
                        if (!row.isNull("mail_address")) checkFieldSimilarity("applicant", "mail_address", row.getString("mail_address"), extractString(applicantData, "mail_address", "mailAddress", "mailingAddress"), ignoredFields, conflicts, matches);
                        if (!row.isNull("mail_postcode")) checkFieldSimilarity("applicant", "mail_postcode", row.getString("mail_postcode"), extractString(applicantData, "mail_postcode", "mailPostcode", "mailingPostcode"), ignoredFields, conflicts, matches);
                        if (!row.isNull("employment_status")) checkFieldSimilarity("applicant", "employment_status", row.getString("employment_status"), extractString(applicantData, "employment_status", "employmentStatus"), ignoredFields, conflicts, matches);
                        if (!row.isNull("employer_name")) checkFieldSimilarity("applicant", "employer_name", row.getString("employer_name"), extractString(applicantData, "employer_name", "employerName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("nature_of_business")) checkFieldSimilarity("applicant", "nature_of_business", row.getString("nature_of_business"), extractString(applicantData, "nature_of_business", "natureOfBusiness"), ignoredFields, conflicts, matches);
                        if (!row.isNull("occupation")) checkFieldSimilarity("applicant", "occupation", row.getString("occupation"), extractString(applicantData, "occupation"), ignoredFields, conflicts, matches);
                        if (!row.isNull("job_position")) checkFieldSimilarity("applicant", "job_position", row.getString("job_position"), extractString(applicantData, "job_position", "jobPosition", "position"), ignoredFields, conflicts, matches);
                        if (!row.isNull("length_of_service_years")) checkFieldSimilarity("applicant", "length_of_service_years", row.getBigDecimal("length_of_service_years"), extractBigDecimal(applicantData, "length_of_service_years", "lengthOfServiceYears"), ignoredFields, conflicts, matches);
                        if (!row.isNull("monthly_gross_rm")) checkFieldSimilarity("applicant", "monthly_gross_rm", row.getBigDecimal("monthly_gross_rm"), extractBigDecimal(applicantData, "monthly_gross_rm", "monthlyGrossRm", "monthlyIncome", "grossIncome"), ignoredFields, conflicts, matches);
                        if (!row.isNull("annual_gross_rm")) checkFieldSimilarity("applicant", "annual_gross_rm", row.getBigDecimal("annual_gross_rm"), extractBigDecimal(applicantData, "annual_gross_rm", "annualGrossRm", "annualIncome"), ignoredFields, conflicts, matches);
                        if (!row.isNull("emergency_name")) checkFieldSimilarity("applicant", "emergency_name", row.getString("emergency_name"), extractString(applicantData, "emergency_name", "emergencyName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("emergency_relationship")) checkFieldSimilarity("applicant", "emergency_relationship", row.getString("emergency_relationship"), extractString(applicantData, "emergency_relationship", "emergencyRelationship"), ignoredFields, conflicts, matches);
                        if (!row.isNull("emergency_phone")) checkFieldSimilarity("applicant", "emergency_phone", row.getString("emergency_phone"), extractString(applicantData, "emergency_phone", "emergencyPhone"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spouse_full_name")) checkFieldSimilarity("applicant", "spouse_full_name", row.getString("spouse_full_name"), extractString(applicantData, "spouse_full_name", "spouseFullName", "spouseName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spouse_id_no")) checkFieldSimilarity("applicant", "spouse_id_no", row.getString("spouse_id_no"), extractString(applicantData, "spouse_id_no", "spouseIdNo"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spouse_mobile")) checkFieldSimilarity("applicant", "spouse_mobile", row.getString("spouse_mobile"), extractString(applicantData, "spouse_mobile", "spouseMobile"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spouse_employer")) checkFieldSimilarity("applicant", "spouse_employer", row.getString("spouse_employer"), extractString(applicantData, "spouse_employer", "spouseEmployer"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spouse_monthly_gross_rm")) checkFieldSimilarity("applicant", "spouse_monthly_gross_rm", row.getBigDecimal("spouse_monthly_gross_rm"), extractBigDecimal(applicantData, "spouse_monthly_gross_rm", "spouseMonthlyGrossRm"), ignoredFields, conflicts, matches);
                    }
                }
            }

            // 3. Check Property table
            if (propertyData != null && !propertyData.isEmpty()) {
                String propertyId = extractString(propertyData, "property_id", "propertyId");
                Statement checkProperty;
                if (propertyId != null && !propertyId.isBlank()) {
                    checkProperty = Statement.newBuilder(
                                    "SELECT property_type, property_status, developer_name, project_name, contractor_name, " +
                                    "spa_price_rm, open_market_rm, renovation_value_rm, property_address, property_postcode, " +
                                    "property_city, property_state, title_number, title_type, lot_number, mukim, district, " +
                                    "is_owner_occupied, is_first_time_buyer FROM property " +
                                    "WHERE transaction_id = @transactionId AND property_id = @propertyId")
                            .bind("transactionId").to(transactionId)
                            .bind("propertyId").to(propertyId)
                            .build();
                } else {
                    checkProperty = Statement.newBuilder(
                                    "SELECT property_type, property_status, developer_name, project_name, contractor_name, " +
                                    "spa_price_rm, open_market_rm, renovation_value_rm, property_address, property_postcode, " +
                                    "property_city, property_state, title_number, title_type, lot_number, mukim, district, " +
                                    "is_owner_occupied, is_first_time_buyer FROM property " +
                                    "WHERE transaction_id = @transactionId LIMIT 1")
                            .bind("transactionId").to(transactionId)
                            .build();
                }

                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkProperty)) {
                    if (rs.next()) {
                        com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                        if (!row.isNull("property_type")) checkFieldSimilarity("property", "property_type", row.getString("property_type"), extractString(propertyData, "property_type", "propertyType"), ignoredFields, conflicts, matches);
                        if (!row.isNull("property_status")) checkFieldSimilarity("property", "property_status", row.getString("property_status"), extractString(propertyData, "property_status", "propertyStatus"), ignoredFields, conflicts, matches);
                        if (!row.isNull("developer_name")) checkFieldSimilarity("property", "developer_name", row.getString("developer_name"), extractString(propertyData, "developer_name", "developerName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("project_name")) checkFieldSimilarity("property", "project_name", row.getString("project_name"), extractString(propertyData, "project_name", "projectName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("contractor_name")) checkFieldSimilarity("property", "contractor_name", row.getString("contractor_name"), extractString(propertyData, "contractor_name", "contractorName"), ignoredFields, conflicts, matches);
                        if (!row.isNull("spa_price_rm")) checkFieldSimilarity("property", "spa_price_rm", row.getBigDecimal("spa_price_rm"), extractBigDecimal(propertyData, "spa_price_rm", "spaPriceRm", "spaPrice", "price"), ignoredFields, conflicts, matches);
                        if (!row.isNull("open_market_rm")) checkFieldSimilarity("property", "open_market_rm", row.getBigDecimal("open_market_rm"), extractBigDecimal(propertyData, "open_market_rm", "openMarketRm", "openMarketValue"), ignoredFields, conflicts, matches);
                        if (!row.isNull("renovation_value_rm")) checkFieldSimilarity("property", "renovation_value_rm", row.getBigDecimal("renovation_value_rm"), extractBigDecimal(propertyData, "renovation_value_rm", "renovationValueRm"), ignoredFields, conflicts, matches);
                        if (!row.isNull("property_address")) checkFieldSimilarity("property", "property_address", row.getString("property_address"), extractString(propertyData, "property_address", "propertyAddress", "address"), ignoredFields, conflicts, matches);
                        if (!row.isNull("property_postcode")) checkFieldSimilarity("property", "property_postcode", row.getString("property_postcode"), extractString(propertyData, "property_postcode", "propertyPostcode", "postcode", "postalCode"), ignoredFields, conflicts, matches);
                        if (!row.isNull("property_city")) checkFieldSimilarity("property", "property_city", row.getString("property_city"), extractString(propertyData, "property_city", "propertyCity", "city"), ignoredFields, conflicts, matches);
                        if (!row.isNull("property_state")) checkFieldSimilarity("property", "property_state", row.getString("property_state"), extractString(propertyData, "property_state", "propertyState", "state"), ignoredFields, conflicts, matches);
                        if (!row.isNull("title_number")) checkFieldSimilarity("property", "title_number", row.getString("title_number"), extractString(propertyData, "title_number", "titleNumber"), ignoredFields, conflicts, matches);
                        if (!row.isNull("title_type")) checkFieldSimilarity("property", "title_type", row.getString("title_type"), extractString(propertyData, "title_type", "titleType"), ignoredFields, conflicts, matches);
                        if (!row.isNull("lot_number")) checkFieldSimilarity("property", "lot_number", row.getString("lot_number"), extractString(propertyData, "lot_number", "lotNumber"), ignoredFields, conflicts, matches);
                        if (!row.isNull("mukim")) checkFieldSimilarity("property", "mukim", row.getString("mukim"), extractString(propertyData, "mukim"), ignoredFields, conflicts, matches);
                        if (!row.isNull("district")) checkFieldSimilarity("property", "district", row.getString("district"), extractString(propertyData, "district"), ignoredFields, conflicts, matches);
                        if (!row.isNull("is_owner_occupied")) checkFieldSimilarity("property", "is_owner_occupied", row.getBoolean("is_owner_occupied"), extractBoolean(propertyData, "is_owner_occupied", "isOwnerOccupied"), ignoredFields, conflicts, matches);
                        if (!row.isNull("is_first_time_buyer")) checkFieldSimilarity("property", "is_first_time_buyer", row.getBoolean("is_first_time_buyer"), extractBoolean(propertyData, "is_first_time_buyer", "isFirstTimeBuyer"), ignoredFields, conflicts, matches);
                    }
                }
            }

            boolean hasConflict = !conflicts.isEmpty();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", hasConflict ? "CONFLICT_DETECTED" : "PASSED");
            result.put("hasConflict", hasConflict);
            result.put("thresholdPercent", this.similarityThreshold * 100.0);
            result.put("conflictCount", conflicts.size());
            result.put("conflicts", conflicts);
            result.put("matches", matches);
            if (hasConflict) {
                if (conflicts.size() == 1) {
                    result.put("message", conflicts.get(0).get("message").toString());
                } else {
                    StringBuilder summary = new StringBuilder(String.format("Multiple conflicts detected (%d conflicts): ", conflicts.size()));
                    for (int i = 0; i < conflicts.size(); i++) {
                        if (i > 0) summary.append("; ");
                        summary.append(String.format("[%d] %s", i + 1, conflicts.get(i).get("message")));
                    }
                    result.put("message", summary.toString());
                }
            } else {
                result.put("message", "Data similarity check passed: No conflicts detected");
            }
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void checkFieldSimilarity(
            String table,
            String field,
            Object existingVal,
            Object incomingVal,
            List<String> customIgnored,
            List<Map<String, Object>> conflicts,
            List<Map<String, Object>> matches
    ) {
        if (!this.properties.isSimilarityCheckEnabled()) {
            return;
        }
        if (this.properties.isFieldIgnored(field, customIgnored)) {
            return;
        }
        if (existingVal == null || incomingVal == null) return;
        if (isEmptyOrZeroOrNA(existingVal)) return;
        String sExisting = existingVal.toString().trim();
        String sIncoming = incomingVal.toString().trim();
        if (sExisting.isEmpty() || sIncoming.isEmpty()) return;

        double similarity = computeSimilarity(existingVal, incomingVal);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("table", table);
        detail.put("field", field);
        detail.put("existingValue", sExisting);
        detail.put("incomingValue", sIncoming);
        detail.put("similarityScorePercent", Math.round(similarity * 1000.0) / 10.0);

        if (similarity < this.similarityThreshold) {
            String msg = String.format(
                    "Conflicting data for %s field '%s': existing value '%s' vs incoming value '%s' (similarity %.1f%% is below threshold %.1f%%)",
                    table, field, sExisting, sIncoming, similarity * 100.0, this.similarityThreshold * 100.0
            );
            detail.put("message", msg);
            conflicts.add(detail);
        } else {
            matches.add(detail);
        }
    }

    private boolean isEmptyOrZeroOrNA(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof BigDecimal bd) {
            return bd.compareTo(BigDecimal.ZERO) == 0;
        }
        if (val instanceof Number n) {
            return n.doubleValue() == 0.0;
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return true;
        }
        String lower = s.toLowerCase();
        if (lower.equals("0")
                || lower.equals("0.0")
                || lower.equals("0.00")
                || lower.equals("0.000")
                || lower.equals("n/a")
                || lower.equals("na")
                || lower.equals("n / a")
                || lower.equals("n.a.")
                || lower.equals("not applicable")
                || lower.equals("null")
                || lower.equals("none")
                || lower.equals("-")
                || lower.equals("--")) {
            return true;
        }
        if (isNumeric(s)) {
            try {
                double d = Double.parseDouble(cleanNumeric(s));
                if (d == 0.0) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private double computeSimilarity(Object existingVal, Object incomingVal) {
        if (existingVal == null && incomingVal == null) return 1.0;
        if (existingVal == null || incomingVal == null) return 0.0;

        // Numeric comparison
        if (existingVal instanceof Number || incomingVal instanceof Number
                || (isNumeric(existingVal.toString()) && isNumeric(incomingVal.toString()))) {
            try {
                double v1 = Double.parseDouble(cleanNumeric(existingVal.toString()));
                double v2 = Double.parseDouble(cleanNumeric(incomingVal.toString()));
                if (v1 == v2) return 1.0;
                double max = Math.max(Math.abs(v1), Math.abs(v2));
                if (max == 0.0) return 1.0;
                double diff = Math.abs(v1 - v2);
                return Math.max(0.0, 1.0 - (diff / max));
            } catch (Exception ignored) {}
        }

        // Boolean comparison
        if (existingVal instanceof Boolean || incomingVal instanceof Boolean) {
            boolean b1 = Boolean.parseBoolean(existingVal.toString().trim());
            boolean b2 = Boolean.parseBoolean(incomingVal.toString().trim());
            return b1 == b2 ? 1.0 : 0.0;
        }

        String s1 = existingVal.toString().trim();
        String s2 = incomingVal.toString().trim();

        if (s1.equalsIgnoreCase(s2)) return 1.0;

        // Normalized alphanumeric comparison (ignoring case, hyphens, and whitespace)
        String norm1 = s1.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String norm2 = s2.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (!norm1.isEmpty() && norm1.equalsIgnoreCase(norm2)) {
            return 1.0;
        }

        double levSim = computeLevenshteinSimilarity(s1.toLowerCase(), s2.toLowerCase());
        double tokenSim = computeTokenSimilarity(s1, s2);
        return Math.max(levSim, tokenSim);
    }

    private double computeTokenSimilarity(String s1, String s2) {
        String[] tokens1 = s1.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        String[] tokens2 = s2.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        if (tokens1.length == 0 || tokens2.length == 0) return 0.0;

        double matchScore = 0.0;
        boolean[] used2 = new boolean[tokens2.length];

        for (String t1 : tokens1) {
            if (t1.isEmpty()) continue;
            double bestTokenMatch = 0.0;
            int bestIdx = -1;

            for (int j = 0; j < tokens2.length; j++) {
                if (used2[j]) continue;
                String t2 = tokens2[j];
                if (t2.isEmpty()) continue;

                if (t1.equals(t2)) {
                    bestTokenMatch = 1.0;
                    bestIdx = j;
                    break;
                } else if ((t1.length() == 1 && t2.startsWith(t1)) || (t2.length() == 1 && t1.startsWith(t2))) {
                    if (bestTokenMatch < 0.90) {
                        bestTokenMatch = 0.90;
                        bestIdx = j;
                    }
                } else {
                    double lev = computeLevenshteinSimilarity(t1, t2);
                    if (lev > 0.80 && lev > bestTokenMatch) {
                        bestTokenMatch = lev;
                        bestIdx = j;
                    }
                }
            }

            if (bestIdx != -1) {
                used2[bestIdx] = true;
                matchScore += bestTokenMatch;
            }
        }

        int maxTokens = Math.max(tokens1.length, tokens2.length);
        return matchScore / (double) maxTokens;
    }

    private double computeLevenshteinSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }
        return prev[s2.length()];
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isBlank()) return false;
        String clean = s.replaceAll("[^0-9.]", "").trim();
        if (clean.isEmpty()) return false;
        try {
            Double.parseDouble(clean);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String cleanNumeric(String s) {
        return s.replaceAll("[^0-9.]", "").trim();
    }

    private void setIfPresent(Mutation.WriteBuilder b, String column, String value) {
        if (value != null && !value.isBlank()) {
            b.set(column).to(value.trim());
        }
    }

    private void setIfPresent(Mutation.WriteBuilder b, String column, Boolean value) {
        if (value != null) {
            b.set(column).to(value);
        }
    }

    private void setIfPresent(Mutation.WriteBuilder b, String column, Long value) {
        if (value != null) {
            b.set(column).to(value);
        }
    }

    private void setIfPresent(Mutation.WriteBuilder b, String column, BigDecimal value) {
        if (value != null) {
            b.set(column).to(value);
        }
    }

    private void setIfPresent(Mutation.WriteBuilder b, String column, com.google.cloud.Date value) {
        if (value != null) {
            b.set(column).to(value);
        }
    }

    private String extractString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                String val = map.get(k).toString().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private Boolean extractBoolean(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Object val = map.get(k);
                if (val instanceof Boolean b) return b;
                return Boolean.parseBoolean(val.toString());
            }
        }
        return null;
    }

    private Long extractLong(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Object val = map.get(k);
                if (val instanceof Number n) return n.longValue();
                try {
                    return Long.parseLong(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Object val = map.get(k);
                if (val instanceof BigDecimal bd) return bd;
                if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                try {
                    String clean = val.toString().replaceAll("[^0-9.]", "").trim();
                    if (!clean.isEmpty()) return new BigDecimal(clean);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private com.google.cloud.Date extractDate(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Object val = map.get(k);
                if (val instanceof LocalDate ld) {
                    return com.google.cloud.Date.fromYearMonthDay(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
                }
                try {
                    String str = val.toString().trim();
                    LocalDate ld = LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
                    return com.google.cloud.Date.fromYearMonthDay(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @Override
    public Mono<List<com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication>> findApplicationsByStatus(String status) {
        return Mono.fromCallable(() -> {
            Statement statement = Statement.newBuilder(
                    "SELECT transaction_id, user_id, application_type, status FROM application WHERE status = @status")
                    .bind("status").to(status)
                    .build();

            List<com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication> list = new ArrayList<>();
            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                while (rs.next()) {
                    com.google.cloud.spanner.Struct row = rs.getCurrentRowAsStruct();
                    list.add(new com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication(
                            row.getString("transaction_id"),
                            row.getString("user_id"),
                            row.isNull("application_type") ? "" : row.getString("application_type"),
                            row.isNull("status") ? "" : row.getString("status")
                    ));
                }
            }
            return list;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> updateStatus(String transactionId, String status) {
        return updateStatusAndAiAnalysis(transactionId, status, null);
    }

    @Override
    public Mono<Void> updateStatusAndAiAnalysis(String transactionId, String status, String aiAnalysis) {
        return Mono.fromRunnable(() -> {
            Mutation.WriteBuilder builder = Mutation.newUpdateBuilder("application")
                    .set("transaction_id").to(transactionId)
                    .set("status").to(status);
            if (aiAnalysis != null) {
                builder.set("ai_analysis").to(aiAnalysis);
            }
            databaseClient.write(List.of(builder.build()));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}

