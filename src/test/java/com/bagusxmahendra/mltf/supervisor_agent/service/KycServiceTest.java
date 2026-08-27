package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
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

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycRepository, storageService);
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
    void verify_withValidFiles_shouldStoreInGcsAndReturnInReviewResponse() {
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

        when(storageService.uploadFile(eq(document), any(), eq("document"))).thenReturn(Mono.just(docUpload));
        when(storageService.uploadFile(eq(selfie), any(), eq("selfie"))).thenReturn(Mono.just(selfieUpload));
        when(kycRepository.save(any(KycProfile.class))).thenReturn(Mono.empty());

        StepVerifier.create(kycService.verify("usr_1001", "Ahmad Syazwan", document, selfie))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("IN_REVIEW", response.status());
                    assertNotNull(response.referenceId());
                    assertNotNull(response.verifiedData());
                    assertEquals("usr_1001", response.verifiedData().userId());
                    assertEquals("Ahmad Syazwan", response.verifiedData().fullName());
                    assertEquals("IN_REVIEW", response.verifiedData().status());
                })
                .verifyComplete();

        org.mockito.ArgumentCaptor<KycProfile> profileCaptor = org.mockito.ArgumentCaptor.forClass(KycProfile.class);
        verify(kycRepository).save(profileCaptor.capture());
        KycProfile savedProfile = profileCaptor.getValue();
        assertEquals("GCS document: gs://mltf-bucket/session-1/document/id.jpg, selfie: gs://mltf-bucket/session-1/selfie/selfie.jpg", savedProfile.remarks());
    }

    @Test
    void verify_withNullDocument_shouldReturnBadRequest() {
        FilePart selfie = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", "Ahmad Syazwan", null, selfie))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void verify_withNullSelfie_shouldReturnBadRequest() {
        FilePart document = mock(FilePart.class);
        StepVerifier.create(kycService.verify("usr_1001", "Ahmad Syazwan", document, null))
                .expectError(ResponseStatusException.class)
                .verify();
    }
}
