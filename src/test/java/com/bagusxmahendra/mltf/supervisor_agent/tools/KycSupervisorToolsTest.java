package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.CaseManagementClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.SelfieValidationClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CaseResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.CreateCaseRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SelfieValidationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycSupervisorToolsTest {

    @Mock
    private DocumentProcessingClient documentProcessingClient;

    @Mock
    private SelfieValidationClient selfieValidationClient;

    @Mock
    private ExternalKycClient externalKycClient;

    @Mock
    private CaseManagementClient caseManagementClient;

    private KycSupervisorTools tools;

    @BeforeEach
    void setUp() {
        tools = new KycSupervisorTools(documentProcessingClient, selfieValidationClient, externalKycClient, caseManagementClient);
    }

    @Test
    void validateDocument_shouldReturnMapOfResponse() {
        DocProcessingResponseDto responseDto = new DocProcessingResponseDto();
        responseDto.setStatus("SUCCESS");
        responseDto.setDetectedDocumentType("NATIONAL_ID");
        responseDto.setScores(Map.of("documentScore", 98.4));
        responseDto.setPixelLevelCheck(Map.of("isTampered", false));

        when(documentProcessingClient.processDocument(eq("gs://mltf-bucket/id.jpg"), eq("image/jpeg"), any()))
                .thenReturn(Mono.just(responseDto));

        Map<String, Object> result = tools.validateDocument("gs://mltf-bucket/id.jpg", "image/jpeg", null);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("NATIONAL_ID", result.get("detectedDocumentType"));
    }

    @Test
    void validateSelfie_shouldReturnMapOfResponse() {
        SelfieValidationResponseDto responseDto = new SelfieValidationResponseDto();
        responseDto.setStatus("SUCCESS");
        responseDto.setIsIdentical(true);
        responseDto.setConfidenceScore(96.8);
        responseDto.setMatchStatus("MATCH");
        responseDto.setExplanation("Facial landmarks match perfectly.");

        when(selfieValidationClient.validateSelfie(eq("gs://bucket/id.jpg"), eq("gs://bucket/selfie.jpg"), any(), any(), any()))
                .thenReturn(Mono.just(responseDto));

        Map<String, Object> result = tools.validateSelfie("gs://bucket/id.jpg", "gs://bucket/selfie.jpg", "image/jpeg", "image/jpeg", null);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(true, result.get("isIdentical"));
        assertEquals(96.8, result.get("confidenceScore"));
        assertEquals("MATCH", result.get("matchStatus"));
    }

    @Test
    void getExternalKycData_shouldReturnMapOfResponse() {
        ExternalKycResponse response = ExternalKycResponse.verified("940822-10-5819", "BUEDI GUNAWAN", "1994-08-22", "Malaysian");

        when(externalKycClient.fetchExternalKycData(eq("940822-10-5819"), eq("BUEDI GUNAWAN"), any(), any()))
                .thenReturn(Mono.just(response));

        Map<String, Object> result = tools.getExternalKycData("940822-10-5819", "BUEDI GUNAWAN", "1994-08-22", "Malaysian");

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(true, result.get("isIdentityVerified"));
        assertEquals(false, result.get("isBlacklisted"));
        assertEquals("PASS", result.get("amlSanctionsStatus"));
    }

    @Test
    void getExternalKycData_whenNameMismatch_shouldReturnInReviewMap() {
        ExternalKycResponse response = ExternalKycResponse.nameMismatch("940822-10-5819", "WRONG NAME", "BUEDI GUNAWAN");

        when(externalKycClient.fetchExternalKycData(eq("940822-10-5819"), eq("WRONG NAME"), any(), any()))
                .thenReturn(Mono.just(response));

        Map<String, Object> result = tools.getExternalKycData("940822-10-5819", "WRONG NAME", "1994-08-22", "Malaysian");

        assertNotNull(result);
        assertEquals("IN_REVIEW", result.get("status"));
        assertEquals("NAME_MISMATCH", result.get("registryStatus"));
        assertEquals(false, result.get("isIdentityVerified"));
    }

    @Test
    void getExternalKycData_whenIdNotFound_shouldReturnInReviewMap() {
        ExternalKycResponse response = ExternalKycResponse.notFound("UNKNOWN-ID", "ANY NAME");

        when(externalKycClient.fetchExternalKycData(eq("UNKNOWN-ID"), eq("ANY NAME"), any(), any()))
                .thenReturn(Mono.just(response));

        Map<String, Object> result = tools.getExternalKycData("UNKNOWN-ID", "ANY NAME", "1994-08-22", "Malaysian");

        assertNotNull(result);
        assertEquals("IN_REVIEW", result.get("status"));
        assertEquals("NOT_FOUND", result.get("registryStatus"));
        assertEquals(false, result.get("isIdentityVerified"));
    }

    @Test
    void createCase_shouldCallCaseManagementClientAndReturnSuccessMap() {
        Instant now = Instant.now();
        CaseResponse response = new CaseResponse(
                "CASE-123",
                "usr_1001",
                "KYC",
                "IN_PROGRESS",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                null,
                null,
                null,
                45.0,
                "MEDIUM",
                null,
                "Requires human review",
                null,
                now,
                now
        );

        when(caseManagementClient.createCase(any(CreateCaseRequest.class)))
                .thenReturn(Mono.just(response));

        Map<String, Object> result = tools.createCase(
                "usr_1001",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                "Requires human review",
                45.0,
                "MEDIUM"
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("CASE-123", result.get("caseId"));
        assertEquals("IN_PROGRESS", result.get("caseStatus"));

        ArgumentCaptor<CreateCaseRequest> captor = ArgumentCaptor.forClass(CreateCaseRequest.class);
        verify(caseManagementClient).createCase(captor.capture());
        CreateCaseRequest sentReq = captor.getValue();
        assertEquals("usr_1001", sentReq.getUserId());
        assertNull(sentReq.getAssignedTo());
        assertEquals("IN_PROGRESS", sentReq.getCaseStatus());
        assertEquals("KYC", sentReq.getCaseType());
    }

    @Test
    void createCase_withFullDto_shouldCallCaseManagementClient() {
        Instant now = Instant.now();
        CaseResponse response = new CaseResponse(
                "CASE-456",
                "usr_1002",
                "KYC",
                "IN_PROGRESS",
                "gs://bucket/id.jpg",
                "gs://bucket/selfie.jpg",
                Map.of("score", 80.0),
                Map.of("match", "INCONCLUSIVE"),
                Map.of("status", "IN_REVIEW"),
                45.0,
                "MEDIUM",
                null,
                "Manual review needed",
                null,
                now,
                now
        );

        when(caseManagementClient.createCase(any(CreateCaseRequest.class)))
                .thenReturn(Mono.just(response));

        CreateCaseRequest req = new CreateCaseRequest();
        req.setUserId("usr_1002");
        req.setDocumentUrl("gs://bucket/id.jpg");
        req.setSelfieUrl("gs://bucket/selfie.jpg");
        req.setExternalKycDetails(Map.of("status", "SUCCESS", "isIdentityVerified", true));
        req.setRiskScore(45.0);
        req.setRiskLevel("MEDIUM");
        req.setRemarks("Manual review needed");

        Map<String, Object> result = tools.createCase(req);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("CASE-456", result.get("caseId"));
    }

    @Test
    void validateDocument_whenExceptionOccurs_shouldReturnFailedMap() {
        when(documentProcessingClient.processDocument(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        Map<String, Object> result = tools.validateDocument("gs://bucket/bad.jpg", "image/jpeg", null);

        assertNotNull(result);
        assertEquals("FAILED", result.get("status"));
        assertNotNull(result.get("error"));
    }
}
