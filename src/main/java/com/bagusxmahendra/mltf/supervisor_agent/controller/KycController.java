package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.KycVerifyResponse;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.KycService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private static final Logger log = LoggerFactory.getLogger(KycController.class);

    private final KycService kycService;
    private final Auth0JwtService auth0JwtService;

    public KycController(KycService kycService, Auth0JwtService auth0JwtService) {
        this.kycService = kycService;
        this.auth0JwtService = auth0JwtService;
    }

    /**
     * Get KYC status for the authenticated user extracted from the Auth0 JWT token.
     * Example: GET /api/v1/kyc/status with Header 'Authorization: Bearer <Auth0_JWT>'
     */
    @GetMapping("/status")
    public Mono<KycStatusResponse> getStatus(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            String userId = auth0JwtService.extractUserId(authHeader);
            return kycService.getStatus(userId);
        } catch (ResponseStatusException ex) {
            return Mono.error(ex);
        } catch (Exception ex) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Failed to process authentication token: " + ex.getMessage(),
                    ex
            ));
        }
    }

    /**
     * Submit KYC documents for verification.
     *
     * <p>Accepts a {@code multipart/form-data} request containing:</p>
     * <ul>
     *   <li>{@code document} – identity document file (JPEG / PNG / PDF)</li>
     *   <li>{@code selfie}   – selfie photo captured from the webcam (JPEG)</li>
     *   <li>{@code fullName} – (optional) user's full name for pre-fill</li>
     * </ul>
     * The {@code Authorization: Bearer <JWT>} header is required and used to identify
     * the submitting user.
     *
     * <p>Returns a {@link KycVerifyResponse} strictly matching the sealed UI contract.</p>
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<KycVerifyResponse> verify(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestPart("document") FilePart document,
            @RequestPart("selfie") FilePart selfie,
            @RequestPart(value = "fullName", required = false) String fullName
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        String userId;
        String email;
        try {
            userId = auth0JwtService.extractUserId(authHeader);
            email = auth0JwtService.extractEmail(authHeader);
        } catch (ResponseStatusException ex) {
            return Mono.error(ex);
        } catch (Exception ex) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Failed to process authentication token: " + ex.getMessage(),
                    ex
            ));
        }

        if (document == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identity document file ('document') is required"
            ));
        }

        if (selfie == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selfie file ('selfie') is required"
            ));
        }

        log.info("KYC verify submission received – userId: {}, email: {}, document: {}, selfie: {}",
                userId, email, document.filename(), selfie.filename());

        return kycService.verify(userId, email, fullName, document, selfie);
    }
    /**
     * Webhook/Endpoint for Ops to update the KYC decision (Accept/Reject).
     */
    @PostMapping(value = "/case-decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<KycStatusResponse> updateKycDecision(
            @org.springframework.web.bind.annotation.RequestBody com.bagusxmahendra.mltf.supervisor_agent.dto.CaseDecisionDto request
    ) {
        if (request == null || request.getUserId() == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payload and userId are required"
            ));
        }

        log.info("Received ops decision for KYC case - userId: {}, status: {}", request.getUserId(), request.getStatus());
        return kycService.updateKycDecision(request.getUserId(), request.getStatus(), request.getRemarks(), request.getVerifiedBy());
    }
}
