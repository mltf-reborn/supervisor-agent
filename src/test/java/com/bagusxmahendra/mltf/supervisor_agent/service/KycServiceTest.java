package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ExtractedProfileData;
import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SupervisorKycDecision;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.bagusxmahendra.mltf.supervisor_agent.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private KycRepository kycRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private KycSupervisorAgentService supervisorAgentService;

    @Mock
    private AuditLogService auditLogService;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycRepository, storageService, supervisorAgentService, auditLogService);
        org.mockito.Mockito.lenient().when(auditLogService.logKycVerification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(reactor.core.publisher.Mono.empty());
    }

    @Test
    void getStatus_withValidUserId_shouldReturnKycStatusResponse() {
        KycProfile profile = new KycProfile(
                "usr_1001",
                "John Doe",
                "john.doe@example.com",
                "+1-555-0199",
                "ID-987654321",
                "NATIONAL_ID",
                LocalDate.of(1988, 5, 12),
                "123 Main St",
                "New York",
                "10001",
                "USA",
                "American",
                "Software Engineer",
                BigDecimal.valueOf(12500.00),
                KycStatus.APPROVED,
                12.5,
                "LOW",
                null,
                "Verified",
                "supervisor_01",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z")
        );

        when(kycRepository.findByUserId("usr_1001")).thenReturn(Mono.just(profile));

        StepVerifier.create(kycService.getStatus("usr_1001"))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(KycStatus.APPROVED, response.status());
                })
                .verifyComplete();
    }

    @Test
    void getStatus_whenUserNotFound_shouldReturnNotFoundException() {
        when(kycRepository.findByUserId("unknown_user")).thenReturn(Mono.empty());

        StepVerifier.create(kycService.getStatus("unknown_user"))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void getStatus_withBlankUserId_shouldReturnBadRequestException() {
        StepVerifier.create(kycService.getStatus("   "))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void getStatusByEmail_withValidEmail_shouldReturnKycStatusResponse() {
        KycProfile profile = new KycProfile(
                "usr_1002",
                "Jane Smith",
                "jane.smith@example.com",
                null, null, null, null, null, null, null, null, null, null, null,
                KycStatus.IN_REVIEW,
                35.0, "MEDIUM", null, null, null, null, null, null
        );

        when(kycRepository.findByEmail("jane.smith@example.com")).thenReturn(Mono.just(profile));

        StepVerifier.create(kycService.getStatusByEmail("jane.smith@example.com"))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(KycStatus.IN_REVIEW, response.status());
                })
                .verifyComplete();
    }

    @Test
    void verify_withApprovedDecision_shouldStoreInGcsAndReturnApprovedResponse() {
        FilePart document = mock(FilePart.class);
        FilePart selfie = mock(FilePart.class);

        FileUploadResult docUpload = new FileUploadResult(
                "id.jpg",
                "image/jpeg",
                1024L,
                "mltf-bucket",
                "session-1/document/id.jpg",
                "gs://mltf-bucket/session-1/document/id.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/document/id.jpg"
        );

        FileUploadResult selfieUpload = new FileUploadResult(
                "selfie.jpg",
                "image/jpeg",
                2048L,
                "mltf-bucket",
                "session-1/selfie/selfie.jpg",
                "gs://mltf-bucket/session-1/selfie/selfie.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/selfie/selfie.jpg"
        );

        SupervisorKycDecision decision = new SupervisorKycDecision();
        decision.setDecision("APPROVED");
        decision.setDecisionConfidence(98.5);
        decision.setRiskScore(5.0);
        decision.setRiskLevel("LOW");
        decision.setExplanation("KYC verification approved successfully with authentic document and biometric match.");
        decision.setRemarks("SUCCESS: Document Score: 98.0%, Biometric Match: 98.5% (MATCH), External Registry: VERIFIED.");

        ExtractedProfileData ext = new ExtractedProfileData();
        ext.setFullName("Ahmad Syazwan");
        ext.setIdCardNumber("940822-10-5819");
        ext.setIdCardType("MyKad (National Identity Card)");
        ext.setDateOfBirth("1994-08-22");
        ext.setNationality("Malaysian");
        decision.setExtractedProfile(ext);

        when(storageService.uploadFile(eq(document), any(), eq("document"))).thenReturn(Mono.just(docUpload));
        when(storageService.uploadFile(eq(selfie), any(), eq("selfie"))).thenReturn(Mono.just(selfieUpload));
        when(supervisorAgentService.evaluateKyc(eq("usr_1001"), eq("Ahmad Syazwan"), eq("gs://mltf-bucket/session-1/document/id.jpg"), eq("gs://mltf-bucket/session-1/selfie/selfie.jpg"), any(), any()))
                .thenReturn(Mono.just(decision));
        when(kycRepository.save(any(KycProfile.class))).thenReturn(Mono.empty());

        StepVerifier.create(kycService.verify("usr_1001", "ahmad.syazwan@example.com", "Ahmad Syazwan", document, selfie))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("APPROVED", response.status());
                    assertNotNull(response.referenceId());
                    assertNotNull(response.verifiedData());
                    assertEquals("usr_1001", response.verifiedData().userId());
                    assertEquals("Ahmad Syazwan", response.verifiedData().fullName());
                    assertEquals("APPROVED", response.verifiedData().status());
                })
                .verifyComplete();

        org.mockito.ArgumentCaptor<KycProfile> profileCaptor = org.mockito.ArgumentCaptor.forClass(KycProfile.class);
        verify(kycRepository).save(profileCaptor.capture());
        KycProfile savedProfile = profileCaptor.getValue();
        assertEquals(KycStatus.APPROVED, savedProfile.status());
        assertEquals("940822-10-5819", savedProfile.idCardNumber());
        assertEquals("ahmad.syazwan@example.com", savedProfile.email());
    }

    @Test
    void verify_withInReviewDecision_shouldStoreInGcsAndReturnInReviewResponse() {
        FilePart document = mock(FilePart.class);
        FilePart selfie = mock(FilePart.class);

        FileUploadResult docUpload = new FileUploadResult(
                "id.jpg", "image/jpeg", 1024L, "mltf-bucket",
                "session-1/document/id.jpg", "gs://mltf-bucket/session-1/document/id.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/document/id.jpg"
        );
        FileUploadResult selfieUpload = new FileUploadResult(
                "selfie.jpg", "image/jpeg", 2048L, "mltf-bucket",
                "session-1/selfie/selfie.jpg", "gs://mltf-bucket/session-1/selfie/selfie.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/selfie/selfie.jpg"
        );

        SupervisorKycDecision decision = new SupervisorKycDecision();
        decision.setDecision("IN_REVIEW");
        decision.setDecisionConfidence(68.0);
        decision.setRiskScore(45.0);
        decision.setRiskLevel("MEDIUM");
        decision.setExplanation("Biometric score 68.0% requires manual officer review.");

        ExtractedProfileData ext = new ExtractedProfileData();
        ext.setFullName("Ahmad Syazwan");
        decision.setExtractedProfile(ext);

        when(storageService.uploadFile(eq(document), any(), eq("document"))).thenReturn(Mono.just(docUpload));
        when(storageService.uploadFile(eq(selfie), any(), eq("selfie"))).thenReturn(Mono.just(selfieUpload));
        when(supervisorAgentService.evaluateKyc(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(decision));
        when(kycRepository.save(any(KycProfile.class))).thenReturn(Mono.empty());

        StepVerifier.create(kycService.verify("usr_1001", "ahmad.syazwan@example.com", "Ahmad Syazwan", document, selfie))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("IN_REVIEW", response.status());
                    assertEquals("IN_REVIEW", response.verifiedData().status());
                })
                .verifyComplete();
    }

    @Test
    void verify_withRejectedDecision_shouldStoreInGcsAndReturnRejectedResponse() {
        FilePart document = mock(FilePart.class);
        FilePart selfie = mock(FilePart.class);

        FileUploadResult docUpload = new FileUploadResult(
                "id.jpg", "image/jpeg", 1024L, "mltf-bucket",
                "session-1/document/id.jpg", "gs://mltf-bucket/session-1/document/id.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/document/id.jpg"
        );
        FileUploadResult selfieUpload = new FileUploadResult(
                "selfie.jpg", "image/jpeg", 2048L, "mltf-bucket",
                "session-1/selfie/selfie.jpg", "gs://mltf-bucket/session-1/selfie/selfie.jpg",
                "https://storage.googleapis.com/mltf-bucket/session-1/selfie/selfie.jpg"
        );

        SupervisorKycDecision decision = new SupervisorKycDecision();
        decision.setDecision("REJECTED");
        decision.setDecisionConfidence(20.0);
        decision.setRiskScore(95.0);
        decision.setRiskLevel("CRITICAL");
        decision.setRejectionReason("Document pixel tampering detected.");
        decision.setExplanation("KYC rejected: forged document.");

        when(storageService.uploadFile(eq(document), any(), eq("document"))).thenReturn(Mono.just(docUpload));
        when(storageService.uploadFile(eq(selfie), any(), eq("selfie"))).thenReturn(Mono.just(selfieUpload));
        when(supervisorAgentService.evaluateKyc(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(decision));
        when(kycRepository.save(any(KycProfile.class))).thenReturn(Mono.empty());

        StepVerifier.create(kycService.verify("usr_1001", "ahmad.syazwan@example.com", "Ahmad Syazwan", document, selfie))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("REJECTED", response.status());
                    assertEquals("REJECTED", response.verifiedData().status());
                })
                .verifyComplete();
    }

    @Test
    void verify_withNullEmail_shouldReturnBadRequest() {
        FilePart document = mock(FilePart.class);
        FilePart selfie = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", null, "Ahmad Syazwan", document, selfie))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void verify_withBlankEmail_shouldReturnBadRequest() {
        FilePart document = mock(FilePart.class);
        FilePart selfie = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", "   ", "Ahmad Syazwan", document, selfie))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void verify_withNullDocument_shouldReturnBadRequest() {
        FilePart selfie = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", "ahmad.syazwan@example.com", "Ahmad Syazwan", null, selfie))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void verify_withNullSelfie_shouldReturnBadRequest() {
        FilePart document = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", "ahmad.syazwan@example.com", "Ahmad Syazwan", document, null))
                .expectError(ResponseStatusException.class)
                .verify();
    }
}
