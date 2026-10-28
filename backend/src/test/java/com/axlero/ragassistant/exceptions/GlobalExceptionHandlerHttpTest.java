package com.axlero.ragassistant.exceptions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerHttpTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsBadRequestProblemForInvalidRequest() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Validation failed")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.instance", equalTo("/probe/validation")))
                .andExpect(jsonPath("$.errors.name", equalTo("must not be blank")));
    }

    @Test
    void returnsBadRequestProblemForMalformedJson() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"secret-marker\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Malformed request")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", equalTo("Request body is missing or invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/validation")))
                .andExpect(content().string(not(containsString("secret-marker"))));
    }

    @Test
    void returnsSanitizedBadRequestProblemForUnexpectedJsonToken() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": UNEXPECTED_SECRET_TOKEN}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Malformed request")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", equalTo("Request body is missing or invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/validation")))
                .andExpect(content().string(not(containsString("UNEXPECTED_SECRET_TOKEN"))))
                .andExpect(content().string(not(containsString("Unrecognized token"))))
                .andExpect(content().string(not(containsString("Unexpected character"))))
                .andExpect(content().string(not(containsString("JsonParseException"))))
                .andExpect(content().string(not(containsString("JsonMappingException"))))
                .andExpect(content().string(not(containsString("HttpMessageNotReadableException"))));
    }

    @Test
    void returnsBadRequestProblemForMissingRequiredRequestParam() throws Exception {
        mockMvc.perform(get("/probe/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Bad request")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", equalTo("Required parameter 'query' is missing")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/missing-param")));
    }

    @Test
    void returnsNotFoundProblemForMissingResource() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Document not found")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/missing")));
    }

    @Test
    void returnsNotFoundProblemForStaticResource() throws Exception {
        mockMvc.perform(get("/probe/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", containsString("No static resource")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/resource-not-found")));
    }

    @Test
    void returnsMethodNotAllowedProblemForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/probe/resource-not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Method not allowed")))
                .andExpect(jsonPath("$.status", equalTo(405)))
                .andExpect(jsonPath("$.detail", containsString("Request method 'POST' is not supported")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/resource-not-found")));
    }

    @Test
    void returnsUnsupportedMediaTypeProblemForInvalidContentType() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unsupported media type")))
                .andExpect(jsonPath("$.status", equalTo(415)))
                .andExpect(jsonPath("$.detail", containsString("Content-Type 'text/plain' is not supported")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/validation")));
    }

    @Test
    void returnsNotAcceptableProblemForUnacceptableAcceptHeader() throws Exception {
        mockMvc.perform(get("/probe/acceptable")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Not acceptable")))
                .andExpect(jsonPath("$.status", equalTo(406)))
                .andExpect(jsonPath("$.instance", equalTo("/probe/acceptable")));
    }

    @Test
    void returnsServiceUnavailableProblemForUnavailableService() throws Exception {
        mockMvc.perform(get("/probe/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Service unavailable")))
                .andExpect(jsonPath("$.status", equalTo(503)))
                .andExpect(jsonPath("$.instance", equalTo("/probe/unavailable")))
                .andExpect(content().string(not(containsString("AI service is unavailable"))));
    }

    @Test
    void returnsSanitizedProblemForDatabaseFailure() throws Exception {
        mockMvc.perform(get("/probe/database-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Service unavailable")))
                .andExpect(jsonPath("$.status", equalTo(503)))
                .andExpect(jsonPath("$.detail",
                        equalTo("A required service is temporarily unavailable")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/database-unavailable")))
                .andExpect(content().string(not(containsString("database credentials must not be exposed"))));
    }

    @Test
    void returnsUnauthorizedProblemForBadCredentials() throws Exception {
        mockMvc.perform(get("/probe/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.detail", equalTo("Invalid email or password")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/bad-credentials")))
                .andExpect(content().string(not(containsString("secret credentials"))));
    }

    @Test
    void returnsConflictProblemForDuplicateEmail() throws Exception {
        mockMvc.perform(post("/probe/duplicate-email"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Email already registered")))
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.detail", equalTo("Email is already registered")))
                .andExpect(jsonPath("$.instance", equalTo("/probe/duplicate-email")));
    }

    @Test
    void returnsInternalServerErrorProblemForUnexpectedFailure() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            mockMvc.perform(get("/probe/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status", equalTo(500)))
                    .andExpect(jsonPath("$.detail", equalTo("An unexpected error occurred")))
                    .andExpect(jsonPath("$.instance", equalTo("/probe/unexpected")))
                    .andExpect(content().string(not(containsString("internal failure"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))));

            assertThat(listAppender.list).isNotEmpty();
            ILoggingEvent loggingEvent = listAppender.list.get(0);
            assertThat(loggingEvent.getLevel()).isEqualTo(Level.ERROR);
            assertThat(loggingEvent.getFormattedMessage()).contains("/probe/unexpected");
            assertThat(loggingEvent.getThrowableProxy()).isNotNull();
            assertThat(loggingEvent.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(loggingEvent.getThrowableProxy().getMessage()).isEqualTo("internal failure");
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @RestController
    @RequestMapping("/probe")
    static class ErrorProbeController {

        @PostMapping(value = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody ValidRequest request) {
        }

        @GetMapping(value = "/acceptable", produces = MediaType.APPLICATION_JSON_VALUE)
        ValidRequest acceptable() {
            return new ValidRequest("ok");
        }

        @GetMapping("/missing-param")
        void missingParam(@RequestParam("query") String query) {
        }

        @GetMapping("/resource-not-found")
        void resourceNotFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/probe/resource-not-found");
        }

        @GetMapping("/missing")
        void missing() {
            throw new ResourceNotFoundException("Document not found");
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new ServiceUnavailableException("AI service is unavailable", new IllegalStateException());
        }

        @GetMapping("/database-unavailable")
        void databaseUnavailable() {
            throw new DataAccessResourceFailureException("database credentials must not be exposed");
        }

        @GetMapping("/bad-credentials")
        void badCredentials() {
            throw new BadCredentialsException("secret credentials must not be exposed");
        }

        @PostMapping("/duplicate-email")
        void duplicateEmail() {
            throw new EmailAlreadyRegisteredException();
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal failure");
        }
    }

    record ValidRequest(@NotBlank String name) {
    }
}
