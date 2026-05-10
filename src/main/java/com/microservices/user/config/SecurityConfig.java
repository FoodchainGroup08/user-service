package com.microservices.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.filter.JwtAuthenticationFilter;
import com.microservices.user.filter.JwtAuthorizationFilter;
import com.microservices.user.oauth2.CustomOAuth2UserService;
import com.microservices.user.oauth2.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthorizationFilter jwtAuthorizationFilter;
    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final ObjectMapper objectMapper;

    @Value("${app.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // CORS preflight from browsers / Swagger UI — without this, OPTIONS returns 403 before POST runs.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/auth/verify-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/auth/resend-verification").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/users").hasRole("HEAD_OFFICE_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/v1/users/*").hasAnyRole("HEAD_OFFICE_ADMIN", "BRANCH_MANAGER")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    writeError(req, res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                        "Authentication required — provide a valid Bearer token"))
                .accessDeniedHandler((req, res, e) ->
                    writeError(req, res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                        "You do not have permission to access this resource"))
            )
            .addFilterAt(jwtAuthenticationFilter, BasicAuthenticationFilter.class)
            .addFilterAfter(jwtAuthorizationFilter, BasicAuthenticationFilter.class);

        if (oauth2Enabled) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(e -> e.userService(oAuth2UserService))
                    .successHandler(oAuth2SuccessHandler)
            );
        }

        return http.build();
    }

    private void writeError(HttpServletRequest req, HttpServletResponse res,
                            int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getWriter(), Map.of(
                "status", status,
                "error", error,
                "message", message,
                "path", req.getRequestURI(),
                "timestamp", Instant.now().toString()
        ));
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterReg(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthorizationFilter> jwtAuthzFilterReg(JwtAuthorizationFilter filter) {
        FilterRegistrationBean<JwtAuthorizationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
