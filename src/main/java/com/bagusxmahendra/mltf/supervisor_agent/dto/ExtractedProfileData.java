package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Extracted profile metadata aggregated by the supervisor agent from document inspection and external KYC checks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedProfileData {

    @JsonProperty("fullName")
    @JsonAlias({"full_name", "name"})
    private String fullName;

    @JsonProperty("idCardNumber")
    @JsonAlias({"id_card_number", "idNumber", "id_number", "nationalId", "national_id"})
    private String idCardNumber;

    @JsonProperty("idCardType")
    @JsonAlias({"id_card_type", "idType", "id_type", "documentType", "document_type"})
    private String idCardType;

    @JsonProperty("dateOfBirth")
    @JsonAlias({"date_of_birth", "dob", "birthDate", "birth_date"})
    private String dateOfBirth;

    @JsonProperty("address")
    private String address;

    @JsonProperty("city")
    private String city;

    @JsonProperty("postalCode")
    @JsonAlias({"postal_code", "zipCode", "zip_code", "postcode"})
    private String postalCode;

    @JsonProperty("country")
    private String country;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("occupation")
    private String occupation;

    @JsonProperty("phoneNumber")
    @JsonAlias({"phone_number", "phone", "mobile", "mobileNumber", "mobile_number"})
    private String phoneNumber;

    @JsonProperty("monthlyIncome")
    @JsonAlias({"monthly_income", "income"})
    private BigDecimal monthlyIncome;

    public ExtractedProfileData() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getIdCardNumber() {
        return idCardNumber;
    }

    public void setIdCardNumber(String idCardNumber) {
        this.idCardNumber = idCardNumber;
    }

    public String getIdCardType() {
        return idCardType;
    }

    public void setIdCardType(String idCardType) {
        this.idCardType = idCardType;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }
}
