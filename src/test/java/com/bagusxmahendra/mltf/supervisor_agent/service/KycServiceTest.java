package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private KycRepository kycRepository;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycRepository);
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
                    assertEquals("usr_1001", response.userId());
                    assertEquals(KycStatus.APPROVED, response.status());
                    assertEquals("John Doe", response.fullName());
                    assertEquals("john.doe@example.com", response.email());
                    assertEquals(12.5, response.riskScore());
                    assertEquals("LOW", response.riskLevel());
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
                    assertEquals("usr_1002", response.userId());
                    assertEquals(KycStatus.IN_REVIEW, response.status());
                    assertEquals("Jane Smith", response.fullName());
                })
                .verifyComplete();
    }
}
