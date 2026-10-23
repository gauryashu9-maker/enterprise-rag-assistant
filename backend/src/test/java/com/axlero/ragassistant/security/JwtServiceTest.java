package com.axlero.ragassistant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.services.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static String testSecret;

    @BeforeAll
    static void generateTestSecret() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        testSecret = Base64.getEncoder().encodeToString(keyBytes);
    }

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(testSecret, Duration.ofMinutes(15));
    }

    @Test
    void exposesExactGenerateTokenSignature() throws NoSuchMethodException {
        var method = JwtService.class.getMethod(
                "generateToken", UUID.class, String.class, Role.class);

        assertEquals(String.class, method.getReturnType());
    }

    @Test
    void generatesExpectedClaimsAndParsesVerifiedUser() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(testSecret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("user@example.com", claims.get("email", String.class));
        assertEquals(Role.USER.name(), claims.get("role", String.class));
        assertEquals(
                Duration.ofMinutes(15).toMillis(),
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertEquals(
                new AuthenticatedUser(userId, "user@example.com", Role.USER),
                jwtService.parseToken(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        byte[] otherKeyBytes = new byte[32];
        new SecureRandom().nextBytes(otherKeyBytes);
        JwtService otherService = new JwtService(
                Base64.getEncoder().encodeToString(otherKeyBytes),
                Duration.ofMinutes(15));
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", Role.USER);

        assertThrows(JwtException.class, () -> otherService.parseToken(token));
    }

    @Test
    void rejectsExpiredToken() {
        Date expired = new Date(1_000L);
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date(0L))
                .expiration(expired)
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(testSecret)), Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(testSecret)), Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", Role.USER);
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        parts[2] = (firstSignatureCharacter == 'A' ? "B" : "A") + parts[2].substring(1);

        assertThrows(JwtException.class, () -> jwtService.parseToken(String.join(".", parts)));
    }

    @Test
    void rejectsSecretsThatAreNotBase64OrTooShort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("not base64", Duration.ofMinutes(15)));
        byte[] shortBytes = new byte[31];
        new SecureRandom().nextBytes(shortBytes);
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService(Base64.getEncoder().encodeToString(shortBytes), Duration.ofMinutes(15)));
    }
}
