package com.axlero.ragassistant.security;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class JwtSecretStartupTest {

    @Test
    void failsToStartJwtServiceWhenJwtSecretIsMissing() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        assertNull(environment.getProperty("JWT_SECRET"));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.register(JwtService.class);

            BeansException exception = assertThrows(BeansException.class, context::refresh);
            String exceptionMessages = exception.getMessage();
            Throwable cause = exception.getCause();
            while (cause != null) {
                exceptionMessages += " " + cause.getMessage();
                cause = cause.getCause();
            }
            assertTrue(exceptionMessages.contains("JWT_SECRET"), exceptionMessages);
        }
    }
}
