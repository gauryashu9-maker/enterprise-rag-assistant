package com.axlero.ragassistant.exceptions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(content().string(not(containsString("secret-marker"))));
    }

    @Test
    void returnsNotFoundProblemForMissingResource() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Document not found")));
    }

    @Test
    void returnsServiceUnavailableProblemForUnavailableService() throws Exception {
        mockMvc.perform(get("/probe/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title", equalTo("Service unavailable")))
                .andExpect(jsonPath("$.status", equalTo(503)));
    }

    @Test
    void returnsSanitizedProblemForDatabaseFailure() throws Exception {
        mockMvc.perform(get("/probe/database-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Service unavailable")))
                .andExpect(jsonPath("$.status", equalTo(503)))
                .andExpect(jsonPath("$.detail",
                        equalTo("A required service is temporarily unavailable")));
    }

    @Test
    void returnsInternalServerErrorProblemForUnexpectedFailure() throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", equalTo(500)))
                .andExpect(jsonPath("$.detail", equalTo("An unexpected error occurred")));
    }

    @RestController
    @RequestMapping("/probe")
    static class ErrorProbeController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidRequest request) {
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

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal failure");
        }
    }

    record ValidRequest(@NotBlank String name) {
    }
}
