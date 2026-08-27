package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycRepository kycRepository;
    private final StorageService storageService;

    public KycService(KycRepository kycRepository, StorageService storageService) {
        this.kycRepository = kycRepository;
        this.storageService = storageService;
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
     * Enhanced KYC verification: stores the 2 files (identity document and webcam selfie)
     * into Google Cloud Storage (BucketName configured in property file: mltf-bucket)
     * with the session ID, saves profile to Spanner, and returns the sealed UI response.
     *
     * @param userId   the authenticated user's ID (extracted from JWT)
     * @param fullName optional full name hint provided by the client
     * @param document identity document file
     * @param selfie   webcam selfie photo
     * @return Mono of KycVerifyResponse matching UI contract
     */
    public Mono<KycVerifyResponse> verify(
            String userId,
            String fullName,
            FilePart document,
            FilePart selfie
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }
        if (document == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity document file ('document') is required"));
        }
        if (selfie == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie file ('selfie') is required"));
        }

        String sanitizedUserId = userId.trim();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String referenceId = "KYC-REV-2026-" + randomSuffix;
        String sessionId = referenceId;
        Instant now = Instant.now();

        log.info("KYC verify submission – userId: {}, referenceId: {}, sessionId: {}",
                sanitizedUserId, referenceId, sessionId);

        // Store the 2 files into Google Cloud Storage (BucketName: mltf-bucket) with the session ID
        Mono<FileUploadResult> uploadDocMono = storageService.uploadFile(document, sessionId, "document");
        Mono<FileUploadResult> uploadSelfieMono = storageService.uploadFile(selfie, sessionId, "selfie");

        return Mono.zip(uploadDocMono, uploadSelfieMono)
                .flatMap(tuple -> {
                    FileUploadResult docResult = tuple.getT1();
                    FileUploadResult selfieResult = tuple.getT2();

                    log.info("GCS upload complete for sessionId: {} – documentUrl: {}, selfieUrl: {}",
                            sessionId, docResult.fileUrl(), selfieResult.fileUrl());

                    KycProfile profile = new KycProfile(
                            sanitizedUserId,
                            fullName,
                            "dummy@gmail.com",
                            "88888",
                            "88888",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            KycStatus.IN_REVIEW,
                            null,
                            null,
                            null,
                            "GCS document: " + docResult.fileUrl() + ", selfie: " + selfieResult.fileUrl(),
                            null,
                            now,
                            now,
                            now
                    );

                    return kycRepository.save(profile)
                            .thenReturn(KycVerifyResponse.inReview(profile, referenceId));
                });
    }

    /**
     * Minimal submission overload (for fallback/testing).
     */
    public Mono<KycVerifyResponse> verify(String userId, String fullName) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }

        String sanitizedUserId = userId.trim();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String referenceId = "KYC-REV-2026-" + randomSuffix;
        Instant now = Instant.now();

        KycProfile profile = new KycProfile(
                sanitizedUserId,
                fullName,
                "dummy@gmail.com",
                "88888",
                "88888",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                KycStatus.IN_REVIEW,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                now
        );

        return kycRepository.save(profile)
                .thenReturn(KycVerifyResponse.inReview(profile, referenceId));
    }
}
