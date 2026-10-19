package com.axlero.ragassistant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, Duration.ofMinutes(15));
    }

    @Test
    void generatesAndParsesUserClaims() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        assertEquals(
                new AuthenticatedUser(userId, "user@example.com", Role.USER),
                jwtService.parseToken(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService(
                Base64.getEncoder().encodeToString(new byte[32]),
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
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
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
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
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
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService(Base64.getEncoder().encodeToString(new byte[31]), Duration.ofMinutes(15)));
    }
}
