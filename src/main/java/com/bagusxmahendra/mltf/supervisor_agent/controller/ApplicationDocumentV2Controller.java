package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.security.Auth0JwtService;
import com.bagusxmahendra.mltf.supervisor_agent.service.ApplicationDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/application")
public class ApplicationDocumentV2Controller {

    private final ApplicationDocumentService applicationDocumentService;
    private final Auth0JwtService auth0JwtService;

    public ApplicationDocumentV2Controller(
            ApplicationDocumentService applicationDocumentService,
            Auth0JwtService auth0JwtService
    ) {
        this.applicationDocumentService = applicationDocumentService;
        this.auth0JwtService = auth0JwtService;
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadDocument(
            @RequestParam("applicationID") String applicationId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestPart("document") FilePart document
    ) {
        if (authHeader == null || authHeader.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header is required"
            ));
        }

        try {
            String userId = auth0JwtService.extractUserId(authHeader);
            return applicationDocumentService.uploadAndStoreWithoutAnalysis(applicationId, userId, document);
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
