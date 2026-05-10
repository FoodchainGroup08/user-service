package com.microservices.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.entity.User;
import com.microservices.user.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runs after JwtAuthenticationFilter. Extracts Bearer token from Authorization header,
 * validates it, and populates the SecurityContext.
 * <p>
 * When there is no Bearer token (e.g. API gateway validated JWT and forwards only
 * {@code X-User-Id} / {@code X-User-Role}), builds an authenticated principal from those
 * headers so Spring Security and {@code @PreAuthorize} see the correct {@code ROLE_*} authorities.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            authenticateFromGatewayHeaders(request);
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            UserDetails userDetails = jwtService.validateAccessToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    Map.of("error", "Invalid or expired token", "status", 401));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Trust headers set by the API gateway after it validates the JWT (gateway removes Authorization).
     */
    private void authenticateFromGatewayHeaders(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");
        if (userId == null || userId.isBlank() || roleHeader == null || roleHeader.isBlank()) {
            return;
        }
        User.Role roleEnum;
        try {
            roleEnum = User.Role.fromDisplayName(roleHeader.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleEnum.name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
