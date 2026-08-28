package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;

@Service
public class LoanApplicationService {

    private static final String MORTGAGE_LOAN = "MORTGAGE_LOAN";

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