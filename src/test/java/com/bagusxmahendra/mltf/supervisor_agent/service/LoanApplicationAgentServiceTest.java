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

        verify(loanApplicationTools).saveApplicant(eq("TXN-1"), eq("usr_1"), any());
        verify(loanApplicationTools).saveDocument(eq("TXN-1"), eq("DOC-1"), eq("doc.pdf"), eq("gs://bucket/doc.pdf"), eq("application/pdf"), eq("SUCCESS"), eq("Processed successfully"), any());
    }
}
