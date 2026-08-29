package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Response payload received from /api/v1/external/kyc.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalKycResponse {

    private String status; // "SUCCESS", "FAILED", "SUSPICIOUS"
    private String message;

    @JsonProperty("idNumber")
    @JsonAlias({"id_number", "idCardNumber"})
    private String idNumber;

    @JsonProperty("fullName")
    @JsonAlias({"full_name", "name"})
    private String fullName;

    @JsonProperty("dateOfBirth")
    @JsonAlias({"date_of_birth", "dob"})
    private String dateOfBirth;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("occupation")
    private String occupation;

    @JsonProperty("monthlyIncome")
    @JsonAlias({"monthly_income", "income"})
    private java.math.BigDecimal monthlyIncome;

    @JsonProperty("registryStatus")
    @JsonAlias({"registry_status", "nationalIdStatus"})
    private String registryStatus; // "ACTIVE", "EXPIRED", "NOT_FOUND", "REVOKED"

    @JsonProperty("isIdentityVerified")
    @JsonAlias({"is_identity_verified", "verified"})
    private Boolean isIdentityVerified;

    @JsonProperty("isBlacklisted")
    @JsonAlias({"is_blacklisted", "blacklisted"})
    private Boolean isBlacklisted;

    @JsonProperty("amlSanctionsStatus")
    @JsonAlias({"aml_sanctions_status", "amlStatus", "sanctionsStatus"})
    private String amlSanctionsStatus; // "PASS", "POTENTIAL_MATCH", "HIT"

    @JsonProperty("pepStatus")
    @JsonAlias({"pep_status", "isPep"})
    private String pepStatus; // "NOT_PEP", "PEP_DOMESTIC", "PEP_FOREIGN"

    @JsonProperty("creditScore")
    @JsonAlias({"credit_score"})
    private Integer creditScore;

    @JsonProperty("riskLevel")
    @JsonAlias({"risk_level"})
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    @JsonProperty("riskScore")
    @JsonAlias({"risk_score"})
    private Double riskScore; // 0.0 - 100.0

    private String remarks;

    @JsonProperty("flags")
    private List<String> flags;

    @JsonProperty("checkedAt")
    private Instant checkedAt;

    public ExternalKycResponse() {
    }

    public static ExternalKycResponse verified(String idNumber, String fullName, String dateOfBirth, String nationality) {
        ExternalKycResponse res = new ExternalKycResponse();
        res.setStatus("SUCCESS");
        res.setMessage("External KYC database verification completed successfully.");
        res.setIdNumber(idNumber);
        res.setFullName(fullName);
        res.setDateOfBirth(dateOfBirth);
        res.setNationality(nationality != null ? nationality : "Malaysian");
        res.setOccupation("Software Engineer");
        res.setMonthlyIncome(new java.math.BigDecimal("8500.00"));
        res.setRegistryStatus("ACTIVE");
        res.setIsIdentityVerified(true);
        res.setIsBlacklisted(false);
        res.setAmlSanctionsStatus("PASS");
        res.setPepStatus("NOT_PEP");
        res.setCreditScore(780);
        res.setRiskLevel("LOW");
        res.setRiskScore(5.0);
        res.setRemarks("National identity verified against central registry. Clean AML/PEP record with no adverse flags.");
        res.setFlags(List.of());
        res.setCheckedAt(Instant.now());
        return res;
    }

    public static ExternalKycResponse suspicious(String idNumber, String fullName, String reason) {
        ExternalKycResponse res = new ExternalKycResponse();
        res.setStatus("SUSPICIOUS");
        res.setMessage("External KYC validation flagged potential risk: " + reason);
        res.setIdNumber(idNumber);
        res.setFullName(fullName);
        res.setRegistryStatus("FLAGGED");
        res.setIsIdentityVerified(false);
        res.setIsBlacklisted(true);
        res.setAmlSanctionsStatus("HIT");
        res.setPepStatus("PEP_DOMESTIC");
        res.setCreditScore(450);
        res.setRiskLevel("HIGH");
        res.setRiskScore(85.0);
        res.setRemarks("External check failed: " + reason);
        res.setFlags(List.of("AML_WATCHLIST_HIT", "SUSPICIOUS_IDENTITY_RECORD"));
        res.setCheckedAt(Instant.now());
        return res;
    }

    public static ExternalKycResponse inReview(String idNumber, String fullName, String reason, String registryStatus) {
        ExternalKycResponse res = new ExternalKycResponse();
        res.setStatus("IN_REVIEW");
        res.setMessage("External KYC validation requires review: " + reason);
        res.setIdNumber(idNumber);
        res.setFullName(fullName);
        res.setRegistryStatus(registryStatus != null ? registryStatus : "IN_REVIEW");
        res.setIsIdentityVerified(false);
        res.setIsBlacklisted(false);
        res.setAmlSanctionsStatus("PASS");
        res.setPepStatus("NOT_PEP");
        res.setCreditScore(650);
        res.setRiskLevel("MEDIUM");
        res.setRiskScore(45.0);
        res.setRemarks("External check requires review: " + reason);
        res.setFlags(List.of("MANUAL_REVIEW_REQUIRED"));
        res.setCheckedAt(Instant.now());
        return res;
    }

    public static ExternalKycResponse nameMismatch(String idNumber, String requestedName, String registryName) {
        ExternalKycResponse res = new ExternalKycResponse();
        res.setStatus("IN_REVIEW");
        res.setMessage(String.format("External KYC name mismatch: Provided name '%s' does not match registry record '%s'.",
                requestedName != null ? requestedName : "", registryName != null ? registryName : ""));
        res.setIdNumber(idNumber);
        res.setFullName(registryName != null ? registryName : requestedName);
        res.setRegistryStatus("NAME_MISMATCH");
        res.setIsIdentityVerified(false);
        res.setIsBlacklisted(false);
        res.setAmlSanctionsStatus("PASS");
        res.setPepStatus("NOT_PEP");
        res.setCreditScore(650);
        res.setRiskLevel("MEDIUM");
        res.setRiskScore(45.0);
        res.setRemarks(String.format("ID Number matched in registry, but name '%s' differs from official record '%s'. Status set to IN_REVIEW.",
                requestedName != null ? requestedName : "", registryName != null ? registryName : ""));
        res.setFlags(List.of("NAME_MISMATCH_REVIEW"));
        res.setCheckedAt(Instant.now());
        return res;
    }

    public static ExternalKycResponse notFound(String idNumber, String requestedName) {
        ExternalKycResponse res = new ExternalKycResponse();
        res.setStatus("IN_REVIEW");
        res.setMessage(String.format("External KYC record not found for ID Number '%s'.", idNumber != null ? idNumber : "N/A"));
        res.setIdNumber(idNumber);
        res.setFullName(requestedName);
        res.setRegistryStatus("NOT_FOUND");
        res.setIsIdentityVerified(false);
        res.setIsBlacklisted(false);
        res.setAmlSanctionsStatus("PASS");
        res.setPepStatus("NOT_PEP");
        res.setCreditScore(500);
        res.setRiskLevel("MEDIUM");
        res.setRiskScore(45.0);
        res.setRemarks(String.format("ID Number '%s' not found in external national registry. Status set to IN_REVIEW for manual verification.",
                idNumber != null ? idNumber : "N/A"));
        res.setFlags(List.of("ID_NOT_FOUND_REVIEW"));
        res.setCheckedAt(Instant.now());
        return res;
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

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public java.math.BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(java.math.BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public String getRegistryStatus() {
        return registryStatus;
    }

    public void setRegistryStatus(String registryStatus) {
        this.registryStatus = registryStatus;
    }

    public Boolean getIsIdentityVerified() {
        return isIdentityVerified;
    }

    public void setIsIdentityVerified(Boolean identityVerified) {
        isIdentityVerified = identityVerified;
    }

    public Boolean getIsBlacklisted() {
        return isBlacklisted;
    }

    public void setIsBlacklisted(Boolean blacklisted) {
        isBlacklisted = blacklisted;
    }

    public String getAmlSanctionsStatus() {
        return amlSanctionsStatus;
    }

    public void setAmlSanctionsStatus(String amlSanctionsStatus) {
        this.amlSanctionsStatus = amlSanctionsStatus;
    }

    public String getPepStatus() {
        return pepStatus;
    }

    public void setPepStatus(String pepStatus) {
        this.pepStatus = pepStatus;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}
