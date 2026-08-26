package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.bagusxmahendra.mltf.supervisor_agent.model.KycProfile;
import com.bagusxmahendra.mltf.supervisor_agent.model.KycStatus;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpannerKycRepositoryTest {

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private ReadContext readContext;

    @Mock
    private ResultSet resultSet;

    private SpannerKycRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SpannerKycRepository(databaseClient);
    }

    @Test
    void findByUserId_whenRecordExists_shouldReturnKycProfile() {
        Struct struct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(struct);

        when(struct.isNull("user_id")).thenReturn(false);
        when(struct.getString("user_id")).thenReturn("usr_1001");
        when(struct.isNull("full_name")).thenReturn(false);
        when(struct.getString("full_name")).thenReturn("John Doe");
        when(struct.isNull("email")).thenReturn(false);
        when(struct.getString("email")).thenReturn("john.doe@example.com");
        when(struct.isNull("phone_number")).thenReturn(false);
        when(struct.getString("phone_number")).thenReturn("+1-555-0199");
        when(struct.isNull("id_card_number")).thenReturn(false);
        when(struct.getString("id_card_number")).thenReturn("ID-12345");
        when(struct.isNull("id_card_type")).thenReturn(false);
        when(struct.getString("id_card_type")).thenReturn("NATIONAL_ID");
        when(struct.isNull("date_of_birth")).thenReturn(false);
        when(struct.getDate("date_of_birth")).thenReturn(Date.fromYearMonthDay(1990, 1, 15));
        when(struct.isNull("address")).thenReturn(true);
        when(struct.isNull("city")).thenReturn(true);
        when(struct.isNull("postal_code")).thenReturn(true);
        when(struct.isNull("country")).thenReturn(true);
        when(struct.isNull("nationality")).thenReturn(true);
        when(struct.isNull("occupation")).thenReturn(true);
        when(struct.isNull("monthly_income")).thenReturn(false);
        when(struct.getBigDecimal("monthly_income")).thenReturn(BigDecimal.valueOf(8000));
        when(struct.isNull("status")).thenReturn(false);
        when(struct.getString("status")).thenReturn("APPROVED");
        when(struct.isNull("risk_score")).thenReturn(false);
        when(struct.getDouble("risk_score")).thenReturn(15.0);
        when(struct.isNull("risk_level")).thenReturn(false);
        when(struct.getString("risk_level")).thenReturn("LOW");
        when(struct.isNull("rejection_reason")).thenReturn(true);
        when(struct.isNull("remarks")).thenReturn(true);
        when(struct.isNull("verified_by")).thenReturn(true);
        when(struct.isNull("verified_at")).thenReturn(false);
        when(struct.getTimestamp("verified_at")).thenReturn(Timestamp.ofTimeSecondsAndNanos(1700000000, 0));
        when(struct.isNull("created_at")).thenReturn(false);
        when(struct.getTimestamp("created_at")).thenReturn(Timestamp.ofTimeSecondsAndNanos(1690000000, 0));
        when(struct.isNull("updated_at")).thenReturn(false);
        when(struct.getTimestamp("updated_at")).thenReturn(Timestamp.ofTimeSecondsAndNanos(1700000000, 0));

        StepVerifier.create(repository.findByUserId("usr_1001"))
                .assertNext(profile -> {
                    assertNotNull(profile);
                    assertEquals("usr_1001", profile.userId());
                    assertEquals("John Doe", profile.fullName());
                    assertEquals("john.doe@example.com", profile.email());
                    assertEquals(KycStatus.APPROVED, profile.status());
                    assertEquals(15.0, profile.riskScore());
                })
                .verifyComplete();
    }

    @Test
    void findByUserId_whenRecordDoesNotExist_shouldReturnEmpty() {
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        StepVerifier.create(repository.findByUserId("unknown"))
                .verifyComplete();
    }

    @Test
    void save_shouldWriteMutationToDatabase() {
        KycProfile profile = new KycProfile(
                "usr_1001",
                "John Doe",
                "john.doe@example.com",
                "+1-555-0199",
                "ID-12345",
                "NATIONAL_ID",
                LocalDate.of(1990, 1, 15),
                "123 Street",
                "City",
                "10001",
                "USA",
                "American",
                "Engineer",
                BigDecimal.valueOf(8000),
                KycStatus.APPROVED,
                15.0,
                "LOW",
                null,
                "Looks good",
                "agent_1",
                null,
                null,
                null
        );

        StepVerifier.create(repository.save(profile))
                .verifyComplete();

        verify(databaseClient).write(any());
    }
}
