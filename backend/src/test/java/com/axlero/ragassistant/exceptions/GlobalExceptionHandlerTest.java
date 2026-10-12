package com.axlero.ragassistant.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsValidationErrorsToBadRequestProblem() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "filename", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult);

        ProblemDetail problem = handler.handleValidation(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getProperties()).containsEntry("errors", java.util.Map.of("filename", "must not be blank"));
    }

    @Test
    void mapsMissingResourceToNotFoundProblem() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("Document not found"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Document not found");
    }

    @Test
    void mapsServiceUnavailableTo503Problem() {
        ProblemDetail problem = handler.handleServiceUnavailable(
                new ServiceUnavailableException("AI service is unavailable", new IllegalStateException()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Service unavailable");
        assertThat(problem.getDetail()).isEqualTo("A required service is temporarily unavailable");
    }

    @Test
    void mapsDatabaseUnavailableTo503Problem() {
        ProblemDetail problem = handler.handleServiceUnavailable(
                new DataAccessResourceFailureException("database unavailable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ProblemDetail problem = handler.handleGeneric(new IllegalStateException("database password"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
