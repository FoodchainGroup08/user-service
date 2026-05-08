package com.microservices.user.oauth2;

import com.microservices.user.entity.User;
import com.microservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Loads or creates a local User record on OAuth2 login.
 * Activated only when app.oauth2.enabled=true via conditional wiring in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getName();
        String email = extractEmail(oAuth2User, provider);

        Optional<User> existing = userRepository.findByOauth2ProviderAndOauth2ProviderId(provider, providerId);
        if (existing.isPresent()) {
            return new OAuth2UserPrincipal(existing.get(), oAuth2User.getAttributes());
        }

        // Create new user from OAuth2 profile
        User newUser = User.builder()
                .email(email)
                .role(User.Role.CUSTOMER)
                .oauth2Provider(provider)
                .oauth2ProviderId(providerId)
                .build();

        // Check if email already exists (user signed up with password before)
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setOauth2Provider(provider);
            u.setOauth2ProviderId(providerId);
            userRepository.save(u);
        });

        if (!userRepository.existsByEmail(email)) {
            newUser = userRepository.save(newUser);
        } else {
            newUser = userRepository.findByEmail(email).orElse(newUser);
        }

        return new OAuth2UserPrincipal(newUser, oAuth2User.getAttributes());
    }

    private String extractEmail(OAuth2User oAuth2User, String provider) {
        if ("github".equals(provider)) {
            Object email = oAuth2User.getAttributes().get("email");
            return email != null ? email.toString() : oAuth2User.getName() + "@github.oauth";
        }
        Object email = oAuth2User.getAttributes().get("email");
        return email != null ? email.toString() : "";
    }
}
