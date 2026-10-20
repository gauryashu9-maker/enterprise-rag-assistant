package com.axlero.ragassistant.security;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityProbeController {

    @GetMapping({
        "/api/auth/probe",
        "/v3/api-docs/probe",
        "/swagger-ui/probe",
        "/actuator/health"
    })
    public String publicEndpoint() {
        return "public";
    }

    @GetMapping("/api/private")
    public String privateEndpoint(@AuthenticationPrincipal AuthenticatedUser user) {
        return user.id() + ":" + user.email() + ":" + user.role();
    }

    @GetMapping("/api/admin-probe")
    public String adminEndpoint() {
        return "admin";
    }
}
