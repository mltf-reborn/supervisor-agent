package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.dto.BatchProcessResponse;
import com.bagusxmahendra.mltf.supervisor_agent.service.BatchProcessingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/batch")
public class BatchProcessingController {

    private final BatchProcessingService batchProcessingService;

    public BatchProcessingController(BatchProcessingService batchProcessingService) {
        this.batchProcessingService = batchProcessingService;
    }

    /**
     * Triggered by a timer scheduler (or HTTP POST request) to process all loan applications
     * currently in SUBMITTED status and perform automated document forensics / verification.
     *
     * @return Mono of BatchProcessResponse detailing processed transactions and their final status.
     */
    @PostMapping("/process")
    public Mono<BatchProcessResponse> processBatch() {
        return batchProcessingService.processSubmittedApplications();
    }

    /**
     * GET endpoint fallback for timer schedulers invoking via GET.
     */
    @GetMapping("/process")
    public Mono<BatchProcessResponse> processBatchGet() {
        return batchProcessingService.processSubmittedApplications();
    }
}
