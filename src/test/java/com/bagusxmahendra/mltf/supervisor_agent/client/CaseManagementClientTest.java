package com.bagusxmahendra.mltf.supervisor_agent.client;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CaseResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaseManagementClientTest {

    private SupervisorAgentProperties properties;
    private CaseManagementClient client;

    @BeforeEach
    void setUp() {
        properties = new SupervisorAgentProperties();
        properties.setCaseManagementUrl("http://localhost:8082");
        client = new CaseManagementClient(WebClient.builder(), properties);
    }

    @Test
    void createCase_whenServerUnavailable_shouldReturnResilientFallback() {
        CreateCaseRequest request = new CreateCaseRequest();
        request.setUserId("usr_1001");
        request.setCaseType("KYC");
        request.setCaseStatus("IN_PROGRESS");
        request.setDocumentUrl("gs://mltf-bucket/session/id.jpg");
        request.setSelfieUrl("gs://mltf-bucket/session/selfie.jpg");
        request.setRiskScore(45.0);
        request.setRiskLevel("MEDIUM");
        request.setRemarks("Biometric match inconclusive, manual review required.");
        request.setKycDetails(Map.of("status", "IN_REVIEW"));

        StepVerifier.create(client.createCase(request))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.caseId());
                    assertTrue(response.caseId().startsWith("CASE-KYC-"));
                    assertEquals("usr_1001", response.userId());
                    assertEquals("KYC", response.caseType());
                    assertEquals("IN_PROGRESS", response.caseStatus());
                    assertEquals("gs://mltf-bucket/session/id.jpg", response.documentUrl());
                    assertEquals("gs://mltf-bucket/session/selfie.jpg", response.selfieUrl());
                    assertEquals(45.0, response.riskScore());
                    assertEquals("MEDIUM", response.riskLevel());
                    assertNull(response.assignedTo());
                    assertNotNull(response.createdAt());
                })
                .verifyComplete();
    }

    @Test
    void createCase_withNullRequest_shouldReturnFallbackGracefully() {
        StepVerifier.create(client.createCase(null))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.caseId());
                    assertEquals("unknown", response.userId());
                    assertEquals("IN_PROGRESS", response.caseStatus());
                })
                .verifyComplete();
    }
}
