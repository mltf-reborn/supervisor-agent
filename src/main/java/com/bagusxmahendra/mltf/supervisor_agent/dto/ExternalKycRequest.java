package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload sent to /api/v1/external/kyc.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalKycRequest {

    @JsonProperty("idNumber")
    @JsonAlias({"id_number", "idCardNumber", "id_card_number", "nationalId", "national_id"})
    private String idNumber;

    @JsonProperty("fullName")
    @JsonAlias({"full_name", "name", "customerName", "customer_name"})
    private String fullName;

    @JsonProperty("dateOfBirth")
    @JsonAlias({"date_of_birth", "dob", "birthDate", "birth_date"})
    private String dateOfBirth;

    @JsonProperty("nationality")
    @JsonAlias({"country", "citizenship"})
    private String nationality;

    public ExternalKycRequest() {
    }

    public ExternalKycRequest(String idNumber, String fullName, String dateOfBirth, String nationality) {
        this.idNumber = idNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
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
}
