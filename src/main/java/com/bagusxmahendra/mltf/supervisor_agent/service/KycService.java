package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycRepository kycRepository;

    public KycService(KycRepository kycRepository) {
        this.kycRepository = kycRepository;
    }

    /**
     * Get KYC status and profile details for a given userId.
     *
     * @param userId the ID of the user
     * @return Mono of KycStatusResponse or 404 error if not found
     */
    public Mono<KycStatusResponse> getStatus(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }

        String sanitizedUserId = userId.trim();
        log.info("Fetching KYC status for userId: {}", sanitizedUserId);

        return kycRepository.findByUserId(sanitizedUserId)
                .map(KycStatusResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "KYC profile not found for user: " + sanitizedUserId
                )));
    }

    /**
     * Get KYC status by email address.
     *
     * @param email the email address
     * @return Mono of KycStatusResponse or 404 error if not found
     */
    public Mono<KycStatusResponse> getStatusByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required"));
        }

        String sanitizedEmail = email.trim();
        log.info("Fetching KYC status for email: {}", sanitizedEmail);

        return kycRepository.findByEmail(sanitizedEmail)
                .map(KycStatusResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "KYC profile not found for email: " + sanitizedEmail
                )));
    }

    /**
     * Persists a new KYC submission for the given user and returns a response
     * shaped to match the Angular UI's {@code KycStatusResponse} + {@code VerifiedKycData}
     * interfaces.
     *
     * <p>The profile is saved with {@link KycStatus#IN_REVIEW} and a generated reference ID.
     * The reference ID is returned at the top level and also inside {@code verifiedData}
     * so the UI can display it on the result screen.</p>
     *
     * @param userId   the authenticated user's ID (extracted from JWT)
     * @param fullName optional full name hint provided by the client
     * @return Mono of KycVerifyResponse matching the UI contract
     */
    public Mono<KycVerifyResponse> verify(String userId, String fullName) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }

        String sanitizedUserId = userId.trim();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String referenceId = "KYC-REV-2026-" + randomSuffix;
        Instant now = Instant.now();

        log.info("KYC verify submission – userId: {}, referenceId: {}", sanitizedUserId, referenceId);

        // Build a minimal profile to persist; leave demographic fields null until
        // the actual verification agent populates them.
        KycProfile profile = new KycProfile(
                sanitizedUserId,
                fullName,          // fullName
                "dummy@gmail.com",              // email
                "88888",              // phoneNumber
                "88888",              // idCardNumber
                null,              // idCardType
                null,              // dateOfBirth
                null,              // address
                null,              // city
                null,              // postalCode
                null,              // country
                null,              // nationality
                null,              // occupation
                null,              // monthlyIncome
                KycStatus.IN_REVIEW,
                null,              // riskScore
                null,              // riskLevel
                null,              // rejectionReason
                null,              // remarks
                null,              // verifiedBy
                now,               // verifiedAt – submission timestamp
                now,               // createdAt
                now                // updatedAt
        );

        return kycRepository.save(profile)
                .thenReturn(KycVerifyResponse.inReview(profile, referenceId));
    }
}

