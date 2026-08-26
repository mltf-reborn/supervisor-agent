package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import reactor.core.publisher.Mono;

public interface KycRepository {

    /**
     * Find KYC profile by user ID.
     *
     * @param userId the user ID to search for
     * @return Mono containing the KycProfile if found, or empty Mono if not found
     */
    Mono<KycProfile> findByUserId(String userId);

    /**
     * Find KYC profile by email address.
     *
     * @param email the email to search for
     * @return Mono containing the KycProfile if found, or empty Mono if not found
     */
    Mono<KycProfile> findByEmail(String email);

    /**
     * Upsert a KYC profile into the database.
     *
     * @param profile the profile to persist
     * @return Mono<Void> indicating completion
     */
    Mono<Void> save(KycProfile profile);
}
