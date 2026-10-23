package com.axlero.ragassistant.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsValidationErrorsToBadRequestProblem() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "filename", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult);

        ProblemDetail problem = handler.handleValidation(exception, request("/probe/validation"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getProperties()).containsEntry("errors", java.util.Map.of("filename", "must not be blank"));
        assertThat(problem.getInstance()).hasToString("/probe/validation");
    }

    @Test
    void mapsMissingResourceToNotFoundProblem() {
        ProblemDetail problem = handler.handleNotFound(
                new ResourceNotFoundException("Document not found"), request("/probe/missing"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Document not found");
        assertThat(problem.getInstance()).hasToString("/probe/missing");
    }

    @Test
    void mapsNoResourceFoundTo404Problem() {
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET, "/probe/static-resource");

        ProblemDetail problem = handler.handleNoResourceFound(
                exception, request("/probe/static-resource"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).contains("No static resource");
        assertThat(problem.getInstance()).hasToString("/probe/static-resource");
    }

    @Test
    void mapsMethodNotSupportedTo405Problem() {
        HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException(
                "POST", List.of("GET"));

        ProblemDetail problem = handler.handleMethodNotSupported(
                exception, request("/probe/get-only"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(problem.getTitle()).isEqualTo("Method not allowed");
        assertThat(problem.getDetail()).contains("Request method 'POST' is not supported");
        assertThat(problem.getInstance()).hasToString("/probe/get-only");
    }

    @Test
    void mapsMediaTypeNotSupportedTo415Problem() {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ProblemDetail problem = handler.handleMediaTypeNotSupported(
                exception, request("/probe/json-only"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(problem.getTitle()).isEqualTo("Unsupported media type");
        assertThat(problem.getDetail()).contains("Content-Type 'text/plain' is not supported");
        assertThat(problem.getInstance()).hasToString("/probe/json-only");
    }

    @Test
    void mapsMediaTypeNotAcceptableTo406Problem() {
        HttpMediaTypeNotAcceptableException exception = new HttpMediaTypeNotAcceptableException(
                List.of(MediaType.APPLICATION_JSON));

        ProblemDetail problem = handler.handleMediaTypeNotAcceptable(
                exception, request("/probe/acceptable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Not acceptable");
        assertThat(problem.getInstance()).hasToString("/probe/acceptable");
    }

    @Test
    void mapsServiceUnavailableTo503Problem() {
        ProblemDetail problem = handler.handleServiceUnavailable(
                new ServiceUnavailableException("AI service is unavailable", new IllegalStateException()),
                request("/probe/unavailable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Service unavailable");
        assertThat(problem.getDetail()).isEqualTo("A required service is temporarily unavailable");
        assertThat(problem.getInstance()).hasToString("/probe/unavailable");
    }

    @Test
    void mapsDatabaseUnavailableTo503Problem() {
        ProblemDetail problem = handler.handleServiceUnavailable(
                new DataAccessResourceFailureException("database unavailable"),
                request("/probe/database-unavailable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ProblemDetail problem = handler.handleGeneric(
                new IllegalStateException("database password"), request("/probe/unexpected"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getInstance()).hasToString("/probe/unexpected");
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        return request;
    }
}
