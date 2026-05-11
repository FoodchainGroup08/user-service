package com.microservices.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.LoginRequest;
import com.microservices.user.entity.User;
import com.microservices.user.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Intercepts POST /auth/login. Validates email/password format via regex, then authenticates
 * credentials via AuthenticationManager and writes a JWT AuthResponse.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";

    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

    private static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()+=])[A-Za-z\\d@$!%*?&_\\-#^()+=]{8,}$";

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getServletPath().equals(LOGIN_PATH) && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LoginRequest loginRequest;
        try {
            loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (Exception e) {
            writeError(response, HttpStatus.BAD_REQUEST, "Malformed request body");
            return;
        }

        if (loginRequest.getEmail() == null || !loginRequest.getEmail().matches(EMAIL_REGEX)) {
            writeError(response, HttpStatus.BAD_REQUEST, "Email must be a valid address (e.g., user@example.com)");
            return;
        }

        if (loginRequest.getPassword() == null) {
            writeError(response, HttpStatus.BAD_REQUEST,
                    "your password can not be null");
            return;
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );

            User user = (User) authentication.getPrincipal();
            AuthResponse authResponse = authService.buildAuthResponse(user);

            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), authResponse);

        } catch (AuthenticationException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        // Do NOT call chain.doFilter — this filter fully handles the login response
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                Map.of("status", status.value(), "error", message));
    }
}
