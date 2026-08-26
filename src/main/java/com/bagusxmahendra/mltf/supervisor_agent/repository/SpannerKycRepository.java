package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
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

import java.util.Collections;

@Repository
public class SpannerKycRepository implements KycRepository {

    private static final Logger log = LoggerFactory.getLogger(SpannerKycRepository.class);

    private static final String SELECT_KYC_COLUMNS =
            "SELECT user_id, full_name, email, phone_number, id_card_number, id_card_type, " +
            "date_of_birth, address, city, postal_code, country, nationality, " +
            "occupation, monthly_income, status, risk_score, risk_level, " +
            "rejection_reason, remarks, verified_by, verified_at, created_at, updated_at " +
            "FROM kyc_profile ";

    private final DatabaseClient databaseClient;

    public SpannerKycRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<KycProfile> findByUserId(String userId) {
        return Mono.fromCallable(() -> {
            log.debug("Executing Spanner query to find KYC profile for userId: {}", userId);
            Statement statement = Statement.newBuilder(SELECT_KYC_COLUMNS + "WHERE user_id = @userId")
                    .bind("userId").to(userId)
                    .build();

            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                if (rs.next()) {
                    return KycProfile.fromStruct(rs.getCurrentRowAsStruct());
                }
                return null;
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<KycProfile> findByEmail(String email) {
        return Mono.fromCallable(() -> {
            log.debug("Executing Spanner query to find KYC profile for email: {}", email);
            Statement statement = Statement.newBuilder(SELECT_KYC_COLUMNS + "WHERE email = @email")
                    .bind("email").to(email)
                    .build();

            try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
                if (rs.next()) {
                    return KycProfile.fromStruct(rs.getCurrentRowAsStruct());
                }
                return null;
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Void> save(KycProfile profile) {
        return Mono.fromRunnable(() -> {
            log.debug("Saving KYC profile to Spanner for userId: {}", profile.userId());
            Mutation.WriteBuilder builder = Mutation.newInsertOrUpdateBuilder("kyc_profile")
                    .set("user_id").to(profile.userId())
                    .set("full_name").to(profile.fullName())
                    .set("email").to(profile.email())
                    .set("phone_number").to(profile.phoneNumber())
                    .set("id_card_number").to(profile.idCardNumber())
                    .set("id_card_type").to(profile.idCardType())
                    .set("address").to(profile.address())
                    .set("city").to(profile.city())
                    .set("postal_code").to(profile.postalCode())
                    .set("country").to(profile.country())
                    .set("nationality").to(profile.nationality())
                    .set("occupation").to(profile.occupation())
                    .set("monthly_income").to(profile.monthlyIncome())
                    .set("status").to(profile.status() != null ? profile.status().name() : KycStatus.PENDING.name())
                    .set("risk_score").to(profile.riskScore())
                    .set("risk_level").to(profile.riskLevel())
                    .set("rejection_reason").to(profile.rejectionReason())
                    .set("remarks").to(profile.remarks())
                    .set("verified_by").to(profile.verifiedBy())
                    .set("updated_at").to(Value.COMMIT_TIMESTAMP);

            if (profile.dateOfBirth() != null) {
                builder.set("date_of_birth").to(Date.fromYearMonthDay(
                        profile.dateOfBirth().getYear(),
                        profile.dateOfBirth().getMonthValue(),
                        profile.dateOfBirth().getDayOfMonth()
                ));
            }

            if (profile.verifiedAt() != null) {
                builder.set("verified_at").to(Timestamp.ofTimeSecondsAndNanos(
                        profile.verifiedAt().getEpochSecond(),
                        profile.verifiedAt().getNano()
                ));
            }

            if (profile.createdAt() != null) {
                builder.set("created_at").to(Timestamp.ofTimeSecondsAndNanos(
                        profile.createdAt().getEpochSecond(),
                        profile.createdAt().getNano()
                ));
            } else {
                builder.set("created_at").to(Value.COMMIT_TIMESTAMP);
            }

            databaseClient.write(Collections.singletonList(builder.build()));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }
}
