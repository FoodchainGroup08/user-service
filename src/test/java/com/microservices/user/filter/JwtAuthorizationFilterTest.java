package com.microservices.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.entity.User;
import com.microservices.user.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthorizationFilter Unit Tests")
class JwtAuthorizationFilterTest {

    @Mock private JwtService jwtService;

    private JwtAuthorizationFilter filter;
    private ObjectMapper objectMapper;
    private UserDetails mockPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new JwtAuthorizationFilter(jwtService, objectMapper);
        SecurityContextHolder.clearContext();

        mockPrincipal = org.springframework.security.core.userdetails.User.builder()
                .username(UUID.randomUUID().toString())
                .password("")
                .roles("CUSTOMER")
                .build();
    }

    // ---- No Authorization header ----

    @Test
    @DisplayName("doFilterInternal: passes through when Authorization header is absent")
    void doFilterInternal_passesThrough_whenNoAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("doFilterInternal: passes through when Authorization header is not Bearer")
    void doFilterInternal_passesThrough_whenNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    // ---- Valid token ----

    @Test
    @DisplayName("doFilterInternal: sets Authentication in SecurityContext for a valid Bearer token")
    void doFilterInternal_setsAuthentication_forValidToken() throws Exception {
        when(jwtService.validateAccessToken("valid-jwt")).thenReturn(mockPrincipal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Bearer valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(mockPrincipal, auth.getPrincipal());
        assertTrue(auth.isAuthenticated());
    }

    @Test
    @DisplayName("doFilterInternal: extracted token is passed to JwtService without the 'Bearer ' prefix")
    void doFilterInternal_stripsBearer_beforeValidation() throws Exception {
        when(jwtService.validateAccessToken("raw-jwt-token")).thenReturn(mockPrincipal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Bearer raw-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        verify(jwtService).validateAccessToken("raw-jwt-token");
    }

    // ---- Invalid token ----

    @Test
    @DisplayName("doFilterInternal: returns 401 and does not continue chain for an invalid token")
    void doFilterInternal_returns401_forInvalidToken() throws Exception {
        when(jwtService.validateAccessToken("bad-token"))
                .thenThrow(new JwtException("Invalid signature"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("doFilterInternal: 401 response body contains error JSON")
    void doFilterInternal_returnsErrorJson_forInvalidToken() throws Exception {
        when(jwtService.validateAccessToken(anyString()))
                .thenThrow(new JwtException("Token expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String body = response.getContentAsString();
        assertTrue(body.contains("error") || body.contains("401"),
                "Response body should contain error information");
    }

    @Test
    @DisplayName("doFilterInternal: SecurityContext is not polluted after a failed validation")
    void doFilterInternal_securityContextEmpty_afterFailedValidation() throws Exception {
        when(jwtService.validateAccessToken(anyString()))
                .thenThrow(new JwtException("Revoked"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader("Authorization", "Bearer revoked-token");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
