package com.bagusxmahendra.mltf.supervisor_agent.controller;

import com.bagusxmahendra.mltf.supervisor_agent.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Proxy endpoint that fetches a private GCS object and streams it back to the browser.
 *
 * <pre>
 * GET /api/v1/media/image?gcsUrl=gs%3A%2F%2Fmltf-bucket%2Fsession%2Fdocument%2Fid.jpg
 * </pre>
 *
 * <p>This is intentionally unauthenticated so the Angular Ops Dashboard can use the URL
 * directly as an {@code <img>} {@code src}. Sensitive access control is enforced at the
 * nginx/ingress layer which only allows the Ops sub-path to users with an Ops session.</p>
 */
@RestController
@RequestMapping("/api/v1/media")
public class GcsImageController {

    private static final Logger log = LoggerFactory.getLogger(GcsImageController.class);

    private final StorageService storageService;

    public GcsImageController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Stream a GCS object to the browser as an image or PDF.
     *
     * @param gcsUrl full {@code gs://bucket/object} URI stored in the case record
     * @return raw bytes with the appropriate Content-Type header
     */
    @GetMapping("/image")
    public Mono<ResponseEntity<byte[]>> getImage(
            @RequestParam(name = "gcsUrl") String gcsUrl
    ) {
        if (gcsUrl == null || gcsUrl.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "'gcsUrl' query parameter is required"));
        }

        log.info("GcsImageController: serving GCS file → {}", gcsUrl);

        return storageService.downloadFile(gcsUrl)
                .map(download -> {
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parseMediaType(download.contentType());
                    } catch (Exception e) {
                        mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    }

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(mediaType);
                    // Allow the browser to display the file inline (important for <img>)
                    headers.set(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + download.filename() + "\"");
                    // Basic cache: ops officers may view the same image many times
                    headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=300");

                    return ResponseEntity
                            .status(HttpStatus.OK)
                            .headers(headers)
                            .body(download.content());
                });
    }
}
