package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExtractedProfileData;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SupervisorKycDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycRepository kycRepository;
    private final StorageService storageService;
    private final KycSupervisorAgentService supervisorAgentService;

    public KycService(
            KycRepository kycRepository,
            StorageService storageService,
            KycSupervisorAgentService supervisorAgentService
    ) {
        this.kycRepository = kycRepository;
        this.storageService = storageService;
        this.supervisorAgentService = supervisorAgentService;
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
     * @param email    the authenticated user's email (extracted from JWT)
     * @param fullName optional full name hint provided by the client
     * @param document identity document file
     * @param selfie   webcam selfie photo
     * @return Mono of KycVerifyResponse matching UI contract
     */
    public Mono<KycVerifyResponse> verify(
            String userId,
            String email,
            String fullName,
            FilePart document,
            FilePart selfie
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }
        if (email == null || email.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required"));
        }
        if (document == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity document file ('document') is required"));
        }
        if (selfie == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie file ('selfie') is required"));
        }

        String sanitizedUserId = userId.trim();
        String sanitizedEmail = email.trim();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String referenceId = "KYC-REV-2026-" + randomSuffix;
        String sessionId = referenceId;
        Instant now = Instant.now();

        log.info("KYC verify submission – userId: {}, email: {}, referenceId: {}, sessionId: {}",
                sanitizedUserId, sanitizedEmail, referenceId, sessionId);

        // Store the 2 files into Google Cloud Storage (BucketName: mltf-bucket) with the session ID
        Mono<FileUploadResult> uploadDocMono = storageService.uploadFile(document, sessionId, "document");
        Mono<FileUploadResult> uploadSelfieMono = storageService.uploadFile(selfie, sessionId, "selfie");

        return Mono.zip(uploadDocMono, uploadSelfieMono)
                .flatMap(tuple -> {
                    FileUploadResult docResult = tuple.getT1();
                    FileUploadResult selfieResult = tuple.getT2();

                    log.info("GCS upload complete for sessionId: {} – documentUrl: {}, selfieUrl: {}",
                            sessionId, docResult.fileUrl(), selfieResult.fileUrl());

                    // Handoff to LLM Supervisor Model (Google ADK) to orchestrate KYC verification
                    return supervisorAgentService.evaluateKyc(
                            sanitizedUserId,
                            fullName,
                            docResult.fileUrl(),
                            selfieResult.fileUrl(),
                            docResult.contentType(),
                            selfieResult.contentType()
                    ).flatMap(decision -> {
                        log.info("Supervisor Agent concluded KYC verification for user: {} – decision: {}, confidence: {}%, riskScore: {}",
                                sanitizedUserId, decision.getDecision(), decision.getDecisionConfidence(), decision.getRiskScore());

                        KycStatus kycStatus = decision.toKycStatus();
                        ExtractedProfileData ext = decision.getExtractedProfile();

                        String effectiveFullName = (fullName != null && !fullName.isBlank())
                                ? fullName
                                : (ext != null && ext.getFullName() != null && !ext.getFullName().isBlank() ? ext.getFullName() : "Applicant");

                        String idCardNumber = ext != null ? ext.getIdCardNumber() : null;
                        String idCardType = ext != null ? ext.getIdCardType() : null;

                        LocalDate dob = null;
                        if (ext != null && ext.getDateOfBirth() != null && !ext.getDateOfBirth().isBlank()) {
                            try {
                                dob = LocalDate.parse(ext.getDateOfBirth().trim());
                            } catch (Exception ignored) {}
                        }

                        String address = ext != null ? ext.getAddress() : null;
                        String city = ext != null ? ext.getCity() : null;
                        String postalCode = ext != null ? ext.getPostalCode() : null;
                        String country = ext != null ? ext.getCountry() : null;
                        String nationality = ext != null ? ext.getNationality() : null;
                        java.math.BigDecimal monthlyIncome = ext != null ? ext.getMonthlyIncome() : null;
                        String occupation = ext != null ? ext.getOccupation() : null;

                        String remarks = decision.getRemarks() != null ? decision.getRemarks() : decision.getExplanation();
                        if (remarks == null || remarks.isBlank()) {
                            remarks = "GCS document: " + docResult.fileUrl() + ", selfie: " + selfieResult.fileUrl();
                        }

                        Double riskScore = decision.getRiskScore();
                        String riskLevel = decision.getRiskLevel();
                        String rejectionReason = decision.getRejectionReason();

                        KycProfile profile = new KycProfile(
                                sanitizedUserId,
                                effectiveFullName,
                                sanitizedEmail,
                                "88888",
                                idCardNumber,
                                idCardType,
                                dob,
                                address,
                                city,
                                postalCode,
                                country,
                                nationality,
                                occupation,
                                monthlyIncome,
                                kycStatus,
                                riskScore,
                                riskLevel,
                                rejectionReason,
                                remarks,
                                "supervisor-agent-llm",
                                now,
                                now,
                                now
                        );

                        String message = decision.getExplanation() != null ? decision.getExplanation() :
                                (kycStatus == KycStatus.APPROVED ? "KYC documents verified and approved successfully." :
                                 kycStatus == KycStatus.REJECTED ? "KYC verification rejected." :
                                 "KYC documents received successfully. Verification is in progress.");

                        return kycRepository.save(profile)
                                .thenReturn(KycVerifyResponse.from(profile, referenceId, message));
                    });
                });
    }

    /**
     * Backward-compatible overload without explicit email parameter.
     */
    public Mono<KycVerifyResponse> verify(
            String userId,
            String fullName,
            FilePart document,
            FilePart selfie
    ) {
        return verify(userId, "dummy@gmail.com", fullName, document, selfie);
    }

    /**
     * Minimal submission overload (for fallback/testing).
     */
    public Mono<KycVerifyResponse> verify(String userId, String email, String fullName) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required"));
        }
        if (email == null || email.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required"));
        }

        String sanitizedUserId = userId.trim();
        String sanitizedEmail = email.trim();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String referenceId = "KYC-REV-2026-" + randomSuffix;
        Instant now = Instant.now();

        KycProfile profile = new KycProfile(
                sanitizedUserId,
                fullName,
                sanitizedEmail,
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

    public Mono<KycVerifyResponse> verify(String userId, String fullName) {
        return verify(userId, "dummy@gmail.com", fullName);
    }
}
