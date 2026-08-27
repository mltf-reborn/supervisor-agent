package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
import com.bagusxmahendra.mltf.supervisor_agent.dto.GcsFileDownload;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface StorageService {

    /**
     * Uploads a FilePart to Google Cloud Storage under the specified session ID and category prefix.
     *
     * @param filePart the multipart file part
     * @param sessionId session identifier
     * @param category category/slot (e.g., "document", "selfie")
     * @return Mono containing the FileUploadResult with file URLs
     */
    Mono<FileUploadResult> uploadFile(FilePart filePart, String sessionId, String category);

    /**
     * Uploads raw byte array content to Google Cloud Storage.
     *
     * @param content file content bytes
     * @param filename file name
     * @param contentType MIME type
     * @param sessionId session identifier
     * @param category category/slot
     * @return Mono containing the FileUploadResult with file URLs
     */
    Mono<FileUploadResult> uploadBytes(byte[] content, String filename, String contentType, String sessionId, String category);

    /**
     * Downloads a file from GCS by its object name (path inside the configured bucket).
     * Accepts either a bare object name (e.g. {@code session/document/id.jpg}) or a full
     * {@code gs://bucket/object} URI – the bucket segment is stripped automatically.
     *
     * @param objectNameOrGcsUri the object path or full gs:// URI
     * @return Mono of {@link GcsFileDownload} containing raw bytes and content-type
     */
    Mono<GcsFileDownload> downloadFile(String objectNameOrGcsUri);

    /**
     * Returns the configured bucket name.
     */
    String getBucketName();
}
