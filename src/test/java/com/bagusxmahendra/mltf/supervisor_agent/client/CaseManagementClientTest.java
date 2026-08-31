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
        properties.setCaseManagementUrl("http://localhost:59999");
        client = new CaseManagementClient(WebClient.builder(), properties);
    }

    @Test
    void createCase_whenServerUnavailable_shouldEmitError() {
        CreateCaseRequest request = new CreateCaseRequest();
        request.setUserId("usr_1001");
        request.setCaseType("LOAN_APPLICATION");
        request.setCaseStatus("IN_PROGRESS");
        request.setDocumentUrl("gs://mltf-bucket/session/id.jpg");
        request.setRemarks("Manual review required.");

        StepVerifier.create(client.createCase(request))
                .expectError()
                .verify();
    }

    @Test
    void createCase_withNullRequest_shouldReturnIllegalArgumentException() {
        StepVerifier.create(client.createCase(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
