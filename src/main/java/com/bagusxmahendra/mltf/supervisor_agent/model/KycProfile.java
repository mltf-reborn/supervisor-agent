package com.bagusxmahendra.mltf.supervisor_agent.model;

import com.google.cloud.spanner.Struct;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record KycProfile(
        String userId,
        String fullName,
        String email,
        String phoneNumber,
        String idCardNumber,
        String idCardType,
        LocalDate dateOfBirth,
        String address,
        String city,
        String postalCode,
        String country,
        String nationality,
        String occupation,
        BigDecimal monthlyIncome,
        KycStatus status,
        Double riskScore,
        String riskLevel,
        String rejectionReason,
        String remarks,
        String verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static KycProfile fromStruct(Struct struct) {
        if (struct == null) {
            return null;
        }

        String userId = struct.isNull("user_id") ? null : struct.getString("user_id");
        String fullName = struct.isNull("full_name") ? null : struct.getString("full_name");
        String email = struct.isNull("email") ? null : struct.getString("email");
        String phoneNumber = struct.isNull("phone_number") ? null : struct.getString("phone_number");
        String idCardNumber = struct.isNull("id_card_number") ? null : struct.getString("id_card_number");
        String idCardType = struct.isNull("id_card_type") ? null : struct.getString("id_card_type");
        
        LocalDate dateOfBirth = null;
        if (!struct.isNull("date_of_birth")) {
            com.google.cloud.Date d = struct.getDate("date_of_birth");
            dateOfBirth = LocalDate.of(d.getYear(), d.getMonth(), d.getDayOfMonth());
        }

        String address = struct.isNull("address") ? null : struct.getString("address");
        String city = struct.isNull("city") ? null : struct.getString("city");
        String postalCode = struct.isNull("postal_code") ? null : struct.getString("postal_code");
        String country = struct.isNull("country") ? null : struct.getString("country");
        String nationality = struct.isNull("nationality") ? null : struct.getString("nationality");
        String occupation = struct.isNull("occupation") ? null : struct.getString("occupation");
        
        BigDecimal monthlyIncome = struct.isNull("monthly_income") ? null : struct.getBigDecimal("monthly_income");
        
        String statusStr = struct.isNull("status") ? null : struct.getString("status");
        KycStatus status = KycStatus.fromString(statusStr);
        
        Double riskScore = struct.isNull("risk_score") ? null : struct.getDouble("risk_score");
        String riskLevel = struct.isNull("risk_level") ? null : struct.getString("risk_level");
        String rejectionReason = struct.isNull("rejection_reason") ? null : struct.getString("rejection_reason");
        String remarks = struct.isNull("remarks") ? null : struct.getString("remarks");
        String verifiedBy = struct.isNull("verified_by") ? null : struct.getString("verified_by");

        Instant verifiedAt = null;
        if (!struct.isNull("verified_at")) {
            com.google.cloud.Timestamp ts = struct.getTimestamp("verified_at");
            verifiedAt = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        }

        Instant createdAt = null;
        if (!struct.isNull("created_at")) {
            com.google.cloud.Timestamp ts = struct.getTimestamp("created_at");
            createdAt = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        }

        Instant updatedAt = null;
        if (!struct.isNull("updated_at")) {
            com.google.cloud.Timestamp ts = struct.getTimestamp("updated_at");
            updatedAt = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        }

        return new KycProfile(
                userId,
                fullName,
                email,
                phoneNumber,
                idCardNumber,
                idCardType,
                dateOfBirth,
                address,
                city,
                postalCode,
                country,
                nationality,
                occupation,
                monthlyIncome,
                status,
                riskScore,
                riskLevel,
                rejectionReason,
                remarks,
                verifiedBy,
                verifiedAt,
                createdAt,
                updatedAt
        );
    }
}
