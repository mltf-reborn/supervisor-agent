package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.bagusxmahendra.mltf.supervisor_agent.repository.KycRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @Mock
    private KycRepository kycRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Test
    void createMortgageLoan_whenKycIsNotSuccessful_shouldRejectWithoutInsert() {
        KycProfile profile = new KycProfile(
                "usr_1001", "John Doe", "john@example.com", null, null, null,
                null, null, null, null, null, null, null, null,
                KycStatus.IN_REVIEW, null, null, null, null, null, null, null, null
        );
        when(kycRepository.findByUserId("usr_1001")).thenReturn(Mono.just(profile));
        LoanApplicationService service = new LoanApplicationService(kycRepository, loanApplicationRepository);

        StepVerifier.create(service.createMortgageLoan("usr_1001"))
                .expectErrorMatches(error -> error.getMessage().contains("SUCCESS status"))
                .verify();

        verify(loanApplicationRepository, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createMortgageLoan_whenNewApplicationExists_shouldRejectWithoutInsert() {
        KycProfile profile = new KycProfile(
                "usr_1001", "John Doe", "john@example.com", null, null, null,
                null, null, null, null, null, null, null, null,
                KycStatus.APPROVED, null, null, null, null, null, null, null, null
        );
        when(kycRepository.findByUserId("usr_1001")).thenReturn(Mono.just(profile));
        when(loanApplicationRepository.existsByUserIdAndStatus("usr_1001", "NEW")).thenReturn(Mono.just(true));
        LoanApplicationService service = new LoanApplicationService(kycRepository, loanApplicationRepository);

        StepVerifier.create(service.createMortgageLoan("usr_1001"))
                .expectErrorMatches(error -> error.getMessage().contains("status NEW"))
                .verify();

        verify(loanApplicationRepository, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}