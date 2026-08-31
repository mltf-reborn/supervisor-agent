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
import java.util.List;
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
        org.mockito.Mockito.lenient().when(appStruct.isNull("other_commitments")).thenReturn(true);
        org.mockito.Mockito.lenient().when(appStruct.isNull("close_relatives")).thenReturn(true);

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
        org.mockito.Mockito.lenient().when(applicantStruct.isNull("other_commitments")).thenReturn(true);
        org.mockito.Mockito.lenient().when(applicantStruct.isNull("close_relatives")).thenReturn(true);

        Map<String, Object> applicantData = Map.of(
                "full_name", "Bagus Wicaksono",
                "monthly_gross_rm", 9000.0
        );

        StepVerifier.create(repository.saveApplicant("TXN-1", "usr_1001", applicantData))
                .verifyComplete();

        verify(databaseClient).write(any());
    }

    @Test
    void checkSimilarity_whenSimilarityCheckDisabled_shouldPassWithoutConflicts() {
        SupervisorAgentProperties properties = new SupervisorAgentProperties();
        properties.setSimilarityCheckEnabled(false);
        SpannerLoanApplicationRepository disabledRepo = new SpannerLoanApplicationRepository(databaseClient, properties);

        Struct appStruct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(appStruct);

        // Application row mock
        when(appStruct.isNull("bank_selection")).thenReturn(false);
        when(appStruct.getString("bank_selection")).thenReturn("CIMB");
        when(appStruct.isNull("application_type")).thenReturn(false);
        when(appStruct.getString("application_type")).thenReturn("HOME_LOAN");
        when(appStruct.isNull("status")).thenReturn(true);
        when(appStruct.isNull("facility_type")).thenReturn(true);
        when(appStruct.isNull("facility_purpose")).thenReturn(true);
        when(appStruct.isNull("marketing_consent")).thenReturn(true);
        when(appStruct.isNull("application_date")).thenReturn(true);

        // Applicant fields
        when(appStruct.isNull("role")).thenReturn(false);
        when(appStruct.getString("role")).thenReturn("Primary");
        when(appStruct.isNull("full_name")).thenReturn(false);
        when(appStruct.getString("full_name")).thenReturn("John Doe");
        // mock spouse_id_no to be non-null to trigger similarity check which would conflict if enabled
        when(appStruct.isNull("spouse_id_no")).thenReturn(false);
        when(appStruct.getString("spouse_id_no")).thenReturn("123-123456-2");
        when(appStruct.isNull("monthly_gross_rm")).thenReturn(true);
        when(appStruct.isNull("dependents_count")).thenReturn(true);
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
        when(appStruct.isNull("spouse_mobile")).thenReturn(true);
        when(appStruct.isNull("spouse_employer")).thenReturn(true);
        when(appStruct.isNull("spouse_monthly_gross_rm")).thenReturn(true);
        org.mockito.Mockito.lenient().when(appStruct.isNull("other_commitments")).thenReturn(true);
        org.mockito.Mockito.lenient().when(appStruct.isNull("close_relatives")).thenReturn(true);

        // Property fields
        when(appStruct.isNull("property_type")).thenReturn(true);
        when(appStruct.isNull("property_status")).thenReturn(true);
        when(appStruct.isNull("developer_name")).thenReturn(true);
        when(appStruct.isNull("project_name")).thenReturn(true);
        when(appStruct.isNull("contractor_name")).thenReturn(true);
        when(appStruct.isNull("spa_price_rm")).thenReturn(true);
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
                "spouse_id_no", "X1424123"
        );
        Map<String, Object> applicationData = Map.of(
                "bank_selection", "Maybank",
                "application_type", "HOME_LOAN"
        );
        Map<String, Object> propertyData = Map.of(
                "spa_price_rm", 650000.0
        );

        StepVerifier.create(disabledRepo.checkSimilarity("TXN-1", applicantData, applicationData, propertyData))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("PASSED", result.get("status"));
                    assertEquals(false, result.get("hasConflict"));
                    assertEquals(0, result.get("conflictCount"));
                })
                .verifyComplete();
    }

    @Test
    void findApplicationsByStatus_shouldQueryAndMapSubmittedApplications() {
        Struct appStruct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(appStruct);

        when(appStruct.getString("transaction_id")).thenReturn("TXN-101");
        when(appStruct.getString("user_id")).thenReturn("usr_101");
        when(appStruct.isNull("application_type")).thenReturn(false);
        when(appStruct.getString("application_type")).thenReturn("Single Application");
        when(appStruct.isNull("status")).thenReturn(false);
        when(appStruct.getString("status")).thenReturn("SUBMITTED");

        StepVerifier.create(repository.findApplicationsByStatus("SUBMITTED"))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("TXN-101", list.get(0).transactionId());
                    assertEquals("usr_101", list.get(0).userId());
                    assertEquals("SUBMITTED", list.get(0).status());
                })
                .verifyComplete();
    }

    @Test
    void updateStatus_shouldWriteMutation() {
        StepVerifier.create(repository.updateStatus("TXN-101", "APPROVED"))
                .verifyComplete();

        verify(databaseClient).write(any());
    }

    @Test
    void updateStatusAndAiAnalysis_shouldWriteMutationWithAiAnalysis() {
        String aiJson = "{\"graphAnalysis\":{\"status\":\"APPROVED\"},\"documents\":[]}";
        StepVerifier.create(repository.updateStatusAndAiAnalysis("TXN-101", "APPROVED", aiJson))
                .verifyComplete();

        verify(databaseClient).write(any());
    }

    @Test
    void findAllApplicationDetails_shouldReturnApplicationsWithDocumentsAndProcessingDetails() {
        ResultSet appRs = mock(ResultSet.class);
        ResultSet applicantRs = mock(ResultSet.class);
        ResultSet propertyRs = mock(ResultSet.class);
        ResultSet docRs = mock(ResultSet.class);

        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class)))
                .thenReturn(appRs, applicantRs, propertyRs, docRs);

        // App row
        Struct appStruct = mock(Struct.class);
        when(appRs.next()).thenReturn(true, false);
        when(appRs.getCurrentRowAsStruct()).thenReturn(appStruct);
        when(appStruct.getString("transaction_id")).thenReturn("TXN-101");
        when(appStruct.getString("user_id")).thenReturn("usr_101");
        when(appStruct.isNull("created_at")).thenReturn(true);
        when(appStruct.isNull("bank_selection")).thenReturn(false);
        when(appStruct.getString("bank_selection")).thenReturn("Maybank");
        when(appStruct.isNull("application_type")).thenReturn(false);
        when(appStruct.getString("application_type")).thenReturn("HOME_LOAN");
        when(appStruct.isNull("status")).thenReturn(false);
        when(appStruct.getString("status")).thenReturn("SUBMITTED");
        when(appStruct.isNull("facility_type")).thenReturn(true);
        when(appStruct.isNull("facility_purpose")).thenReturn(true);
        when(appStruct.isNull("facilities_required")).thenReturn(true);
        when(appStruct.isNull("refinancing_bank")).thenReturn(true);
        when(appStruct.isNull("joint_relationship")).thenReturn(true);
        when(appStruct.isNull("marketing_consent")).thenReturn(true);
        when(appStruct.isNull("docs_enclosed")).thenReturn(true);
        when(appStruct.isNull("ftfc_category")).thenReturn(true);
        when(appStruct.isNull("signatures")).thenReturn(true);
        when(appStruct.isNull("application_date")).thenReturn(true);
        when(appStruct.isNull("ai_analysis")).thenReturn(true);

        // Applicant rows (empty)
        when(applicantRs.next()).thenReturn(false);

        // Property rows (empty)
        when(propertyRs.next()).thenReturn(false);

        // Document row
        Struct docStruct = mock(Struct.class);
        when(docRs.next()).thenReturn(true, false);
        when(docRs.getCurrentRowAsStruct()).thenReturn(docStruct);
        when(docStruct.getString("transaction_id")).thenReturn("TXN-101");
        when(docStruct.isNull("document_id")).thenReturn(false);
        when(docStruct.getString("document_id")).thenReturn("DOC-999");
        when(docStruct.isNull("document_filename")).thenReturn(false);
        when(docStruct.getString("document_filename")).thenReturn("payslip.pdf");
        when(docStruct.isNull("gcs_url")).thenReturn(false);
        when(docStruct.getString("gcs_url")).thenReturn("gs://bucket/payslip.pdf");
        when(docStruct.isNull("content_type")).thenReturn(false);
        when(docStruct.getString("content_type")).thenReturn("application/pdf");
        when(docStruct.isNull("document_status")).thenReturn(false);
        when(docStruct.getString("document_status")).thenReturn("SUCCESS");
        when(docStruct.isNull("document_message")).thenReturn(false);
        when(docStruct.getString("document_message")).thenReturn("OK");
        when(docStruct.isNull("document_processing_details")).thenReturn(false);
        when(docStruct.getString("document_processing_details")).thenReturn("{\"grossSalary\": 8000}");
        when(docStruct.isNull("created_at")).thenReturn(true);

        StepVerifier.create(repository.findAllApplicationDetails())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    Map<String, Object> item = list.get(0);
                    assertEquals("TXN-101", item.get("transaction_id"));
                    assertEquals("usr_101", item.get("user_id"));

                    assertTrue(item.containsKey("documents"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> docs = (List<Map<String, Object>>) item.get("documents");
                    assertEquals(1, docs.size());
                    assertEquals("DOC-999", docs.get(0).get("document_id"));
                    assertEquals("payslip.pdf", docs.get(0).get("document_filename"));
                    assertEquals("SUCCESS", docs.get(0).get("document_status"));
                    assertEquals("{\"grossSalary\": 8000}", docs.get(0).get("document_processing_details"));
                })
                .verifyComplete();
    }
}

