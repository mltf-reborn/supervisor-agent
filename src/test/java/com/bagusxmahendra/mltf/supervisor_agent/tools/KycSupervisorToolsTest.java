package com.bagusxmahendra.mltf.supervisor_agent.tools;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.client.SelfieValidationClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.SelfieValidationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycSupervisorToolsTest {

    @Mock
    private DocumentProcessingClient documentProcessingClient;

    @Mock
    private SelfieValidationClient selfieValidationClient;

    @Mock
    private ExternalKycClient externalKycClient;

    private KycSupervisorTools tools;

    @BeforeEach
    void setUp() {
        tools = new KycSupervisorTools(documentProcessingClient, selfieValidationClient, externalKycClient);
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
        ExternalKycResponse response = ExternalKycResponse.verified("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian");

        when(externalKycClient.fetchExternalKycData(eq("940822-10-5819"), eq("AHMAD SYAZWAN"), any(), any()))
                .thenReturn(Mono.just(response));

        Map<String, Object> result = tools.getExternalKycData("940822-10-5819", "AHMAD SYAZWAN", "1994-08-22", "Malaysian");

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(true, result.get("isIdentityVerified"));
        assertEquals(false, result.get("isBlacklisted"));
        assertEquals("PASS", result.get("amlSanctionsStatus"));
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
