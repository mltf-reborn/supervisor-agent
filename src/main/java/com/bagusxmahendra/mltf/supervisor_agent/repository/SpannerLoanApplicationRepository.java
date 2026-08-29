package com.bagusxmahendra.mltf.supervisor_agent.repository;

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
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SpannerLoanApplicationRepository implements LoanApplicationRepository {

    private static final Logger log = LoggerFactory.getLogger(SpannerLoanApplicationRepository.class);

    private final DatabaseClient databaseClient;

    public SpannerLoanApplicationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
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

            // 1. Applicant table update/insert
            if (applicantData != null && !applicantData.isEmpty()) {
                String applicantId = extractString(applicantData, "applicant_id", "applicantId", userId);
                if (applicantId == null || applicantId.isBlank()) {
                    applicantId = userId;
                }

                // Check if applicant record exists
                Statement checkApplicant = Statement.newBuilder(
                                "SELECT applicant_id FROM applicant WHERE transaction_id = @transactionId AND applicant_id = @applicantId")
                        .bind("transactionId").to(transactionId)
                        .bind("applicantId").to(applicantId)
                        .build();

                boolean applicantExists = false;
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkApplicant)) {
                    applicantExists = rs.next();
                }

                Mutation.WriteBuilder b = applicantExists
                        ? Mutation.newUpdateBuilder("applicant")
                        : Mutation.newInsertBuilder("applicant");

                b.set("transaction_id").to(transactionId);
                b.set("applicant_id").to(applicantId);
                if (!applicantExists) {
                    b.set("role").to(extractString(applicantData, "role", "role", "Primary"));
                }

                setIfPresent(b, "role", extractString(applicantData, "role"));
                setIfPresent(b, "full_name", extractString(applicantData, "full_name", "fullName", "name"));
                setIfPresent(b, "id_type", extractString(applicantData, "id_type", "idType"));
                setIfPresent(b, "id_no", extractString(applicantData, "id_no", "idNo", "idNumber"));
                setIfPresent(b, "nationality", extractString(applicantData, "nationality"));
                setIfPresent(b, "race", extractString(applicantData, "race"));
                setIfPresent(b, "bumiputera_status", extractBoolean(applicantData, "bumiputera_status", "bumiputeraStatus", "isBumiputera"));
                setIfPresent(b, "gender", extractString(applicantData, "gender", "sex"));
                setIfPresent(b, "marital_status", extractString(applicantData, "marital_status", "maritalStatus"));
                setIfPresent(b, "date_of_birth", extractDate(applicantData, "date_of_birth", "dateOfBirth", "dob"));
                setIfPresent(b, "dependents_count", extractLong(applicantData, "dependents_count", "dependentsCount"));
                setIfPresent(b, "education_level", extractString(applicantData, "education_level", "educationLevel"));
                setIfPresent(b, "mobile_phone", extractString(applicantData, "mobile_phone", "mobilePhone", "phoneNumber", "mobile"));
                setIfPresent(b, "residential_phone", extractString(applicantData, "residential_phone", "residentialPhone"));
                setIfPresent(b, "email", extractString(applicantData, "email"));
                setIfPresent(b, "perm_address", extractString(applicantData, "perm_address", "permAddress", "address"));
                setIfPresent(b, "perm_postcode", extractString(applicantData, "perm_postcode", "permPostcode", "postalCode", "postcode"));
                setIfPresent(b, "perm_city", extractString(applicantData, "perm_city", "permCity", "city"));
                setIfPresent(b, "perm_state", extractString(applicantData, "perm_state", "permState", "state"));
                setIfPresent(b, "mail_address", extractString(applicantData, "mail_address", "mailAddress", "mailingAddress"));
                setIfPresent(b, "mail_postcode", extractString(applicantData, "mail_postcode", "mailPostcode", "mailingPostcode"));
                setIfPresent(b, "employment_status", extractString(applicantData, "employment_status", "employmentStatus"));
                setIfPresent(b, "employer_name", extractString(applicantData, "employer_name", "employerName"));
                setIfPresent(b, "nature_of_business", extractString(applicantData, "nature_of_business", "natureOfBusiness"));
                setIfPresent(b, "occupation", extractString(applicantData, "occupation"));
                setIfPresent(b, "job_position", extractString(applicantData, "job_position", "jobPosition", "position"));
                setIfPresent(b, "length_of_service_years", extractBigDecimal(applicantData, "length_of_service_years", "lengthOfServiceYears"));
                setIfPresent(b, "monthly_gross_rm", extractBigDecimal(applicantData, "monthly_gross_rm", "monthlyGrossRm", "monthlyIncome", "grossIncome"));
                setIfPresent(b, "annual_gross_rm", extractBigDecimal(applicantData, "annual_gross_rm", "annualGrossRm", "annualIncome"));
                setIfPresent(b, "emergency_name", extractString(applicantData, "emergency_name", "emergencyName"));
                setIfPresent(b, "emergency_relationship", extractString(applicantData, "emergency_relationship", "emergencyRelationship"));
                setIfPresent(b, "emergency_phone", extractString(applicantData, "emergency_phone", "emergencyPhone"));
                setIfPresent(b, "spouse_full_name", extractString(applicantData, "spouse_full_name", "spouseFullName", "spouseName"));
                setIfPresent(b, "spouse_id_no", extractString(applicantData, "spouse_id_no", "spouseIdNo"));
                setIfPresent(b, "spouse_mobile", extractString(applicantData, "spouse_mobile", "spouseMobile"));
                setIfPresent(b, "spouse_employer", extractString(applicantData, "spouse_employer", "spouseEmployer"));
                setIfPresent(b, "spouse_monthly_gross_rm", extractBigDecimal(applicantData, "spouse_monthly_gross_rm", "spouseMonthlyGrossRm"));

                mutations.add(b.build());
            }

            // 2. Application table update
            if (applicationData != null && !applicationData.isEmpty()) {
                Mutation.WriteBuilder b = Mutation.newUpdateBuilder("application");
                b.set("transaction_id").to(transactionId);

                setIfPresent(b, "bank_selection", extractString(applicationData, "bank_selection", "bankSelection", "bank"));
                setIfPresent(b, "application_type", extractString(applicationData, "application_type", "applicationType"));
                setIfPresent(b, "status", extractString(applicationData, "status", "applicationStatus"));
                setIfPresent(b, "facility_type", extractString(applicationData, "facility_type", "facilityType"));
                setIfPresent(b, "facility_purpose", extractString(applicationData, "facility_purpose", "facilityPurpose"));
                setIfPresent(b, "marketing_consent", extractString(applicationData, "marketing_consent", "marketingConsent"));
                setIfPresent(b, "application_date", extractDate(applicationData, "application_date", "applicationDate"));

                mutations.add(b.build());
            }

            // 3. Property table update/insert
            if (propertyData != null && !propertyData.isEmpty()) {
                // Check if property record exists for transaction_id
                Statement checkProperty = Statement.newBuilder(
                                "SELECT property_id FROM property WHERE transaction_id = @transactionId LIMIT 1")
                        .bind("transactionId").to(transactionId)
                        .build();

                String propertyId = null;
                try (ResultSet rs = databaseClient.singleUse().executeQuery(checkProperty)) {
                    if (rs.next()) {
                        propertyId = rs.getString("property_id");
                    }
                }

                boolean propertyExists = (propertyId != null);
                if (propertyId == null) {
                    propertyId = extractString(propertyData, "property_id", "propertyId", "PROP-" + UUID.randomUUID());
                }

                Mutation.WriteBuilder b = propertyExists
                        ? Mutation.newUpdateBuilder("property")
                        : Mutation.newInsertBuilder("property");

                b.set("transaction_id").to(transactionId);
                b.set("property_id").to(propertyId);

                setIfPresent(b, "property_type", extractString(propertyData, "property_type", "propertyType"));
                setIfPresent(b, "property_status", extractString(propertyData, "property_status", "propertyStatus"));
                setIfPresent(b, "developer_name", extractString(propertyData, "developer_name", "developerName"));
                setIfPresent(b, "project_name", extractString(propertyData, "project_name", "projectName"));
                setIfPresent(b, "contractor_name", extractString(propertyData, "contractor_name", "contractorName"));
                setIfPresent(b, "spa_price_rm", extractBigDecimal(propertyData, "spa_price_rm", "spaPriceRm", "spaPrice", "price"));
                setIfPresent(b, "open_market_rm", extractBigDecimal(propertyData, "open_market_rm", "openMarketRm", "openMarketValue"));
                setIfPresent(b, "renovation_value_rm", extractBigDecimal(propertyData, "renovation_value_rm", "renovationValueRm"));
                setIfPresent(b, "property_address", extractString(propertyData, "property_address", "propertyAddress", "address"));
                setIfPresent(b, "property_postcode", extractString(propertyData, "property_postcode", "propertyPostcode", "postcode", "postalCode"));
                setIfPresent(b, "property_city", extractString(propertyData, "property_city", "propertyCity", "city"));
                setIfPresent(b, "property_state", extractString(propertyData, "property_state", "propertyState", "state"));
                setIfPresent(b, "title_number", extractString(propertyData, "title_number", "titleNumber"));
                setIfPresent(b, "title_type", extractString(propertyData, "title_type", "titleType"));
                setIfPresent(b, "lot_number", extractString(propertyData, "lot_number", "lotNumber"));
                setIfPresent(b, "mukim", extractString(propertyData, "mukim"));
                setIfPresent(b, "district", extractString(propertyData, "district"));
                setIfPresent(b, "is_owner_occupied", extractBoolean(propertyData, "is_owner_occupied", "isOwnerOccupied"));
                setIfPresent(b, "is_first_time_buyer", extractBoolean(propertyData, "is_first_time_buyer", "isFirstTimeBuyer"));

                mutations.add(b.build());
            }

            if (!mutations.isEmpty()) {
                log.info("Executing {} database mutations to update application tables for transaction: {}",
                        mutations.size(), transactionId);
                databaseClient.write(mutations);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
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
}
