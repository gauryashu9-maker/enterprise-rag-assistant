package com.axlero.ragassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
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
    void configuresGlobalSecurityRequirement() {
        OpenAPI openAPI = openApiConfig.openAPI();

        assertThat(openAPI.getSecurity()).isNotNull().isNotEmpty();
        SecurityRequirement requirement = openAPI.getSecurity().get(0);
        assertThat(requirement).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
    }

    @Test
    void registersOpenApiBeanInSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            OpenAPI openAPI = context.getBean(OpenAPI.class);
            assertThat(openAPI.getInfo().getTitle()).isEqualTo("Enterprise RAG Document Assistant API");
            assertThat(openAPI.getComponents().getSecuritySchemes())
                    .containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
        });
    }
}
