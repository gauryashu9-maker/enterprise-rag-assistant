package com.axlero.ragassistant.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badCredentialsReturns401WithTheSafeMessage() {
        ProblemDetail problem = handler.handleBadCredentials(
                new BadCredentialsException("Invalid email or password"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
        assertThat(problem.getDetail()).isEqualTo("Invalid email or password");
    }

    @Test
    void duplicateEmailReturns409() {
        ProblemDetail problem = handler.handleEmailAlreadyRegistered(
                new EmailAlreadyRegisteredException());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Email already registered");
        assertThat(problem.getDetail()).isEqualTo("Email is already registered");
    }
}