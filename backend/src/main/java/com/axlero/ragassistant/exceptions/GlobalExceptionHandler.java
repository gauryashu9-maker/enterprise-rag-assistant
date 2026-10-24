package com.axlero.ragassistant.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return withInstance(problem, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body is missing or invalid");
        problem.setTitle("Malformed request");
        return withInstance(problem, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        return withInstance(problem, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage());
        problem.setTitle("Method not allowed");
        return withInstance(problem, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
        problem.setTitle("Unsupported media type");
        return withInstance(problem, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_ACCEPTABLE, exception.getMessage());
        problem.setTitle("Not acceptable");
        return withInstance(problem, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        return withInstance(problem, request);
    }

    @ExceptionHandler({ServiceUnavailableException.class, DataAccessException.class})
    ProblemDetail handleServiceUnavailable(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "A required service is temporarily unavailable");
        problem.setTitle("Service unavailable");
        return withInstance(problem, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(
            BadCredentialsException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
        problem.setTitle("Unauthorized");
        return withInstance(problem, request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Email already registered");
        return withInstance(problem, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception exception, HttpServletRequest request) {
        log.error("An unexpected error occurred while processing request {}", request.getRequestURI(), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return withInstance(problem, request);
    }

    private ProblemDetail withInstance(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
