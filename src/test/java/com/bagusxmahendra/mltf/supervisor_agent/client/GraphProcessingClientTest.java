package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DynamicDocumentData;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GraphAnalysisRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphProcessingClientTest {

    private SupervisorAgentProperties properties;
    private GraphProcessingClient client;

    @BeforeEach
    void setUp() {
        properties = new SupervisorAgentProperties();
        properties.setGraphProcessingUrl("http://localhost:59997");
        properties.setTimeoutSeconds(2);
        client = new GraphProcessingClient(WebClient.builder(), properties);
    }

    @Test
    void analyzeGraph_whenServerUnavailable_shouldReturnFlaggedFallback() {
        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-1001", "applicantName", "Diana Prince"),
                List.of(new DynamicDocumentData("PAYSLIP", Map.of("netSalary", 14147.65)))
        );

        StepVerifier.create(client.analyzeGraph(request))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("FLAGGED", response.status());
                    assertEquals("SALARY_TRIANGULATION", response.checkName());
                    assertFalse(response.passed());
                    assertFalse(response.discrepancies().isEmpty());
                    assertTrue(response.discrepancies().get(0).contains("Failed to reach Graph Processing Agent"));
                })
                .verifyComplete();
    }
}
