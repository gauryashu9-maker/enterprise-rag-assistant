package com.axlero.ragassistant.security;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SecurityProblemHandler securityProblemHandler;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityProblemHandler securityProblemHandler) {
        this.jwtService = jwtService;
        this.securityProblemHandler = securityProblemHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(request, response);
            return;
        }

        String token = authorization.substring(7);
        if (token.isBlank()) {
            reject(request, response);
            return;
        }

        AuthenticatedUser principal;
        try {
            principal = jwtService.parseToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            reject(request, response);
            return;
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        securityProblemHandler.commence(
                request, response, new BadCredentialsException("Invalid bearer token"));
    }
}
