package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SupervisorKycDecision;
import com.bagusxmahendra.mltf.supervisor_agent.prompt.SupervisorPromptProvider;
import com.bagusxmahendra.mltf.supervisor_agent.tools.KycSupervisorTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycSupervisorAgentServiceTest {

    @Mock
    private KycSupervisorTools supervisorTools;

    private SupervisorAgentProperties properties;
    private SupervisorPromptProvider promptProvider;
    private KycSupervisorAgentService service;

    @BeforeEach
    void setUp() {
        properties = new SupervisorAgentProperties();
        properties.setApprovedThreshold(85.0);
        properties.setRejectionThreshold(50.0);
        promptProvider = new SupervisorPromptProvider(new DefaultResourceLoader());
        service = new KycSupervisorAgentService(properties, promptProvider, supervisorTools);
    }

    @Test
    void evaluateProgrammatically_whenAllChecksPass_shouldDecideApproved() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("detectedDocumentType", "NATIONAL_ID");
        docResult.put("scores", Map.of("documentScore", 98.0));
        docResult.put("pixelLevelCheck", Map.of("isTampered", false));
        docResult.put("extractedFields", Map.of(
                "fullName", "AHMAD SYAZWAN BIN ABDULLAH",
                "idNumber", "940822-10-5819",
                "idType", "MyKad",
                "dateOfBirth", "1994-08-22",
                "nationality", "Malaysian"
        ));

        Map<String, Object> selfieResult = new LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 96.8);
        selfieResult.put("matchStatus", "MATCH");
        selfieResult.put("explanation", "Facial landmarks matched with authentic liveness.");

        Map<String, Object> externalResult = new LinkedHashMap<>();
        externalResult.put("status", "SUCCESS");
        externalResult.put("isIdentityVerified", true);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);

        StepVerifier.create(service.evaluateProgrammatically(
                "usr_1001",
                "AHMAD SYAZWAN BIN ABDULLAH",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "image/jpeg",
                "image/jpeg"
        ))
        .assertNext(decision -> {
            assertNotNull(decision);
            assertEquals("APPROVED", decision.getDecision());
            assertEquals(96.8, decision.getDecisionConfidence());
            assertEquals(5.0, decision.getRiskScore());
            assertEquals("LOW", decision.getRiskLevel());
            assertNull(decision.getRejectionReason());
            assertNotNull(decision.getExplanation());
            assertTrue(decision.getExplanation().contains("approved"));
            assertNotNull(decision.getExtractedProfile());
            assertEquals("940822-10-5819", decision.getExtractedProfile().getIdCardNumber());
        })
        .verifyComplete();
    }

    @Test
    void evaluateProgrammatically_whenConfidenceFallsShort_shouldDecideInReview() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("detectedDocumentType", "NATIONAL_ID");
        docResult.put("scores", Map.of("documentScore", 85.0));
        docResult.put("pixelLevelCheck", Map.of("isTampered", false));

        Map<String, Object> selfieResult = new LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 68.5); // Between 50.0 and 85.0
        selfieResult.put("matchStatus", "INCONCLUSIVE");

        Map<String, Object> externalResult = new LinkedHashMap<>();
        externalResult.put("status", "SUCCESS");
        externalResult.put("isIdentityVerified", true);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);
        when(supervisorTools.createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class)))
                .thenReturn(Map.of("status", "SUCCESS", "caseStatus", "IN_PROGRESS"));

        StepVerifier.create(service.evaluateProgrammatically(
                "usr_1001",
                "Applicant",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "image/jpeg",
                "image/jpeg"
        ))
        .assertNext(decision -> {
            assertNotNull(decision);
            assertEquals("IN_REVIEW", decision.getDecision());
            assertEquals(68.5, decision.getDecisionConfidence());
            assertEquals("MEDIUM", decision.getRiskLevel());
            assertTrue(decision.getExplanation().contains("manual"));
        })
        .verifyComplete();

        org.mockito.Mockito.verify(supervisorTools).createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class));
    }

    @Test
    void evaluateProgrammatically_whenDocumentTampered_shouldDecideRejected() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("pixelLevelCheck", Map.of("isTampered", true, "tamperingRiskLevel", "HIGH"));
        docResult.put("scores", Map.of("documentScore", 30.0));

        Map<String, Object> selfieResult = new LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 90.0);
        selfieResult.put("matchStatus", "MATCH");

        Map<String, Object> externalResult = new LinkedHashMap<>();
        externalResult.put("status", "SUCCESS");
        externalResult.put("isIdentityVerified", true);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);

        StepVerifier.create(service.evaluateProgrammatically(
                "usr_1001",
                "Applicant",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "image/jpeg",
                "image/jpeg"
        ))
        .assertNext(decision -> {
            assertNotNull(decision);
            assertEquals("REJECTED", decision.getDecision());
            assertEquals("CRITICAL", decision.getRiskLevel());
            assertNotNull(decision.getRejectionReason());
            assertTrue(decision.getRejectionReason().contains("tampering"));
        })
        .verifyComplete();
    }

    @Test
    void evaluateProgrammatically_whenExternalKycNameMismatch_shouldDecideInReview() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("detectedDocumentType", "NATIONAL_ID");
        docResult.put("scores", Map.of("documentScore", 95.0));
        docResult.put("pixelLevelCheck", Map.of("isTampered", false));

        Map<String, Object> selfieResult = new LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 96.0);
        selfieResult.put("matchStatus", "MATCH");

        Map<String, Object> externalResult = new LinkedHashMap<>();
        externalResult.put("status", "IN_REVIEW");
        externalResult.put("registryStatus", "NAME_MISMATCH");
        externalResult.put("isIdentityVerified", false);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");
        externalResult.put("message", "External KYC name mismatch: name in registry does not match inquiry name.");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);
        when(supervisorTools.createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class)))
                .thenReturn(Map.of("status", "SUCCESS", "caseStatus", "IN_PROGRESS"));

        StepVerifier.create(service.evaluateProgrammatically(
                "usr_1001",
                "Wrong Name",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "image/jpeg",
                "image/jpeg"
        ))
        .assertNext(decision -> {
            assertNotNull(decision);
            assertEquals("IN_REVIEW", decision.getDecision());
            assertEquals("MEDIUM", decision.getRiskLevel());
            assertTrue(decision.getExplanation().contains("External KYC status is IN_REVIEW"));
        })
        .verifyComplete();

        org.mockito.Mockito.verify(supervisorTools).createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class));
    }

    @Test
    void evaluateProgrammatically_whenExternalKycIdNotFound_shouldDecideInReview() {
        Map<String, Object> docResult = new LinkedHashMap<>();
        docResult.put("status", "SUCCESS");
        docResult.put("detectedDocumentType", "NATIONAL_ID");
        docResult.put("scores", Map.of("documentScore", 95.0));
        docResult.put("pixelLevelCheck", Map.of("isTampered", false));

        Map<String, Object> selfieResult = new LinkedHashMap<>();
        selfieResult.put("status", "SUCCESS");
        selfieResult.put("isIdentical", true);
        selfieResult.put("confidenceScore", 96.0);
        selfieResult.put("matchStatus", "MATCH");

        Map<String, Object> externalResult = new LinkedHashMap<>();
        externalResult.put("status", "IN_REVIEW");
        externalResult.put("registryStatus", "NOT_FOUND");
        externalResult.put("isIdentityVerified", false);
        externalResult.put("isBlacklisted", false);
        externalResult.put("amlSanctionsStatus", "PASS");
        externalResult.put("message", "ID Number not found in external national registry.");

        when(supervisorTools.validateDocument(any(), any(), any())).thenReturn(docResult);
        when(supervisorTools.validateSelfie(any(), any(), any(), any(), any())).thenReturn(selfieResult);
        when(supervisorTools.getExternalKycData(any(), any(), any(), any())).thenReturn(externalResult);
        when(supervisorTools.createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class)))
                .thenReturn(Map.of("status", "SUCCESS", "caseStatus", "IN_PROGRESS"));

        StepVerifier.create(service.evaluateProgrammatically(
                "usr_1001",
                "Some Name",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "image/jpeg",
                "image/jpeg"
        ))
        .assertNext(decision -> {
            assertNotNull(decision);
            assertEquals("IN_REVIEW", decision.getDecision());
            assertEquals("MEDIUM", decision.getRiskLevel());
            assertTrue(decision.getExplanation().contains("NOT_FOUND"));
        })
        .verifyComplete();

        org.mockito.Mockito.verify(supervisorTools).createCase(any(com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest.class));
    }
}
