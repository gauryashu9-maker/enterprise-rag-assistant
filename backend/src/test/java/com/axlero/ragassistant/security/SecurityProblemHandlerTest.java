package com.axlero.ragassistant.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class SecurityProblemHandlerTest {

    @Test
    void returnsProblemDetailForForbiddenRequests() throws Exception {
        SecurityProblemHandler handler = new SecurityProblemHandler(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("internal detail"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString().contains("\"title\":\"Forbidden\""));
        assertTrue(response.getContentAsString().contains("\"status\":403"));
        assertTrue(response.getContentAsString().contains("\"instance\":\"/api/admin\""));
        assertFalse(response.getContentAsString().contains("internal detail"));
    }
}
