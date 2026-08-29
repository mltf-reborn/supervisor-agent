package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.prompt.LoanApplicationPromptProvider;
import com.bagusxmahendra.mltf.supervisor_agent.tools.LoanApplicationTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationAgentServiceTest {

    @Mock
    private LoanApplicationTools loanApplicationTools;

    private SupervisorAgentProperties properties;
    private LoanApplicationPromptProvider promptProvider;
    private LoanApplicationAgentService service;

    @BeforeEach
    void setUp() {
        properties = new SupervisorAgentProperties();
        promptProvider = new LoanApplicationPromptProvider(new DefaultResourceLoader());
        service = new LoanApplicationAgentService(properties, promptProvider, loanApplicationTools);
    }

    @Test
    void processProgrammatically_whenValidDocument_shouldSaveDataAndDocument() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("message", "Processed successfully");
        docResult.put("detectedDocumentType", "PAYSLIP");
        docResult.put("extractedFields", Map.of(
                "race", "Malay",
                "nationality", "Malaysian",
                "monthly_gross_rm", 10000.00
        ));

        when(loanApplicationTools.validateDocument(eq("gs://bucket/doc.pdf"), eq("application/pdf"), any()))
                .thenReturn(docResult);
        when(loanApplicationTools.checkDataSimilarity(eq("TXN-1"), any(), any(), any()))
                .thenReturn(Map.of("status", "PASSED", "hasConflict", false));
        when(loanApplicationTools.saveApplicant(eq("TXN-1"), eq("usr_1"), any()))
                .thenReturn(Map.of("status", "SUCCESS"));
        when(loanApplicationTools.saveDocument(eq("TXN-1"), eq("DOC-1"), eq("doc.pdf"), eq("gs://bucket/doc.pdf"), eq("application/pdf"), eq("SUCCESS"), eq("Processed successfully"), any()))
                .thenReturn(Map.of("status", "SUCCESS"));

        StepVerifier.create(service.processProgrammatically(
                "TXN-1",
                "usr_1",
                "DOC-1",
                "doc.pdf",
                "gs://bucket/doc.pdf",
                "application/pdf"
        ))
        .assertNext(res -> {
            assertNotNull(res);
            assertEquals("doc.pdf", res.documentFilename());
            assertEquals("DOC-1", res.documentId());
            assertEquals("SUCCESS", res.documentStatus());
            assertEquals("Processed successfully", res.documentMessage());
        })
        .verifyComplete();

        verify(loanApplicationTools).checkDataSimilarity(eq("TXN-1"), any(), any(), any());
        verify(loanApplicationTools).saveApplicant(eq("TXN-1"), eq("usr_1"), any());
        verify(loanApplicationTools).saveDocument(eq("TXN-1"), eq("DOC-1"), eq("doc.pdf"), eq("gs://bucket/doc.pdf"), eq("application/pdf"), eq("SUCCESS"), eq("Processed successfully"), any());
    }

    @Test
    void processProgrammatically_whenConflictDetected_shouldFailAndNotSaveData() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("message", "Processed successfully");
        docResult.put("detectedDocumentType", "PAYSLIP");
        docResult.put("extractedFields", Map.of(
                "full_name", "Bagus Mahendra Wicaksono"
        ));

        when(loanApplicationTools.validateDocument(eq("gs://bucket/doc.pdf"), eq("application/pdf"), any()))
                .thenReturn(docResult);
        when(loanApplicationTools.checkDataSimilarity(eq("TXN-1"), any(), any(), any()))
                .thenReturn(Map.of(
                        "status", "CONFLICT_DETECTED",
                        "hasConflict", true,
                        "message", "Conflicting data for applicant field 'full_name': existing value 'John Doe' vs incoming value 'Bagus Mahendra Wicaksono' (similarity 18.2% is below threshold 80.0%)"
                ));
        when(loanApplicationTools.saveDocument(eq("TXN-1"), eq("DOC-1"), eq("doc.pdf"), eq("gs://bucket/doc.pdf"), eq("application/pdf"), eq("FAILED"), any(), any()))
                .thenReturn(Map.of("status", "SUCCESS"));

        StepVerifier.create(service.processProgrammatically(
                "TXN-1",
                "usr_1",
                "DOC-1",
                "doc.pdf",
                "gs://bucket/doc.pdf",
                "application/pdf"
        ))
        .assertNext(res -> {
            assertNotNull(res);
            assertEquals("doc.pdf", res.documentFilename());
            assertEquals("DOC-1", res.documentId());
            assertEquals("FAILED", res.documentStatus());
            org.junit.jupiter.api.Assertions.assertTrue(res.documentMessage().contains("Conflicting data"));
        })
        .verifyComplete();

        verify(loanApplicationTools).checkDataSimilarity(eq("TXN-1"), any(), any(), any());
        org.mockito.Mockito.verify(loanApplicationTools, org.mockito.Mockito.never()).saveApplicant(any(), any(), any());
        verify(loanApplicationTools).saveDocument(eq("TXN-1"), eq("DOC-1"), eq("doc.pdf"), eq("gs://bucket/doc.pdf"), eq("application/pdf"), eq("FAILED"), any(), any());
    }

    @Test
    void isFieldIgnored_shouldCentralizeAndIgnoreStandardMetadataFields() {
        // Status and lifecycle
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("status"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("application_status"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("applicationStatus"));

        // Dates and timestamps
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("application_date"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("applicationDate"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("created_at"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("createdAt"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("updated_at"));

        // Primary / Foreign Keys
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("transaction_id"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("user_id"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("applicant_id"));
        org.junit.jupiter.api.Assertions.assertTrue(properties.isFieldIgnored("property_id"));

        // Non-ignored substantive data fields
        org.junit.jupiter.api.Assertions.assertFalse(properties.isFieldIgnored("full_name"));
        org.junit.jupiter.api.Assertions.assertFalse(properties.isFieldIgnored("id_no"));
        org.junit.jupiter.api.Assertions.assertFalse(properties.isFieldIgnored("monthly_gross_rm"));
        org.junit.jupiter.api.Assertions.assertFalse(properties.isFieldIgnored("spa_price_rm"));
    }
}
