package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class ApplicationDocumentService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final StorageService storageService;
    private final LoanApplicationAgentService loanApplicationAgentService;
    private final ApplicationDocumentRepository applicationDocumentRepository;

    public ApplicationDocumentService(
            LoanApplicationRepository loanApplicationRepository,
            StorageService storageService,
            LoanApplicationAgentService loanApplicationAgentService,
            ApplicationDocumentRepository applicationDocumentRepository
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.storageService = storageService;
        this.loanApplicationAgentService = loanApplicationAgentService;
        this.applicationDocumentRepository = applicationDocumentRepository;
    }

    public Mono<ApplicationDocumentResponse> uploadAndProcess(
            String applicationId,
            String userId,
            FilePart document
    ) {
        if (applicationId == null || applicationId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "applicationID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        if (document == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "document file is required"));
        }

        String sanitizedApplicationId = applicationId.trim();
        String sanitizedUserId = userId.trim();
        return loanApplicationRepository.existsByTransactionIdAndUserId(sanitizedApplicationId, sanitizedUserId)
                .flatMap(applicationExists -> {
                    if (!applicationExists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found for customer"
                        ));
                    }

                    String documentId = "DOC-" + UUID.randomUUID();
                    // 1. Upload the document to GCS
                    return storageService.uploadFile(document, sanitizedApplicationId, "document")
                            // 2. Handoff the process to the LoanApplicationAgent LLM Model
                            .flatMap(upload -> loanApplicationAgentService.processDocument(
                                    sanitizedApplicationId,
                                    sanitizedUserId,
                                    documentId,
                                    upload.filename(),
                                    upload.fileUrl(),
                                    upload.contentType()
                            ));
                });
    }

    public Mono<Void> deleteDocument(
            String applicationId,
            String userId,
            String documentId
    ) {
        if (applicationId == null || applicationId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "applicationID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        if (documentId == null || documentId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentID is required"));
        }

        String sanitizedApplicationId = applicationId.trim();
        String sanitizedUserId = userId.trim();
        String sanitizedDocumentId = documentId.trim();

        return loanApplicationRepository.existsByTransactionIdAndUserId(sanitizedApplicationId, sanitizedUserId)
                .flatMap(applicationExists -> {
                    if (!applicationExists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found for customer"
                        ));
                    }
                    return applicationDocumentRepository.delete(sanitizedApplicationId, sanitizedDocumentId);
                });
    }

    public Mono<Map<String, Object>> uploadAndStoreWithoutAnalysis(
            String applicationId,
            String userId,
            FilePart document
    ) {
        if (applicationId == null || applicationId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "applicationID is required"));
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID is required"));
        }
        if (document == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "document file is required"));
        }

        String sanitizedApplicationId = applicationId.trim();
        String sanitizedUserId = userId.trim();

        return loanApplicationRepository.existsByTransactionIdAndUserId(sanitizedApplicationId, sanitizedUserId)
                .flatMap(applicationExists -> {
                    if (!applicationExists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found for customer"
                        ));
                    }

                    String documentId = "DOC-" + UUID.randomUUID();
                    // 1. Upload the document to GCS
                    return storageService.uploadFile(document, sanitizedApplicationId, "document")
                            // 2. Save to database directly with null analytical details
                            .flatMap(upload -> applicationDocumentRepository.save(
                                    sanitizedApplicationId,
                                    documentId,
                                    upload.filename(),
                                    upload.fileUrl(),
                                    upload.contentType(),
                                    "SUCCESS",
                                    "Document uploaded successfully",
                                    null
                            ).thenReturn(Map.of("status", "success", "documentId", documentId)));
                });
    }
}
