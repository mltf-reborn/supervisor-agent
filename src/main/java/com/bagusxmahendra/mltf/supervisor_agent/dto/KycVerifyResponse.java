package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Response DTO for POST /api/v1/kyc/verify.
 *
 * <p>Shaped to match what the Angular UI's {@code KycStatusResponse} and
 * {@code VerifiedKycData} interfaces expect:</p>
 *
 * <pre>
 * {
 *   "status":      "IN_REVIEW" | "APPROVED" | "PENDING" | "REJECTED",
 *   "message":     "...",
 *   "referenceId": "KYC-REV-2026-1234",
 *   "verifiedData": {
 *     "userId":      "auth0|...",
 *     "fullName":    "AHMAD SYAZWAN BIN ABDULLAH",
 *     "idNumber":    "940822-10-5819",
 *     "idType":      "MyKad (National Identity Card)",
 *     "dateOfBirth": "22 Aug 1994",
 *     "nationality": "Malaysian",
 *     "matchScore":  99.4,          // populated only when APPROVED
 *     "referenceId": "KYC-REV-2026-1234",
 *     "verifiedAt":  "8/26/2026, 2:23:00 PM",
 *     "status":      "IN_REVIEW"
 *   }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KycVerifyResponse(

        /** Top-level status string – mirrors KycStatusResponse.status consumed by the UI. */
        String status,

        /** Human-readable message. */
        String message,

        /** Backend reference ID echoed at the top level for convenience. */
        String referenceId,

        /** Nested verified data – mirrors the Angular VerifiedKycData interface. */
        VerifiedData verifiedData

) {

    /**
     * Mirrors the Angular {@code VerifiedKycData} interface field-for-field.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerifiedData(
            String userId,
            String fullName,
            /** Maps to KycProfile.idCardNumber – labelled idNumber by the UI. */
            String idNumber,
            /** Maps to KycProfile.idCardType – labelled idType by the UI. */
            String idType,
            String dateOfBirth,
            String nationality,
            /** Populated only when status is APPROVED; sourced from riskScore. */
            Double matchScore,
            String referenceId,
            String verifiedAt,
            /** Repeats the top-level status inside verifiedData as the UI reads it from here. */
            String status
    ) {}

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DOB_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM yyyy");

    /**
     * Builds a {@link KycVerifyResponse} from a persisted {@link KycProfile}.
     *
     * @param profile     the saved KYC profile
     * @param referenceId the generated submission reference ID
     * @param message     human-readable status message
     */
    public static KycVerifyResponse from(KycProfile profile, String referenceId, String message) {
        KycStatus kycStatus = profile.status() != null ? profile.status() : KycStatus.IN_REVIEW;
        String statusStr = kycStatus.name();

        String verifiedAt = profile.verifiedAt() != null
                ? DISPLAY_FORMATTER.format(profile.verifiedAt())
                : DISPLAY_FORMATTER.format(java.time.Instant.now());

        String dateOfBirth = profile.dateOfBirth() != null
                ? profile.dateOfBirth().format(DOB_FORMATTER)
                : null;

        // matchScore is meaningful for APPROVED; use riskScore as a proxy when available
        Double matchScore = (kycStatus == KycStatus.APPROVED && profile.riskScore() != null)
                ? profile.riskScore()
                : null;

        VerifiedData verifiedData = new VerifiedData(
                profile.userId(),
                profile.fullName(),
                profile.idCardNumber(),
                profile.idCardType(),
                dateOfBirth,
                profile.nationality(),
                matchScore,
                referenceId,
                verifiedAt,
                statusStr
        );

        return new KycVerifyResponse(statusStr, message, referenceId, verifiedData);
    }

    /**
     * Convenience factory for the common IN_REVIEW submission flow.
     */
    public static KycVerifyResponse inReview(KycProfile profile, String referenceId) {
        return from(
                profile,
                referenceId,
                "KYC documents received successfully. Verification is in progress."
        );
    }
}
