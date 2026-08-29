package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.ReadContext;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpannerLoanApplicationRepositoryTest {

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private ReadContext readContext;

    @Mock
    private ResultSet resultSet;

    private SpannerLoanApplicationRepository repository;

    @BeforeEach
    void setUp() {
        SupervisorAgentProperties properties = new SupervisorAgentProperties();
        repository = new SpannerLoanApplicationRepository(databaseClient, properties);
    }

    @Test
    void checkSimilarity_whenExistingDataIsZeroOrNAOrEmpty_shouldPassWithoutConflict() {
        Struct appStruct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(appStruct);

        // Application row mock (existing is N/A and empty)
        when(appStruct.isNull("bank_selection")).thenReturn(false);
        when(appStruct.getString("bank_selection")).thenReturn("N/A");
        when(appStruct.isNull("application_type")).thenReturn(false);
        when(appStruct.getString("application_type")).thenReturn("");
        when(appStruct.isNull("status")).thenReturn(true);
        when(appStruct.isNull("facility_type")).thenReturn(false);
        when(appStruct.getString("facility_type")).thenReturn("n/a");
        when(appStruct.isNull("facility_purpose")).thenReturn(true);
        when(appStruct.isNull("marketing_consent")).thenReturn(true);
        when(appStruct.isNull("application_date")).thenReturn(true);

        // Applicant fields
        when(appStruct.isNull("role")).thenReturn(false);
        when(appStruct.getString("role")).thenReturn("Primary");
        when(appStruct.isNull("full_name")).thenReturn(false);
        when(appStruct.getString("full_name")).thenReturn("N/A");
        when(appStruct.isNull("monthly_gross_rm")).thenReturn(false);
        when(appStruct.getBigDecimal("monthly_gross_rm")).thenReturn(BigDecimal.ZERO);
        when(appStruct.isNull("dependents_count")).thenReturn(false);
        when(appStruct.getLong("dependents_count")).thenReturn(0L);
        when(appStruct.isNull("id_type")).thenReturn(true);
        when(appStruct.isNull("id_no")).thenReturn(true);
        when(appStruct.isNull("nationality")).thenReturn(true);
        when(appStruct.isNull("race")).thenReturn(true);
        when(appStruct.isNull("bumiputera_status")).thenReturn(true);
        when(appStruct.isNull("gender")).thenReturn(true);
        when(appStruct.isNull("marital_status")).thenReturn(true);
        when(appStruct.isNull("date_of_birth")).thenReturn(true);
        when(appStruct.isNull("education_level")).thenReturn(true);
        when(appStruct.isNull("mobile_phone")).thenReturn(true);
        when(appStruct.isNull("residential_phone")).thenReturn(true);
        when(appStruct.isNull("email")).thenReturn(true);
        when(appStruct.isNull("perm_address")).thenReturn(true);
        when(appStruct.isNull("perm_postcode")).thenReturn(true);
        when(appStruct.isNull("perm_city")).thenReturn(true);
        when(appStruct.isNull("perm_state")).thenReturn(true);
        when(appStruct.isNull("mail_address")).thenReturn(true);
        when(appStruct.isNull("mail_postcode")).thenReturn(true);
        when(appStruct.isNull("employment_status")).thenReturn(true);
        when(appStruct.isNull("employer_name")).thenReturn(true);
        when(appStruct.isNull("nature_of_business")).thenReturn(true);
        when(appStruct.isNull("occupation")).thenReturn(true);
        when(appStruct.isNull("job_position")).thenReturn(true);
        when(appStruct.isNull("length_of_service_years")).thenReturn(true);
        when(appStruct.isNull("annual_gross_rm")).thenReturn(true);
        when(appStruct.isNull("emergency_name")).thenReturn(true);
        when(appStruct.isNull("emergency_relationship")).thenReturn(true);
        when(appStruct.isNull("emergency_phone")).thenReturn(true);
        when(appStruct.isNull("spouse_full_name")).thenReturn(true);
        when(appStruct.isNull("spouse_id_no")).thenReturn(true);
        when(appStruct.isNull("spouse_mobile")).thenReturn(true);
        when(appStruct.isNull("spouse_employer")).thenReturn(true);
        when(appStruct.isNull("spouse_monthly_gross_rm")).thenReturn(true);

        // Property fields
        when(appStruct.isNull("property_type")).thenReturn(true);
        when(appStruct.isNull("property_status")).thenReturn(true);
        when(appStruct.isNull("developer_name")).thenReturn(true);
        when(appStruct.isNull("project_name")).thenReturn(true);
        when(appStruct.isNull("contractor_name")).thenReturn(true);
        when(appStruct.isNull("spa_price_rm")).thenReturn(false);
        when(appStruct.getBigDecimal("spa_price_rm")).thenReturn(BigDecimal.ZERO);
        when(appStruct.isNull("open_market_rm")).thenReturn(true);
        when(appStruct.isNull("renovation_value_rm")).thenReturn(true);
        when(appStruct.isNull("property_address")).thenReturn(true);
        when(appStruct.isNull("property_postcode")).thenReturn(true);
        when(appStruct.isNull("property_city")).thenReturn(true);
        when(appStruct.isNull("property_state")).thenReturn(true);
        when(appStruct.isNull("title_number")).thenReturn(true);
        when(appStruct.isNull("title_type")).thenReturn(true);
        when(appStruct.isNull("lot_number")).thenReturn(true);
        when(appStruct.isNull("mukim")).thenReturn(true);
        when(appStruct.isNull("district")).thenReturn(true);
        when(appStruct.isNull("is_owner_occupied")).thenReturn(true);
        when(appStruct.isNull("is_first_time_buyer")).thenReturn(true);

        Map<String, Object> applicantData = Map.of(
                "full_name", "Bagus Wicaksono",
                "monthly_gross_rm", 8500.0,
                "dependents_count", 2
        );
        Map<String, Object> applicationData = Map.of(
                "bank_selection", "Maybank",
                "application_type", "HOME_LOAN",
                "facility_type", "Conventional"
        );
        Map<String, Object> propertyData = Map.of(
                "spa_price_rm", 650000.0
        );

        StepVerifier.create(repository.checkSimilarity("TXN-1", applicantData, applicationData, propertyData))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("PASSED", result.get("status"));
                    assertEquals(false, result.get("hasConflict"));
                    assertEquals(0, result.get("conflictCount"));
                })
                .verifyComplete();
    }

    @Test
    void saveApplicant_whenExistingDataIsZeroOrNA_shouldOverwriteWithoutError() {
        Struct applicantStruct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(applicantStruct);

        when(applicantStruct.isNull("role")).thenReturn(false);
        when(applicantStruct.getString("role")).thenReturn("Primary");
        when(applicantStruct.isNull("full_name")).thenReturn(false);
        when(applicantStruct.getString("full_name")).thenReturn("N/A");
        when(applicantStruct.isNull("monthly_gross_rm")).thenReturn(false);
        when(applicantStruct.getBigDecimal("monthly_gross_rm")).thenReturn(BigDecimal.ZERO);
        when(applicantStruct.isNull("id_type")).thenReturn(true);
        when(applicantStruct.isNull("id_no")).thenReturn(true);
        when(applicantStruct.isNull("nationality")).thenReturn(true);
        when(applicantStruct.isNull("race")).thenReturn(true);
        when(applicantStruct.isNull("bumiputera_status")).thenReturn(true);
        when(applicantStruct.isNull("gender")).thenReturn(true);
        when(applicantStruct.isNull("marital_status")).thenReturn(true);
        when(applicantStruct.isNull("date_of_birth")).thenReturn(true);
        when(applicantStruct.isNull("dependents_count")).thenReturn(true);
        when(applicantStruct.isNull("education_level")).thenReturn(true);
        when(applicantStruct.isNull("mobile_phone")).thenReturn(true);
        when(applicantStruct.isNull("residential_phone")).thenReturn(true);
        when(applicantStruct.isNull("email")).thenReturn(true);
        when(applicantStruct.isNull("perm_address")).thenReturn(true);
        when(applicantStruct.isNull("perm_postcode")).thenReturn(true);
        when(applicantStruct.isNull("perm_city")).thenReturn(true);
        when(applicantStruct.isNull("perm_state")).thenReturn(true);
        when(applicantStruct.isNull("mail_address")).thenReturn(true);
        when(applicantStruct.isNull("mail_postcode")).thenReturn(true);
        when(applicantStruct.isNull("employment_status")).thenReturn(true);
        when(applicantStruct.isNull("employer_name")).thenReturn(true);
        when(applicantStruct.isNull("nature_of_business")).thenReturn(true);
        when(applicantStruct.isNull("occupation")).thenReturn(true);
        when(applicantStruct.isNull("job_position")).thenReturn(true);
        when(applicantStruct.isNull("length_of_service_years")).thenReturn(true);
        when(applicantStruct.isNull("annual_gross_rm")).thenReturn(true);
        when(applicantStruct.isNull("emergency_name")).thenReturn(true);
        when(applicantStruct.isNull("emergency_relationship")).thenReturn(true);
        when(applicantStruct.isNull("emergency_phone")).thenReturn(true);
        when(applicantStruct.isNull("spouse_full_name")).thenReturn(true);
        when(applicantStruct.isNull("spouse_id_no")).thenReturn(true);
        when(applicantStruct.isNull("spouse_mobile")).thenReturn(true);
        when(applicantStruct.isNull("spouse_employer")).thenReturn(true);
        when(applicantStruct.isNull("spouse_monthly_gross_rm")).thenReturn(true);

        Map<String, Object> applicantData = Map.of(
                "full_name", "Bagus Wicaksono",
                "monthly_gross_rm", 9000.0
        );

        StepVerifier.create(repository.saveApplicant("TXN-1", "usr_1001", applicantData))
                .verifyComplete();

        verify(databaseClient).write(any());
    }
}
