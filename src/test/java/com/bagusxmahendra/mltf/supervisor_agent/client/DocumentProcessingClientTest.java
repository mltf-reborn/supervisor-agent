package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessingClientTest {

    private SupervisorAgentProperties properties;
    private DocumentProcessingClient client;

    @BeforeEach
    void setUp() {
        properties = new SupervisorAgentProperties();
        properties.setDocumentProcessingUrl("http://localhost:59998");
        properties.setTimeoutSeconds(2);
        client = new DocumentProcessingClient(WebClient.builder(), properties);
    }

    @Test
    void processDocument_whenServerUnavailable_shouldReturnInReviewFallback() {
        StepVerifier.create(client.processDocument("gs://bucket/sample.pdf", "application/pdf", null))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("IN_REVIEW", response.getStatus());
                    assertTrue(response.getMessage().contains("Failed to reach Document Processing Agent"));
                    assertEquals("gs://bucket/sample.pdf", response.getGcsUrl());
                    assertFalse(response.isTampered());
                })
                .verifyComplete();
    }
}
