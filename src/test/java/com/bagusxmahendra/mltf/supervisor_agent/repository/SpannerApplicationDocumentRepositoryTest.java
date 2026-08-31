package com.bagusxmahendra.mltf.supervisor_agent.repository;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.ReadContext;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpannerApplicationDocumentRepositoryTest {

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private ReadContext readContext;

    @Mock
    private ResultSet resultSet;

    private SpannerApplicationDocumentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SpannerApplicationDocumentRepository(databaseClient);
    }

    @Test
    void findByTransactionId_shouldQueryAndMapDocumentRecords() {
        Struct docStruct = mock(Struct.class);
        when(databaseClient.singleUse()).thenReturn(readContext);
        when(readContext.executeQuery(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getCurrentRowAsStruct()).thenReturn(docStruct);

        when(docStruct.getString("transaction_id")).thenReturn("TXN-101");
        when(docStruct.getString("document_id")).thenReturn("DOC-01");
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
        when(docStruct.isNull("document_processing_details")).thenReturn(true);
        when(docStruct.isNull("created_at")).thenReturn(false);
        when(docStruct.getTimestamp("created_at")).thenReturn(Timestamp.now());

        StepVerifier.create(repository.findByTransactionId("TXN-101"))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("DOC-01", list.get(0).documentId());
                    assertEquals("payslip.pdf", list.get(0).documentFilename());
                    assertEquals("SUCCESS", list.get(0).documentStatus());
                })
                .verifyComplete();
    }

    @Test
    void updateDocumentProcessingResult_shouldWriteMutation() {
        StepVerifier.create(repository.updateDocumentProcessingResult("TXN-101", "DOC-01", "SUCCESS", "OK", "{}"))
                .verifyComplete();

        verify(databaseClient).write(any());
    }

    @Test
    void save_shouldWriteMutation() {
        StepVerifier.create(repository.save("TXN-101", "DOC-01", "payslip.pdf", "gs://bucket/payslip.pdf", "application/pdf", "SUCCESS", "OK", "{}"))
                .verifyComplete();

        verify(databaseClient).write(any());
    }

    @Test
    void delete_shouldWriteMutation() {
        StepVerifier.create(repository.delete("TXN-101", "DOC-01"))
                .verifyComplete();

        verify(databaseClient).write(any());
    }
}
