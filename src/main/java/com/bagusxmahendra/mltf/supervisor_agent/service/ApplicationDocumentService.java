package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.client.DocumentProcessingClient;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.dto.DocProcessingResponseDto;
import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.repository.ApplicationDocumentRepository;
import com.bagusxmahendra.mltf.supervisor_agent.repository.LoanApplicationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ApplicationDocumentService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final StorageService storageService;
    private final DocumentProcessingClient documentProcessingClient;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ApplicationDocumentService(
            LoanApplicationRepository loanApplicationRepository,
            ApplicationDocumentRepository documentRepository,
            StorageService storageService,
            DocumentProcessingClient documentProcessingClient
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.documentProcessingClient = documentProcessingClient;
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
                    return storageService.uploadFile(document, sanitizedApplicationId, "document")
                            .flatMap(upload -> processAndSave(
                                    sanitizedApplicationId, documentId, upload));
                });
    }

    private Mono<ApplicationDocumentResponse> processAndSave(
            String applicationId,
            String documentId,
            FileUploadResult upload
    ) {
        return documentProcessingClient.processDocument(upload.fileUrl(), upload.contentType(), null)
                .flatMap(processingResult -> serialize(processingResult)
                        .flatMap(processingDetails -> documentRepository.save(
                                applicationId,
                                documentId,
                                upload.filename(),
                                upload.fileUrl(),
                                upload.contentType(),
                                processingResult.getStatus(),
                                processingResult.getMessage(),
                                processingDetails
                        ).thenReturn(new ApplicationDocumentResponse(
                                upload.filename(),
                                documentId,
                                processingResult.getStatus(),
                                processingResult.getMessage()
                        ))));
    }

    private Mono<String> serialize(DocProcessingResponseDto response) {
        try {
            return Mono.just(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException ex) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store document processing details",
                    ex
            ));
        }
    }
}