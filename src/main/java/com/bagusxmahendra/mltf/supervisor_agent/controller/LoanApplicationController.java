package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.LoanApplicationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/v1/application")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final Auth0JwtService auth0JwtService;

    public LoanApplicationController(
            LoanApplicationService loanApplicationService,
            Auth0JwtService auth0JwtService
    ) {
        this.loanApplicationService = loanApplicationService;
        this.auth0JwtService = auth0JwtService;
    }

    @PostMapping
    public Mono<LoanApplicationResponse> create(
            @RequestParam String action,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (!"create".equalsIgnoreCase(action)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported action: " + action
            ));
        }
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.createMortgageLoan(auth0JwtService.extractUserId(authHeader));
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

    @org.springframework.web.bind.annotation.GetMapping
    public Mono<List<ApplicationSummaryResponse>> getApplications(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.getApplications(auth0JwtService.extractUserId(authHeader));
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

    @DeleteMapping
    public Mono<Void> delete(
            @RequestParam String applicationReferenceNumber,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.deleteApplication(
                    applicationReferenceNumber,
                    auth0JwtService.extractUserId(authHeader)
            );
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
}