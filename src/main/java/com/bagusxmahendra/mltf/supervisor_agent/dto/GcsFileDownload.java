package com.bagusxmahendra.mltf.supervisor_agent.dto;

/**
 * Result of downloading a file from Google Cloud Storage.
 *
 * @param content     raw file bytes
 * @param contentType MIME type of the file (e.g. "image/jpeg", "image/png")
 * @param filename    original filename for Content-Disposition header
 */
public record GcsFileDownload(
        byte[] content,
        String contentType,
        String filename
) {}
