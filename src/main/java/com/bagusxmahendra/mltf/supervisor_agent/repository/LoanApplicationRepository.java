package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import java.util.List;
import reactor.core.publisher.Mono;

public interface LoanApplicationRepository {

    Mono<Boolean> existsByUserIdAndStatus(String userId, String status);

    Mono<Boolean> existsByTransactionIdAndUserId(String transactionId, String userId);

    Mono<List<ApplicationSummaryResponse>> findSummariesByUserId(String userId);

    Mono<ApplicationInquiryResponse> findInquiryByTransactionIdAndUserId(String transactionId, String userId);

    Mono<Void> create(String transactionId, String userId, String applicationType, KycProfile kycProfile);

    Mono<Boolean> deleteByTransactionIdAndUserId(String transactionId, String userId);
}