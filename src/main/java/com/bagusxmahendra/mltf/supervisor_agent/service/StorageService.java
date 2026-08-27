package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.dto.FileUploadResult;
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
     * Returns the configured bucket name.
     */
    String getBucketName();
}
