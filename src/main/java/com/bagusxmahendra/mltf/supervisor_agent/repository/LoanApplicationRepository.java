package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface LoanApplicationRepository {

    Mono<Boolean> existsByUserIdAndStatus(String userId, String status);

    Mono<Boolean> existsByTransactionIdAndUserId(String transactionId, String userId);

    Mono<List<ApplicationSummaryResponse>> findSummariesByUserId(String userId);

    Mono<ApplicationInquiryResponse> findInquiryByTransactionIdAndUserId(String transactionId, String userId);

    Mono<Void> create(String transactionId, String userId, String applicationType, KycProfile kycProfile);

    Mono<Map<String, Object>> getApplicationDetails(String transactionId, String userId);

    Mono<Boolean> deleteByTransactionIdAndUserId(String transactionId, String userId);

    Mono<Void> updateApplicationData(
            String transactionId,
            String userId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    );

    Mono<Void> saveApplication(String transactionId, String userId, Map<String, Object> applicationData);

    Mono<Void> saveApplicant(String transactionId, String applicantId, Map<String, Object> applicantData);

    Mono<Void> saveProperty(String transactionId, String propertyId, Map<String, Object> propertyData);

    Mono<Map<String, Object>> checkSimilarity(
            String transactionId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    );

    Mono<Map<String, Object>> checkSimilarity(
            String transactionId,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData,
            List<String> ignoredFields
    );

    Mono<List<com.bagusxmahendra.mltf.supervisor_agent.model.SubmittedApplication>> findApplicationsByStatus(String status);

    Mono<Void> updateStatus(String transactionId, String status);

    Mono<Void> updateStatusAndAiAnalysis(String transactionId, String status, String aiAnalysis);
}

