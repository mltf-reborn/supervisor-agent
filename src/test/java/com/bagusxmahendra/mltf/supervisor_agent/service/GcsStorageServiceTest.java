package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.config.StorageProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcsStorageServiceTest {

    @Mock
    private Storage storage;

    private StorageProperties properties;
    private GcsStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setProjectId("mltf-506212");
        properties.setBucketName("mltf-bucket");
        storageService = new GcsStorageService(storage, properties);
    }

    @Test
    void getBucketName_shouldReturnConfiguredBucket() {
        assertEquals("mltf-bucket", storageService.getBucketName());
    }

    @Test
    void uploadBytes_shouldStoreInGcsAndReturnResult() {
        byte[] content = "dummy-document-content".getBytes(StandardCharsets.UTF_8);
        Blob mockBlob = mock(Blob.class);
        when(storage.create(any(BlobInfo.class), eq(content))).thenReturn(mockBlob);

        StepVerifier.create(storageService.uploadBytes(content, "mykad.jpg", "image/jpeg", "session-123", "document"))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("mykad.jpg", result.filename());
                    assertEquals("image/jpeg", result.contentType());
                    assertEquals("mltf-bucket", result.bucketName());
                    assertEquals("session-123/document/mykad.jpg", result.objectName());
                    assertEquals("gs://mltf-bucket/session-123/document/mykad.jpg", result.gcsUrl());
                    assertEquals("gs://mltf-bucket/session-123/document/mykad.jpg", result.gcsUri());
                    assertEquals("gs://mltf-bucket/session-123/document/mykad.jpg", result.fileUrl());
                    assertEquals("https://storage.googleapis.com/mltf-bucket/session-123/document/mykad.jpg", result.httpsUrl());
                })
                .verifyComplete();

        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(captor.capture(), eq(content));
        assertEquals("mltf-bucket", captor.getValue().getBucket());
        assertEquals("session-123/document/mykad.jpg", captor.getValue().getName());
        assertEquals("image/jpeg", captor.getValue().getContentType());
    }

    @Test
    void uploadFile_withFilePart_shouldStoreInGcsAndReturnResult() {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);

        when(filePart.filename()).thenReturn("selfie.jpg");
        when(filePart.headers()).thenReturn(headers);

        byte[] rawBytes = "selfie-image-data".getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(rawBytes);
        when(filePart.content()).thenReturn(Flux.just(dataBuffer));

        Blob mockBlob = mock(Blob.class);
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mockBlob);

        StepVerifier.create(storageService.uploadFile(filePart, "session-456", "selfie"))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("selfie.jpg", result.filename());
                    assertEquals("image/jpeg", result.contentType());
                    assertEquals("mltf-bucket", result.bucketName());
                    assertEquals("session-456/selfie/selfie.jpg", result.objectName());
                    assertEquals("gs://mltf-bucket/session-456/selfie/selfie.jpg", result.gcsUrl());
                    assertEquals("gs://mltf-bucket/session-456/selfie/selfie.jpg", result.gcsUri());
                    assertEquals("gs://mltf-bucket/session-456/selfie/selfie.jpg", result.fileUrl());
                    assertEquals("https://storage.googleapis.com/mltf-bucket/session-456/selfie/selfie.jpg", result.httpsUrl());
                })
                .verifyComplete();
    }
}
