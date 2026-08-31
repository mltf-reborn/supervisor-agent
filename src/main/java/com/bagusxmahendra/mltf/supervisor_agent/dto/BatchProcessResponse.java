package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchProcessResponse {

    private String status;
    private String message;
    private int totalProcessed;
    private List<BatchProcessItemResponse> results;
    private Instant timestamp;

    public BatchProcessResponse() {
    }

    public BatchProcessResponse(String status, String message, int totalProcessed, List<BatchProcessItemResponse> results, Instant timestamp) {
        this.status = status;
        this.message = message;
        this.totalProcessed = totalProcessed;
        this.results = results;
        this.timestamp = timestamp;
    }

    public static BatchProcessResponse noTransactionsFound() {
        return new BatchProcessResponse(
                "SUCCESS",
                "No transactions found for processing",
                0,
                Collections.emptyList(),
                Instant.now()
        );
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public List<BatchProcessItemResponse> getResults() {
        return results;
    }

    public void setResults(List<BatchProcessItemResponse> results) {
        this.results = results;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
