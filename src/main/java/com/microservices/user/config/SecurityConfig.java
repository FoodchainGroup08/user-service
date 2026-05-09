package com.microservices.user.config;

import com.microservices.user.filter.JwtAuthenticationFilter;
import com.microservices.user.filter.JwtAuthorizationFilter;
import com.microservices.user.oauth2.CustomOAuth2UserService;
import com.microservices.user.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthorizationFilter jwtAuthorizationFilter;
    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Value("${app.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints (paths are relative to context-path /api)
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/reset-password").permitAll()
                // OAuth2 server-side redirect flow
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Actuator
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Swagger UI
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // Role-based access
                .requestMatchers(HttpMethod.GET, "/users").hasRole("HEAD_OFFICE_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/users/*").hasAnyRole("HEAD_OFFICE_ADMIN", "BRANCH_MANAGER")
                // All other requests require authentication
                .anyRequest().authenticated()
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

    // Prevent Spring Boot from auto-registering these filters as raw servlet filters.
    // They must only run inside the Spring Security filter chain.
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
