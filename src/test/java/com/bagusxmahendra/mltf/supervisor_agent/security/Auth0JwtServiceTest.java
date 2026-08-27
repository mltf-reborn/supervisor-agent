package com.bagusxmahendra.mltf.supervisor_agent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Auth0JwtServiceTest {

    private Auth0JwtService auth0JwtService;
    private Algorithm algorithm;

    @BeforeEach
    void setUp() {
        auth0JwtService = new Auth0JwtService();
        algorithm = Algorithm.HMAC256("test-secret-key-12345");
    }

    private String createToken(String subject, Date expiresAt) {
        return JWT.create()
                .withSubject(subject)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    @Test
    void extractUserId_withValidSubject_shouldReturnUserId() {
        String token = createToken("usr_1001", Date.from(Instant.now().plusSeconds(3600)));
        String authHeader = "Bearer " + token;

        String userId = auth0JwtService.extractUserId(authHeader);

        assertEquals("usr_1001", userId);
    }

    @Test
    void extractUserId_withCaseInsensitiveBearer_shouldReturnUserId() {
        String token = createToken("usr_1001", Date.from(Instant.now().plusSeconds(3600)));
        String authHeader = "bearer " + token;

        String userId = auth0JwtService.extractUserId(authHeader);

        assertEquals("usr_1001", userId);
    }

    @Test
    void extractUserId_withRawJwt_shouldReturnUserId() {
        String token = createToken("usr_1001", Date.from(Instant.now().plusSeconds(3600)));

        String userId = auth0JwtService.extractUserId(token);

        assertEquals("usr_1001", userId);
    }

    @Test
    void extractUserId_withCustomUserIdClaim_shouldPrioritizeCustomClaim() {
        String token = JWT.create()
                .withSubject("auth0|12345678")
                .withClaim("userId", "usr_custom_1002")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String userId = auth0JwtService.extractUserId("Bearer " + token);

        assertEquals("usr_custom_1002", userId);
    }

    @Test
    void extractUserId_withSnakeCaseUserIdClaim_shouldReturnUserId() {
        String token = JWT.create()
                .withClaim("user_id", "usr_snake_1003")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String userId = auth0JwtService.extractUserId("Bearer " + token);

        assertEquals("usr_snake_1003", userId);
    }

    @Test
    void extractUserId_withNamespacedUserIdClaim_shouldReturnUserId() {
        String token = JWT.create()
                .withClaim("https://mltf.example.com/userId", "usr_namespaced_1004")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String userId = auth0JwtService.extractUserId("Bearer " + token);

        assertEquals("usr_namespaced_1004", userId);
    }

    @Test
    void extractUserId_withExpiredToken_shouldThrowUnauthorized() {
        String token = createToken("usr_1001", Date.from(Instant.now().minusSeconds(60)));
        String authHeader = "Bearer " + token;

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId(authHeader)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
    }

    @Test
    void extractUserId_withNullOrBlankHeader_shouldThrowUnauthorized() {
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId(null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId("   ")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex2.getStatusCode());
    }

    @Test
    void extractUserId_withInvalidHeaderFormat_shouldThrowUnauthorized() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId("Basic dXNlcjpwYXNz")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid Authorization header format"));
    }

    @Test
    void extractUserId_withEmptyBearerToken_shouldThrowUnauthorized() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId("Bearer    ")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Bearer token is empty"));
    }

    @Test
    void extractUserId_withMalformedToken_shouldThrowUnauthorized() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId("Bearer not.a.valid.jwt.token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid JWT token"));
    }

    @Test
    void extractUserId_withoutSubjectOrUserIdClaim_shouldThrowUnauthorized() {
        String token = JWT.create()
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractUserId("Bearer " + token)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User ID claim"));
    }

    @Test
    void extractEmail_withStandardClaim_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail("Bearer " + token);

        assertEquals("john.doe@example.com", email);
    }

    @Test
    void extractEmail_withCaseInsensitiveBearer_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail("bearer " + token);

        assertEquals("john.doe@example.com", email);
    }

    @Test
    void extractEmail_withRawJwt_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail(token);

        assertEquals("john.doe@example.com", email);
    }

    @Test
    void extractEmail_withSnakeCaseUserEmailClaim_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("user_email", "snake.email@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail("Bearer " + token);

        assertEquals("snake.email@example.com", email);
    }

    @Test
    void extractEmail_withCamelCaseUserEmailClaim_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("userEmail", "camel.email@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail("Bearer " + token);

        assertEquals("camel.email@example.com", email);
    }

    @Test
    void extractEmail_withNamespacedEmailClaim_shouldReturnEmail() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("https://mltf.example.com/email", "namespaced.email@example.com")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        String email = auth0JwtService.extractEmail("Bearer " + token);

        assertEquals("namespaced.email@example.com", email);
    }

    @Test
    void extractEmail_withExpiredToken_shouldThrowUnauthorized() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withClaim("email", "john.doe@example.com")
                .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
                .sign(algorithm);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractEmail("Bearer " + token)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
    }

    @Test
    void extractEmail_withNullOrBlankHeader_shouldThrowUnauthorized() {
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractEmail(null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractEmail("   ")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex2.getStatusCode());
    }

    @Test
    void extractEmail_withoutEmailClaim_shouldThrowUnauthorized() {
        String token = JWT.create()
                .withSubject("usr_1001")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> auth0JwtService.extractEmail("Bearer " + token)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Email claim"));
    }
}
