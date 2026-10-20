package com.axlero.ragassistant.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axlero.ragassistant.config.CorsConfig;
import com.axlero.ragassistant.config.SecurityConfig;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.services.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityProbeController.class)
@Import({
    SecurityConfig.class,
    CorsConfig.class,
    MethodAuthorizationTestConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    SecurityProblemHandler.class
})
@TestPropertySource(properties = "app.security.jwt.expiration=PT15M")
class SecurityConfigHttpTest {

    private static final String TEST_SECRET;

    static {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        TEST_SECRET = Base64.getEncoder().encodeToString(keyBytes);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> TEST_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void permitsConfiguredPublicEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectsPathsOutsideTheExactPublicAllowlist() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance", equalTo("/swagger-ui.html")));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance", equalTo("/actuator/health/liveness")));
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance", equalTo("/api/private")));
    }

    @Test
    void requiresTokenForProtectedEndpointsAndReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.detail",
                        equalTo("Authentication is required or the bearer token is invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/api/private")));
    }

    @Test
    void suppliesAuthenticatedUserFromVerifiedClaims() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(userId + ":user@example.com:USER"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void returnsSanitizedForbiddenProblemForUserDeniedByAdminRule() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", Role.USER);

        mockMvc.perform(get("/api/admin-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Forbidden")))
                .andExpect(jsonPath("$.status", equalTo(403)))
                .andExpect(jsonPath("$.detail",
                        equalTo("You do not have permission to access this resource")))
                .andExpect(jsonPath("$.instance", equalTo("/api/admin-probe")))
                .andExpect(content().string(not(containsString("internal authorization detail"))));
    }

    @Test
    void rejectsInvalidTokenOnProtectedEndpointWithoutExposingIt() throws Exception {
        String token = "invalid-token-marker";

        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance", equalTo("/api/private")))
                .andExpect(content().string(not(containsString(token))));
    }

    @Test
    void rejectsExpiredTokenWithUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/private")));
    }

    @Test
    void rejectsTokenWithoutExpirationWithUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + tokenWithoutExpiration()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/private")));
    }

    @Test
    void rejectsTamperedTokenWithUnauthorizedProblemDetail() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", Role.USER);
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        parts[2] = (firstSignatureCharacter == 'A' ? "B" : "A") + parts[2].substring(1);

        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/private")));
    }

    @Test
    void servesCorsPreflightOnlyForConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/private")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/private")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String expiredToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithoutExpiration() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET)), Jwts.SIG.HS256)
                .compact();
    }
}
