package com.bagusxmahendra.mltf.supervisor_agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Provider that loads and formats prompt templates for the LoanApplicationAgent.
 */
@Component
public class LoanApplicationPromptProvider {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationPromptProvider.class);
    private static final String DEFAULT_PROMPT_PATH = "classpath:prompts/loan-application-system-prompt.txt";

    private final ResourceLoader resourceLoader;
    private String cachedSystemPrompt;

    public LoanApplicationPromptProvider(ResourceLoader resourceLoader) {
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

    public String buildUserPrompt(
            String applicationId,
            String userId,
            String documentId,
            String filename,
            String gcsUrl,
            String contentType
    ) {
        return """
                Please process the following loan application document:
                - Application ID: %s
                - User ID: %s
                - Document ID: %s
                - Filename: %s
                - GCS URL: %s
                - Content Type: %s
                
                Workflow:
                1. Validate the document at the GCS URL using `validateDocument`.
                2. If valid, extract applicant, application, and property fields STRICTLY from the document extraction tool result.
                   CRITICAL ANTI-HALLUCINATION RULE: Strictly ONLY use values explicitly returned in the `validateDocument` tool response. Do NOT hallucinate, infer, guess, or invent any non-existent values. When a value cannot be found in the document, you MUST send NULL or an empty string "" (or omit it); do NOT put any other value or placeholder.
                3. Verify data integrity against existing records using `checkDataSimilarity`.
                4. If `checkDataSimilarity` detects conflicts (status: CONFLICT_DETECTED), do NOT save table records; save the document as "FAILED" using `saveDocument` and return documentStatus "FAILED" with the conflict message.
                5. If `checkDataSimilarity` passes, save extracted fields using `saveApplication`, `saveApplicant`, and `saveProperty`.
                6. Save the document record in the database using `saveDocument`.
                7. Return the structured JSON outcome.
                """.formatted(
                applicationId,
                userId,
                documentId,
                filename,
                gcsUrl,
                contentType != null ? contentType : "application/pdf"
        );
    }

    private String getEmbeddedSystemPrompt() {
        return """
                You are the LoanApplicationAgent built with Google ADK.
                Your task is to validate loan application documents using validateDocument,
                strictly extract only fields explicitly present in the validateDocument tool output without hallucinating or inventing any non-existent values (when a field cannot be found, use NULL or empty string ""),
                verify data similarity against existing records using checkDataSimilarity,
                save extracted fields to application, applicant, and property tables using saveApplication, saveApplicant, and saveProperty,
                and persist document metadata to the document table using saveDocument.
                Return the final status as clean JSON.
                """;
    }
}
