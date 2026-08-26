package com.bagusxmahendra.mltf.supervisor_agent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class Auth0JwtService {

    private static final Logger log = LoggerFactory.getLogger(Auth0JwtService.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Extracts the user ID from an Authorization header containing an Auth0 JWT.
     *
     * @param authorizationHeader the HTTP Authorization header (e.g. "Bearer <token>")
     * @return the extracted user ID
     * @throws ResponseStatusException if header is missing/invalid, token is expired, or user ID cannot be found
     */
    public String extractUserId(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        return extractUserIdFromToken(token);
    }

    /**
     * Extracts the raw token string from the Authorization header.
     *
     * @param authorizationHeader the HTTP Authorization header
     * @return the raw JWT token
     */
    public String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }

        String trimmed = authorizationHeader.trim();
        if (trimmed.equalsIgnoreCase("Bearer")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is empty");
        }
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = trimmed.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is empty");
            }
            return token;
        }

        // Also allow raw JWT if formatted as a 3-part dot-separated token
        if (trimmed.split("\\.").length == 3) {
            return trimmed;
        }

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid Authorization header format. Expected 'Bearer <token>'"
        );
    }

    /**
     * Decodes the JWT token and extracts the user ID from standard and custom claims.
     *
     * @param token the raw JWT token
     * @return the user ID
     */
    public String extractUserIdFromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token cannot be empty");
        }

        DecodedJWT decodedJwt;
        try {
            decodedJwt = JWT.decode(token);
        } catch (JWTDecodeException e) {
            log.warn("Failed to decode JWT token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token: " + e.getMessage(), e);
        }

        // Check token expiration
        Date expiresAt = decodedJwt.getExpiresAt();
        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now())) {
            log.warn("JWT token has expired at: {}", expiresAt);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired");
        }

        String userId = null;

        // 1. Check custom claim "userId"
        Claim userIdClaim = decodedJwt.getClaim("userId");
        if (!userIdClaim.isMissing() && !userIdClaim.isNull()) {
            String val = userIdClaim.asString();
            if (val != null && !val.isBlank()) {
                userId = val.trim();
            }
        }

        // 2. Check custom claim "user_id"
        if (userId == null) {
            Claim snakeCaseClaim = decodedJwt.getClaim("user_id");
            if (!snakeCaseClaim.isMissing() && !snakeCaseClaim.isNull()) {
                String val = snakeCaseClaim.asString();
                if (val != null && !val.isBlank()) {
                    userId = val.trim();
                }
            }
        }

        // 3. Check custom namespaced claims (e.g. "https://mltf.com/userId" or "https://mltf.com/user_id")
        if (userId == null) {
            for (Map.Entry<String, Claim> entry : decodedJwt.getClaims().entrySet()) {
                String key = entry.getKey().toLowerCase();
                if ((key.endsWith("/userid") || key.endsWith("/user_id")) && !entry.getValue().isNull()) {
                    String val = entry.getValue().asString();
                    if (val != null && !val.isBlank()) {
                        userId = val.trim();
                        break;
                    }
                }
            }
        }

        // 4. Check standard "sub" (subject) claim
        if (userId == null) {
            String subject = decodedJwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                userId = subject.trim();
            }
        }

        if (userId == null || userId.isBlank()) {
            log.warn("User ID not found in JWT token claims");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User ID claim ('sub', 'userId', or 'user_id') not found in JWT token"
            );
        }

        log.debug("Successfully extracted userId: {}", userId);
        return userId;
    }
}
