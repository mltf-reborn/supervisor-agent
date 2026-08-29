package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationToolsTest {

    @Mock
    private DocumentProcessingClient documentProcessingClient;

    @Mock
    private ApplicationDocumentRepository applicationDocumentRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    private LoanApplicationTools tools;

    @BeforeEach
    void setUp() {
        tools = new LoanApplicationTools(documentProcessingClient, applicationDocumentRepository, loanApplicationRepository);
    }

    @Test
    void validateDocument_shouldReturnMapOfResponse() {
        DocProcessingResponseDto responseDto = new DocProcessingResponseDto();
        responseDto.setStatus("SUCCESS");
        responseDto.setDetectedDocumentType("PASSPORT");
        responseDto.setExtractedFields(Map.of("nationality", "Malaysian", "race", "Malay"));

        when(documentProcessingClient.processDocument(eq("gs://bucket/doc.pdf"), eq("application/pdf"), any()))
                .thenReturn(Mono.just(responseDto));

        Map<String, Object> result = tools.validateDocument("gs://bucket/doc.pdf", "application/pdf", null);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("PASSPORT", result.get("detectedDocumentType"));
    }

    @Test
    void saveApplicationData_shouldCallRepository() {
        when(loanApplicationRepository.updateApplicationData(
                eq("TXN-1"), eq("usr_1"), any(), any(), any()
        )).thenReturn(Mono.empty());

        Map<String, Object> applicantData = Map.of("race", "Malay", "nationality", "Malaysian");
        Map<String, Object> result = tools.saveApplicationData("TXN-1", "usr_1", applicantData, Map.of(), Map.of());

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(loanApplicationRepository).updateApplicationData(eq("TXN-1"), eq("usr_1"), eq(applicantData), any(), any());
    }

    @Test
    void saveApplication_shouldCallRepository() {
        when(loanApplicationRepository.saveApplication(eq("TXN-1"), eq("usr_1"), any()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = tools.saveApplication(
                "TXN-1", "usr_1", "Maybank", "HOME_LOAN", "NEW", "Conventional", "Purchase property", "YES", "2026-08-29"
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(loanApplicationRepository).saveApplication(eq("TXN-1"), eq("usr_1"), any());
    }

    @Test
    void saveApplicant_shouldCallRepository() {
        when(loanApplicationRepository.saveApplicant(eq("TXN-1"), eq("usr_1"), any()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = tools.saveApplicant(
                "TXN-1", "usr_1", "Primary", "Ahmad", "NRIC", "940822-10-5819", "Malaysian", "Malay",
                true, "MALE", "SINGLE", "1994-08-22", 0L, "Bachelor", "0123456789", null,
                "ahmad@test.com", "123 St", "50000", "KL", "KL", null, null, "EMPLOYED",
                "Tech Sdn Bhd", "IT", "Engineer", "Senior", 3.5, 10000.0, 120000.0,
                null, null, null, null, null, null, null, null
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(loanApplicationRepository).saveApplicant(eq("TXN-1"), eq("usr_1"), any());
    }

    @Test
    void saveProperty_shouldCallRepository() {
        when(loanApplicationRepository.saveProperty(eq("TXN-1"), eq("PROP-1"), any()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = tools.saveProperty(
                "TXN-1", "PROP-1", "Condominium", "Completed", "Dev Corp", "Sky Heights", "Builder Co",
                500000.0, 520000.0, 30000.0, "Unit 10-2, Sky Heights", "50000", "KL", "KL",
                "STRATA-123", "Strata Title", "Lot 100", "Mukim Batu", "Kuala Lumpur", true, true
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(loanApplicationRepository).saveProperty(eq("TXN-1"), eq("PROP-1"), any());
    }

    @Test
    void saveDocument_shouldCallRepository() {
        when(applicationDocumentRepository.save(
                eq("TXN-1"), eq("DOC-1"), eq("file.pdf"), eq("gs://bucket/doc.pdf"),
                eq("application/pdf"), eq("SUCCESS"), eq("OK"), any()
        )).thenReturn(Mono.empty());

        Map<String, Object> result = tools.saveDocument(
                "TXN-1", "DOC-1", "file.pdf", "gs://bucket/doc.pdf",
                "application/pdf", "SUCCESS", "OK", "{}"
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(applicationDocumentRepository).save(
                eq("TXN-1"), eq("DOC-1"), eq("file.pdf"), eq("gs://bucket/doc.pdf"),
                eq("application/pdf"), eq("SUCCESS"), eq("OK"), any()
        );
    }
}
