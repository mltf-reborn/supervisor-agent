package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationSummaryResponse;
import com.google.cloud.spanner.Struct;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ApplicationSummaryResponseMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private ApplicationSummaryResponseMapper() {
    }

    static ApplicationSummaryResponse from(Struct row) {
        String dateApplied = null;
        if (!row.isNull("application_date")) {
            com.google.cloud.Date date = row.getDate("application_date");
            dateApplied = LocalDate.of(date.getYear(), date.getMonth(), date.getDayOfMonth()).format(DATE_FORMAT);
        }

        String propertyPrice = null;
        if (!row.isNull("spa_price_rm")) {
            BigDecimal price = row.getBigDecimal("spa_price_rm");
            propertyPrice = "RM " + CURRENCY_FORMAT.format(price);
        }

        return new ApplicationSummaryResponse(
                row.getString("transaction_id"),
                dateApplied,
                row.isNull("facility_purpose") ? null : row.getString("facility_purpose"),
                row.isNull("project_name") ? null : row.getString("project_name"),
                propertyPrice,
                row.isNull("application_type") ? null : row.getString("application_type"),
                formatStatus(row.isNull("status") ? null : row.getString("status"))
        );
    }

    private static String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return status;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "IN_REVIEW" -> "In Review";
            case "PENDING_APPROVAL" -> "Pending Approval";
            case "SUBMITTED" -> "Submitted";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            case "NEW" -> "New";
            default -> status;
        };
    }
}