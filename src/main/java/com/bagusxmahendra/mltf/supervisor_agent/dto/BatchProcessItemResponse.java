package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchProcessItemResponse {

    private String transactionId;
    private String userId;
    private String previousStatus;
    private String finalStatus;
    private String message;
    private String caseId;
    private List<BatchDocumentItemResponse> documents;
    private GraphAnalysisResult graphAnalysis;

    public BatchProcessItemResponse() {
    }

    public BatchProcessItemResponse(
            String transactionId,
            String userId,
            String previousStatus,
            String finalStatus,
            String message,
            String caseId,
            List<BatchDocumentItemResponse> documents
    ) {
        this(transactionId, userId, previousStatus, finalStatus, message, caseId, documents, null);
    }

    public BatchProcessItemResponse(
            String transactionId,
            String userId,
            String previousStatus,
            String finalStatus,
            String message,
            String caseId,
            List<BatchDocumentItemResponse> documents,
            GraphAnalysisResult graphAnalysis
    ) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.previousStatus = previousStatus;
        this.finalStatus = finalStatus;
        this.message = message;
        this.caseId = caseId;
        this.documents = documents;
        this.graphAnalysis = graphAnalysis;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public List<BatchDocumentItemResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<BatchDocumentItemResponse> documents) {
        this.documents = documents;
    }

    public GraphAnalysisResult getGraphAnalysis() {
        return graphAnalysis;
    }

    public void setGraphAnalysis(GraphAnalysisResult graphAnalysis) {
        this.graphAnalysis = graphAnalysis;
    }
}
