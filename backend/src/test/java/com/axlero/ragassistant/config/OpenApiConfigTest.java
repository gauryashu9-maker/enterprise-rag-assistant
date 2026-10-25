package com.axlero.ragassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(OpenApiConfig.class);

    @Test
    void configuresExpectedApiMetadata() {
        OpenAPI openAPI = openApiConfig.openAPI();

        assertThat(openAPI).isNotNull();
        Info info = openAPI.getInfo();
        assertThat(info).isNotNull();
        assertThat(info.getTitle()).isEqualTo("Enterprise RAG Document Assistant API");
        assertThat(info.getVersion()).isEqualTo("0.0.1");
        assertThat(info.getDescription())
                .isNotBlank()
                .contains("Enterprise RAG Document Assistant")
                .contains("JWT Bearer token");
    }

    @Test
    void configuresHttpBearerJwtSecurityScheme() {
        OpenAPI openAPI = openApiConfig.openAPI();

        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        SecurityScheme scheme =
                openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.SECURITY_SCHEME_NAME);
        assertThat(scheme.getName()).isEqualTo(OpenApiConfig.SECURITY_SCHEME_NAME);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(scheme.getDescription()).contains("JWT token");
    }

    @Test
    void doesNotConfigureMisleadingGlobalSecurityRequirementOnRootOpenApi() {
        OpenAPI openAPI = openApiConfig.openAPI();

        // Root-level security requirement should be null or empty so public routes are not marked protected
        assertThat(openAPI.getSecurity()).isNullOrEmpty();
    }

    @Test
    void appliesBearerAuthOnlyToProtectedOperations() {
        OpenAPI openAPI = openApiConfig.openAPI();

        Paths paths = new Paths();

        // Public auth operations
        PathItem authRegister = new PathItem().post(new Operation().operationId("registerUser"));
        PathItem authLogin = new PathItem().post(new Operation().operationId("loginUser"));
        paths.addPathItem("/api/auth/register", authRegister);
        paths.addPathItem("/api/auth/login", authLogin);

        // Protected business operations
        PathItem documents = new PathItem().get(new Operation().operationId("listDocuments"))
                .post(new Operation().operationId("uploadDocument"));
        PathItem documentById = new PathItem().delete(new Operation().operationId("deleteDocument"));
        PathItem ragQuery = new PathItem().post(new Operation().operationId("queryRag"));
        PathItem aiPing = new PathItem().get(new Operation().operationId("pingAi"));
        paths.addPathItem("/api/documents", documents);
        paths.addPathItem("/api/documents/{id}", documentById);
        paths.addPathItem("/api/rag/query", ragQuery);
        paths.addPathItem("/api/ai/ping", aiPing);

        openAPI.setPaths(paths);

        // Execute the customizer
        GlobalOpenApiCustomizer customizer = openApiConfig.securityOpenApiCustomizer();
        customizer.customise(openAPI);

        // Verify public operations have NO BearerAuth security requirement
        assertThat(authRegister.getPost().getSecurity()).isNotNull().isEmpty();
        assertThat(authLogin.getPost().getSecurity()).isNotNull().isEmpty();

        // Verify protected operations have BearerAuth security requirement
        assertThat(documents.getGet().getSecurity()).hasSize(1);
        SecurityRequirement docGetReq = documents.getGet().getSecurity().get(0);
        assertThat(docGetReq).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        assertThat(documents.getPost().getSecurity()).hasSize(1);
        SecurityRequirement docPostReq = documents.getPost().getSecurity().get(0);
        assertThat(docPostReq).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        assertThat(documentById.getDelete().getSecurity()).hasSize(1);
        SecurityRequirement docDelReq = documentById.getDelete().getSecurity().get(0);
        assertThat(docDelReq).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        assertThat(ragQuery.getPost().getSecurity()).hasSize(1);
        SecurityRequirement ragReq = ragQuery.getPost().getSecurity().get(0);
        assertThat(ragReq).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        assertThat(aiPing.getGet().getSecurity()).hasSize(1);
        SecurityRequirement aiReq = aiPing.getGet().getSecurity().get(0);
        assertThat(aiReq).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
    }

    @Test
    void isPublicPathIdentifiesExpectedPublicEndpoints() {
        assertThat(OpenApiConfig.isPublicPath("/api/auth/register")).isTrue();
        assertThat(OpenApiConfig.isPublicPath("/api/auth/login")).isTrue();
        assertThat(OpenApiConfig.isPublicPath("/api/auth")).isTrue();
        assertThat(OpenApiConfig.isPublicPath("/v3/api-docs/swagger-config")).isTrue();
        assertThat(OpenApiConfig.isPublicPath("/swagger-ui/index.html")).isTrue();
        assertThat(OpenApiConfig.isPublicPath("/actuator/health")).isTrue();

        assertThat(OpenApiConfig.isPublicPath("/api/documents")).isFalse();
        assertThat(OpenApiConfig.isPublicPath("/api/documents/123")).isFalse();
        assertThat(OpenApiConfig.isPublicPath("/api/rag/query")).isFalse();
        assertThat(OpenApiConfig.isPublicPath("/api/ai/ping")).isFalse();
        assertThat(OpenApiConfig.isPublicPath("/api/chat/sessions")).isFalse();
        assertThat(OpenApiConfig.isPublicPath(null)).isFalse();
    }

    @Test
    void registersOpenApiAndCustomizerBeansInSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context).hasSingleBean(GlobalOpenApiCustomizer.class);

            OpenAPI openAPI = context.getBean(OpenAPI.class);
            assertThat(openAPI.getInfo().getTitle()).isEqualTo("Enterprise RAG Document Assistant API");
            assertThat(openAPI.getComponents().getSecuritySchemes())
                    .containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
            assertThat(openAPI.getSecurity()).isNullOrEmpty();
        });
    }
}
