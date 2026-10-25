package com.axlero.ragassistant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Collections;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration providing API documentation and JWT Bearer authentication scheme
 * for Swagger UI.
 *
 * <p>Registers the {@code BearerAuth} security scheme in {@link Components} so Swagger UI exposes
 * the interactive authorization dialog. Uses a {@link GlobalOpenApiCustomizer} to apply the
 * {@code BearerAuth} requirement exclusively to protected operations while ensuring public
 * endpoints (such as {@code /api/auth/**}) remain explicitly unsecured.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise RAG Document Assistant API")
                        .version("0.0.1")
                        .description("REST APIs for the Enterprise RAG Document Assistant. Provides endpoints for "
                                + "stateless JWT user authentication, owner-scoped document management, "
                                + "vector retrieval, RAG streaming and question answering, and chat history. "
                                + "Public endpoints (/api/auth/**, /v3/api-docs/**, /swagger-ui/**, /actuator/health) "
                                + "do not require authentication. Protected endpoints require a valid JWT Bearer token."))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token to authorize requests.")));
    }

    @Bean
    public GlobalOpenApiCustomizer securityOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                boolean isPublic = isPublicPath(path);
                pathItem.readOperations().forEach(operation -> {
                    if (isPublic) {
                        operation.setSecurity(Collections.emptyList());
                    } else {
                        boolean alreadyHasBearer = operation.getSecurity() != null && operation.getSecurity().stream()
                                .anyMatch(req -> req.containsKey(SECURITY_SCHEME_NAME));
                        if (!alreadyHasBearer) {
                            operation.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
                        }
                    }
                });
            });
        };
    }

    public static boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/auth/")
                || path.equals("/api/auth")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator/health");
    }
}
