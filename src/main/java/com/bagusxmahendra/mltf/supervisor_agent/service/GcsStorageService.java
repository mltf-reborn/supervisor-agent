package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.config.StorageProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.UUID;

@Service
public class GcsStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageService.class);

    private final Storage storage;
    private final StorageProperties properties;

    public GcsStorageService(Storage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public String getBucketName() {
        return properties.getBucketName();
    }

    @Override
    public Mono<FileUploadResult> uploadFile(FilePart filePart, String sessionId, String category) {
        if (filePart == null) {
            return Mono.error(new IllegalArgumentException("FilePart cannot be null for category: " + category));
        }

        String rawFilename = filePart.filename();
        String filename = sanitizeFilename(rawFilename, category);
        String contentType = determineContentType(filePart, filename);
        String session = sanitizeSessionId(sessionId);
        String objectName = session + "/" + category + "/" + filename;

        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> uploadToGcs(bytes, objectName, contentType, filename));
    }

    @Override
    public Mono<FileUploadResult> uploadBytes(byte[] content, String filename, String contentType, String sessionId, String category) {
        if (content == null) {
            return Mono.error(new IllegalArgumentException("Content bytes cannot be null for category: " + category));
        }

        String safeFilename = sanitizeFilename(filename, category);
        String safeContentType = (contentType != null && !contentType.isBlank()) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String session = sanitizeSessionId(sessionId);
        String objectName = session + "/" + category + "/" + safeFilename;

        return uploadToGcs(content, objectName, safeContentType, safeFilename);
    }

    private Mono<FileUploadResult> uploadToGcs(byte[] bytes, String objectName, String contentType, String filename) {
        return Mono.fromCallable(() -> {
            String bucket = properties.getBucketName();
            log.info("Uploading file to GCS [bucket={}, objectName={}, size={} bytes, contentType={}]",
                    bucket, objectName, bytes.length, contentType);

            BlobId blobId = BlobId.of(bucket, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            Blob blob = storage.create(blobInfo, bytes);
            String gcsUrl = "gs://" + bucket + "/" + objectName;
            String httpsUrl = "https://storage.googleapis.com/" + bucket + "/" + objectName;

            log.info("Successfully uploaded file to GCS: {}", gcsUrl);
            return new FileUploadResult(
                    filename,
                    contentType,
                    bytes.length,
                    bucket,
                    objectName,
                    gcsUrl,
                    gcsUrl,
                    gcsUrl,
                    httpsUrl
            );
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private String sanitizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sessionId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sanitizeFilename(String filename, String fallbackPrefix) {
        if (filename == null || filename.trim().isEmpty()) {
            return fallbackPrefix + ".jpg";
        }
        String clean = new File(filename.trim()).getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.isEmpty()) {
            return fallbackPrefix + ".jpg";
        }
        return clean;
    }

    private String determineContentType(FilePart filePart, String filename) {
        HttpHeaders headers = filePart.headers();
        MediaType mediaType = headers != null ? headers.getContentType() : null;
        if (mediaType != null && !mediaType.toString().isBlank()
                && !mediaType.toString().equalsIgnoreCase(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
            return mediaType.toString();
        }

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        } else if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else {
            return MediaType.IMAGE_JPEG_VALUE;
        }
    }
}
