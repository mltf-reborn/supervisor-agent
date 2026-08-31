package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;

@Service
public class LoanApplicationService {

    private static final String MORTGAGE_LOAN = "Single Application";

    private final KycRepository kycRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public LoanApplicationService(
            KycRepository kycRepository,
            LoanApplicationRepository loanApplicationRepository
    ) {
        this.kycRepository = kycRepository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    public Mono<LoanApplicationResponse> createMortgageLoan(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }

        String sanitizedUserId = userId.trim();
        return kycRepository.findByUserId(sanitizedUserId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "KYC profile not found for user: " + sanitizedUserId
                )))
                .flatMap(profile -> {
                    if (profile.status() != KycStatus.APPROVED) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "KYC verification must have SUCCESS status before applying"
                        ));
                    }

                        return loanApplicationRepository.existsByUserIdAndStatus(sanitizedUserId, "NEW")
                            .flatMap(hasNewApplication -> {
                            if (hasNewApplication) {
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "User already has a loan application with status NEW"
                                ));
                            }

                            String transactionId = "TXN-" + UUID.randomUUID();
                            return loanApplicationRepository.create(transactionId, sanitizedUserId, MORTGAGE_LOAN, profile)
                                .thenReturn(new LoanApplicationResponse(transactionId));
                            });
                });
    }

    public Mono<List<ApplicationSummaryResponse>> getApplications(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        return loanApplicationRepository.findSummariesByUserId(userId.trim());
    }

    public Mono<ApplicationInquiryResponse> getApplicationInquiry(String applicationId, String userId) {
        if (applicationId == null || applicationId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application ID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }

        return loanApplicationRepository.findInquiryByTransactionIdAndUserId(applicationId.trim(), userId.trim())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application not found for customer"
                )));
    }

    public Mono<Map<String, Object>> getApplicationDetails(String transactionId, String userId) {
        if (transactionId == null || transactionId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application ID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        return loanApplicationRepository.getApplicationDetails(transactionId.trim(), userId.trim())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application details not found for transaction: " + transactionId
                )));
    }

    public Mono<Void> saveApplicationDraft(String transactionId, String userId, Map<String, Object> payload) {
        if (transactionId == null || transactionId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application ID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> applicantData = (Map<String, Object>) payload.getOrDefault("applicant", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> applicationData = (Map<String, Object>) payload.getOrDefault("application", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> propertyData = (Map<String, Object>) payload.getOrDefault("property", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> jointApplicantData = (Map<String, Object>) payload.getOrDefault("joint_applicant", 
                payload.getOrDefault("jointApplicant", java.util.Collections.emptyMap()));

        Map<String, Object> mutableApplicantData = new java.util.HashMap<>(applicantData);
        if (jointApplicantData != null && !jointApplicantData.isEmpty()) {
            mutableApplicantData.put("joint_applicant", jointApplicantData);
        }

        Map<String, Object> mutableAppData = new java.util.HashMap<>(applicationData);
        mutableAppData.put("status", "NEW");

        return loanApplicationRepository.updateApplicationData(transactionId.trim(), userId.trim(), mutableApplicantData, mutableAppData, propertyData);
    }

    public Mono<Void> saveApplicationDetails(String transactionId, String userId, Map<String, Object> payload) {
        if (transactionId == null || transactionId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application ID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> applicantData = (Map<String, Object>) payload.getOrDefault("applicant", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> applicationData = (Map<String, Object>) payload.getOrDefault("application", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> propertyData = (Map<String, Object>) payload.getOrDefault("property", java.util.Collections.emptyMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> jointApplicantData = (Map<String, Object>) payload.getOrDefault("joint_applicant", 
                payload.getOrDefault("jointApplicant", java.util.Collections.emptyMap()));

        Map<String, Object> mutableApplicantData = new java.util.HashMap<>(applicantData);
        if (jointApplicantData != null && !jointApplicantData.isEmpty()) {
            mutableApplicantData.put("joint_applicant", jointApplicantData);
        }

        Map<String, Object> mutableAppData = new java.util.HashMap<>(applicationData);
        mutableAppData.put("status", "SUBMITTED");

        return loanApplicationRepository.updateApplicationData(transactionId.trim(), userId.trim(), mutableApplicantData, mutableAppData, propertyData);
    }

    public Mono<List<Map<String, Object>>> getAllLoanApplications() {
        return loanApplicationRepository.findAllApplicationDetails();
    }

    public Mono<Void> deleteApplication(String transactionId, String userId) {
        if (transactionId == null || transactionId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application reference number is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }

        return loanApplicationRepository.deleteByTransactionIdAndUserId(transactionId.trim(), userId.trim())
                .flatMap(deleted -> deleted
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan application not found")));
    }
}