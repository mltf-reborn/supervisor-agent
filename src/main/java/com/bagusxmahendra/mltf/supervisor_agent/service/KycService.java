package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

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
}
