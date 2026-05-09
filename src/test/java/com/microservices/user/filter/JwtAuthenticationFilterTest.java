package com.microservices.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microservices.user.dto.AuthResponse;
import com.microservices.user.dto.LoginRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
import com.microservices.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private AuthService authService;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new JwtAuthenticationFilter(authenticationManager, authService, objectMapper);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(User.Role.CUSTOMER)
                .build();
    }

    // ---- shouldNotFilter ----

    @Test
    @DisplayName("shouldNotFilter: returns false for POST /auth/login (filter must run)")
    void shouldNotFilter_returnsFalse_forLoginPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter: returns true for GET /auth/login (skip filter)")
    void shouldNotFilter_returnsTrue_forLoginGet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        request.setServletPath("/auth/login");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter: returns true for POST /auth/register (skip filter)")
    void shouldNotFilter_returnsTrue_forRegisterPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setServletPath("/auth/register");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter: returns true for GET /users/me (skip filter)")
    void shouldNotFilter_returnsTrue_forOtherPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.setServletPath("/users/me");

        assertTrue(filter.shouldNotFilter(request));
    }

    // ---- doFilterInternal ----

    @Test
    @DisplayName("doFilterInternal: returns 200 and AuthResponse JSON for valid credentials")
    void doFilterInternal_returns200WithAuthResponse_forValidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@example.com");
        loginRequest.setPassword("password123");

        Authentication successAuth = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(successAuth);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-abc")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(UserResponse.from(testUser))
                .build();
        when(authService.buildAuthResponse(testUser)).thenReturn(authResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setContentType("application/json");
        request.setContent(objectMapper.writeValueAsBytes(loginRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getContentType());

        AuthResponse parsed = objectMapper.readValue(response.getContentAsString(), AuthResponse.class);
        assertEquals("access-token-123", parsed.getAccessToken());
        assertEquals("Bearer", parsed.getTokenType());
    }

    @Test
    @DisplayName("doFilterInternal: returns 401 and error JSON for invalid credentials")
    void doFilterInternal_returns401_forInvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@example.com");
        loginRequest.setPassword("wrong-password");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setContentType("application/json");
        request.setContent(objectMapper.writeValueAsBytes(loginRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("doFilterInternal: does not call filterChain.doFilter on success (response is terminal)")
    void doFilterInternal_doesNotCallChain_onSuccessfulLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@example.com");
        loginRequest.setPassword("password123");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(authService.buildAuthResponse(testUser)).thenReturn(
                AuthResponse.builder().accessToken("t").refreshToken("r").user(UserResponse.from(testUser)).build()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setContentType("application/json");
        request.setContent(objectMapper.writeValueAsBytes(loginRequest));

        MockFilterChain chain = mock(MockFilterChain.class);
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        // The filter must NOT continue the chain — it owns the login response
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal: authenticationManager receives email and password from body")
    void doFilterInternal_passesEmailAndPasswordToAuthManager() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("exact@example.com");
        loginRequest.setPassword("exact-password");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("simulate failure to avoid NPE"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setContentType("application/json");
        request.setContent(objectMapper.writeValueAsBytes(loginRequest));

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(authenticationManager).authenticate(argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken &&
                "exact@example.com".equals(token.getPrincipal()) &&
                "exact-password".equals(token.getCredentials())
        ));
    }
}
