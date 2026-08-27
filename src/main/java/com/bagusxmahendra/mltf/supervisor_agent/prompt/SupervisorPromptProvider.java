package com.bagusxmahendra.mltf.supervisor_agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Provider that loads and formats supervisor orchestration prompts.
 */
@Component
public class SupervisorPromptProvider {

    private static final Logger log = LoggerFactory.getLogger(SupervisorPromptProvider.class);

    private static final String DEFAULT_PROMPT_PATH = "classpath:prompts/supervisor-system-prompt.txt";

    private final ResourceLoader resourceLoader;
    private String cachedSystemPrompt;

    public SupervisorPromptProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public synchronized String getSystemPrompt() {
        if (cachedSystemPrompt != null && !cachedSystemPrompt.isBlank()) {
            return cachedSystemPrompt;
        }

        try {
            Resource resource = resourceLoader.getResource(DEFAULT_PROMPT_PATH);
            try (InputStream is = resource.getInputStream()) {
                cachedSystemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return cachedSystemPrompt;
            }
        } catch (Exception e) {
            log.warn("Could not load prompt file [{}]: {}, using embedded fallback prompt", DEFAULT_PROMPT_PATH, e.getMessage());
            cachedSystemPrompt = getEmbeddedSystemPrompt();
            return cachedSystemPrompt;
        }
    }

    public String buildUserPrompt(String userId, String fullName, String documentGcsUrl, String selfieGcsUrl, String docMimeType, String selfieMimeType) {
        return """
                Please conduct a comprehensive, diligent KYC verification review for customer [%s] (userId: %s).
                
                Input files provided:
                - Identity Document GCS URL: %s (mimeType: %s)
                - Webcam Selfie GCS URL: %s (mimeType: %s)
                
                Please execute the KYC orchestration workflow:
                1. Call tool `validateDocument` with the document GCS URL to inspect pixel integrity, tampering, authenticity scores, and extract full identity fields.
                2. Call tool `validateSelfie` with the ID document GCS URL and Selfie GCS URL to verify biometric facial match, confidence score, and liveness anti-spoofing.
                3. Call tool `getExternalKycData` with the extracted ID card number and full name to check national identity registry, AML/CFT sanctions, PEP status, and blacklists.
                4. Carefully analyze and review all findings. If the verification falls into `IN_REVIEW` status, call tool `createCase` to create a case in the Case Management Service (/api/v1/case) for human review.
                5. Synthesize an explainable decision (APPROVED, IN_REVIEW, or REJECTED) with confidence ratings, risk scores, and detailed reasoning.
                
                Return the final synthesized decision strictly in the requested JSON structure.
                """.formatted(
                fullName != null && !fullName.isBlank() ? fullName : "Customer",
                userId != null ? userId : "N/A",
                documentGcsUrl,
                docMimeType != null ? docMimeType : "image/jpeg",
                selfieGcsUrl,
                selfieMimeType != null ? selfieMimeType : "image/jpeg"
        );
    }

    private String getEmbeddedSystemPrompt() {
        return """
                You are the Senior KYC Supervisor Agent orchestrating KYC verification using Google ADK.
                Use the tools validateDocument, validateSelfie, and getExternalKycData to conduct diligent KYC checks.
                If KYC status is IN_REVIEW, use the createCase tool to create a case for human review.
                Synthesize findings and decide APPROVED, IN_REVIEW, or REJECTED with explainable reasoning in valid JSON.
                """;
    }
}
