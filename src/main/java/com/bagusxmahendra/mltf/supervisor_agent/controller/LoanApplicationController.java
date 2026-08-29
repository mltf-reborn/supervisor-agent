package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.LoanApplicationResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationInquiryResponse;
import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.ApplicationDocumentService;
import com.bagusxmahendra.mltf.supervisor_agent.service.LoanApplicationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/application")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final Auth0JwtService auth0JwtService;
    private final ApplicationDocumentService applicationDocumentService;

    public LoanApplicationController(
            LoanApplicationService loanApplicationService,
            Auth0JwtService auth0JwtService,
            ApplicationDocumentService applicationDocumentService
    ) {
        this.loanApplicationService = loanApplicationService;
        this.auth0JwtService = auth0JwtService;
        this.applicationDocumentService = applicationDocumentService;
    }

    @PostMapping(value = "/document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApplicationDocumentResponse> uploadDocument(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestPart("document") org.springframework.http.codec.multipart.FilePart document
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return applicationDocumentService.uploadAndProcess(
                    applicationId,
                    auth0JwtService.extractUserId(authHeader),
                    document
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

    @org.springframework.web.bind.annotation.GetMapping("/edit")
    public Mono<ApplicationInquiryResponse> inquiry(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return inquiryWithAction("edit", applicationId, authHeader);
    }

    @org.springframework.web.bind.annotation.GetMapping("/status")
    public Mono<ApplicationInquiryResponse> getApplicationStatus(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return inquiryWithAction("status", applicationId, authHeader);
    }

    @org.springframework.web.bind.annotation.GetMapping("/documents")
    public Mono<ApplicationInquiryResponse> getApplicationDocuments(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return inquiryWithAction("documents", applicationId, authHeader);
    }

    @org.springframework.web.bind.annotation.GetMapping("/document/status")
    public Mono<ApplicationInquiryResponse> getDocumentStatus(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return inquiryWithAction("document_status", applicationId, authHeader);
    }

    @org.springframework.web.bind.annotation.GetMapping(params = "action")
    public Mono<ApplicationInquiryResponse> inquiryByAction(
            @RequestParam String action,
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (!"edit".equalsIgnoreCase(action) && !"status".equalsIgnoreCase(action) && !"inquiry".equalsIgnoreCase(action)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported action: " + action
            ));
        }
        return inquiryWithAction(action, applicationId, authHeader);
    }

    private Mono<ApplicationInquiryResponse> inquiryWithAction(
            String action,
            String applicationId,
            String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.getApplicationInquiry(
                    applicationId,
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

    @DeleteMapping("/document")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDocument(
            @RequestParam("applicationID") String applicationId,
            @RequestParam("documentID") String documentId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return applicationDocumentService.deleteDocument(
                    applicationId,
                    auth0JwtService.extractUserId(authHeader),
                    documentId
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

    @org.springframework.web.bind.annotation.GetMapping("/details")
    public Mono<Map<String, Object>> getDetails(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.getApplicationDetails(
                    applicationId,
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

    @PostMapping("/details")
    public Mono<Void> saveDetails(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            return loanApplicationService.saveApplicationDetails(
                    applicationId,
                    auth0JwtService.extractUserId(authHeader),
                    payload
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

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
