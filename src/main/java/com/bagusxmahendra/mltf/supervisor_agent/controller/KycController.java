package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.KycStatusResponse;
import com.bagusxmahendra.mltf.supervisor_agent.service.KycService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /**
     * Get KYC status by query parameter (userId or email).
     * Example: GET /api/v1/kyc/status?userId=usr_1001
     * Example: GET /api/v1/kyc/status?email=john.doe@example.com
     */
    @GetMapping("/status")
    public Mono<KycStatusResponse> getStatus(
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "email", required = false) String email
    ) {
        if (userId != null && !userId.isBlank()) {
            return kycService.getStatus(userId);
        }
        if (email != null && !email.isBlank()) {
            return kycService.getStatusByEmail(email);
        }
        return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Query parameter 'userId' or 'email' is required"
        ));
    }

    /**
     * Get KYC status by user ID path variable.
     * Example: GET /api/v1/kyc/status/usr_1001
     */
    @GetMapping("/status/{userId}")
    public Mono<KycStatusResponse> getStatusByUserIdPath(@PathVariable("userId") String userId) {
        return kycService.getStatus(userId);
    }
}
