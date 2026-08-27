package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.client.ExternalKycClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycRequest;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ExternalKycResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Mock REST endpoint for external KYC identity, AML sanctions, and PEP validation.
 * Fulfills requirement: /api/v1/external/kyc mock API.
 */
@RestController
@RequestMapping("/api/v1/external/kyc")
public class ExternalKycController {

    private static final Logger log = LoggerFactory.getLogger(ExternalKycController.class);

    private final ExternalKycClient externalKycClient;

    public ExternalKycController(ExternalKycClient externalKycClient) {
        this.externalKycClient = externalKycClient;
    }

    /**
     * POST /api/v1/external/kyc
     */
    @PostMapping(
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ExternalKycResponse> verifyExternalKycPost(@RequestBody(required = false) ExternalKycRequest request) {
        String idNumber = request != null ? request.getIdNumber() : null;
        String fullName = request != null ? request.getFullName() : null;
        String dob = request != null ? request.getDateOfBirth() : null;
        String nationality = request != null ? request.getNationality() : null;

        log.info("Received POST /api/v1/external/kyc for ID: {}, Name: {}", idNumber, fullName);
        return Mono.just(externalKycClient.generateMockKycData(idNumber, fullName, dob, nationality));
    }

    /**
     * GET /api/v1/external/kyc
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ExternalKycResponse> verifyExternalKycGet(
            @RequestParam(name = "idNumber", required = false) String idNumber,
            @RequestParam(name = "id_number", required = false) String idNumberSnake,
            @RequestParam(name = "fullName", required = false) String fullName,
            @RequestParam(name = "full_name", required = false) String fullNameSnake,
            @RequestParam(name = "dateOfBirth", required = false) String dateOfBirth,
            @RequestParam(name = "dob", required = false) String dob,
            @RequestParam(name = "nationality", required = false) String nationality
    ) {
        String effectiveId = idNumber != null ? idNumber : idNumberSnake;
        String effectiveName = fullName != null ? fullName : fullNameSnake;
        String effectiveDob = dateOfBirth != null ? dateOfBirth : dob;

        log.info("Received GET /api/v1/external/kyc for ID: {}, Name: {}", effectiveId, effectiveName);
        return Mono.just(externalKycClient.generateMockKycData(effectiveId, effectiveName, effectiveDob, nationality));
    }
}
