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
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SpannerLoanApplicationRepository implements LoanApplicationRepository {

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
}