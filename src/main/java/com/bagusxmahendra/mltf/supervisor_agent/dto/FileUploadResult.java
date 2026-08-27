package com.bagusxmahendra.mltf.supervisor_agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of storing a file in Google Cloud Storage.
 * The primary URL returned is the GCS URL (e.g. gs://bucket/session/category/filename).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadResult(
        String filename,
        String contentType,
        long size,
        String bucketName,
        String objectName,
        String gcsUrl,
        String gcsUri,
        String fileUrl,
        String httpsUrl
) {
    public FileUploadResult(
            String filename,
            String contentType,
            long size,
            String bucketName,
            String objectName,
            String gcsUrl
    ) {
        this(filename, contentType, size, bucketName, objectName, gcsUrl, gcsUrl, gcsUrl, null);
    }

    public FileUploadResult(
            String filename,
            String contentType,
            long size,
            String bucketName,
            String objectName,
            String gcsUrl,
            String httpsUrl
    ) {
        this(filename, contentType, size, bucketName, objectName, gcsUrl, gcsUrl, gcsUrl, httpsUrl);
    }
}
