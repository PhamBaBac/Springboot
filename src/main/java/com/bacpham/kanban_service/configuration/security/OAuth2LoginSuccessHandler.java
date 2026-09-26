package com.bacpham.kanban_service.configuration.security;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.Provider;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.UserCacheService;
import com.bacpham.kanban_service.service.impl.AuthenticationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final GenericRedisService<String, String, String> redisService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserCacheService userCacheService;

    @Value("${application.oauth2.authorized-redirect-uri}")
    private String authorizedRedirectUri;

    @Value("${application.cookie.secure:false}")
    private boolean isCookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        Map<String, Object> attributes = oauth2User.getAttributes();
        Provider provider = Provider.valueOf(registrationId.toUpperCase());

        String email = (String) attributes.get("email");
        String providerId = "";
        String firstName = "";
        String lastName = "";
        String avatarUrl = (String) attributes.get("avatar_url");

        switch (provider) {
            case GOOGLE -> {
                providerId = (String) attributes.get("sub");
                firstName = (String) attributes.get("given_name");
                lastName = (String) attributes.get("family_name");
                if ((firstName == null || firstName.isBlank()) && attributes.get("name") != null) {
                    String fullName = (String) attributes.get("name");
                    int spaceIdx = fullName.lastIndexOf(" ");
                    if (spaceIdx > 0) {
                        firstName = fullName.substring(0, spaceIdx);
                        lastName = fullName.substring(spaceIdx + 1);
                    } else {
                        firstName = fullName;
                        lastName = "";
                    }
                }
                avatarUrl = (String) attributes.get("picture");
            }
            case GITHUB -> {
                providerId = String.valueOf(attributes.get("id"));
                String name = (String) attributes.get("login");
                if (name != null && name.contains(" ")) {
                    firstName = name.split(" ")[0];
                    lastName = name.substring(name.indexOf(" ") + 1);
                } else {
                    firstName = name != null ? name : "GitHub";
                    lastName = "";
                }
                if (email == null || email.isBlank()) {
                    email = "no-email-" + providerId + "@github.local";
                }
            }
            default -> throw new IllegalStateException("Unsupported provider: " + provider);
        }

        final String finalEmail = email;
        final String finalFirstName = firstName;
        final String finalLastName = lastName;
        final String finalProviderId = providerId;
        final Provider finalProvider = provider;
        final String finalAvatarUrl = avatarUrl;

        User user = userRepository.findByEmail(finalEmail).map(existingUser -> {
            boolean updated = false;
            if (existingUser.getProvider() == null) {
                existingUser.setProvider(finalProvider);
                updated = true;
            }
            if (existingUser.getProviderId() == null || existingUser.getProviderId().isBlank()) {
                existingUser.setProviderId(finalProviderId);
                updated = true;
            }
            if (finalFirstName != null && !finalFirstName.isBlank() && (existingUser.getFirstname() == null || existingUser.getFirstname().isBlank())) {
                existingUser.setFirstname(finalFirstName);
                updated = true;
            }
            if (finalLastName != null && !finalLastName.isBlank() && (existingUser.getLastname() == null || existingUser.getLastname().isBlank())) {
                existingUser.setLastname(finalLastName);
                updated = true;
            }
            if (finalAvatarUrl != null && !finalAvatarUrl.isBlank()) {
                if (existingUser.getAvatarUrl() == null 
                        || existingUser.getAvatarUrl().isBlank() 
                        || existingUser.getAvatarUrl().contains("googleusercontent.com")
                        || existingUser.getAvatarUrl().contains("githubusercontent.com")) {
                    existingUser.setAvatarUrl(finalAvatarUrl);
                    updated = true;
                }
            }
            if (updated) {
                userCacheService.evictUser(finalEmail);
                return userRepository.save(existingUser);
            }
            return existingUser;
        }).orElseGet(() -> {
            log.info("Creating new user with email: {}", finalEmail);
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setFirstname(finalFirstName != null && !finalFirstName.isBlank() ? finalFirstName : "User");
            newUser.setLastname(finalLastName != null ? finalLastName : "");
            newUser.setRole(Role.USER);
            newUser.setMfaEnabled(false);
            newUser.setProvider(finalProvider);
            newUser.setProviderId(finalProviderId);
            newUser.setAvatarUrl(finalAvatarUrl);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepository.save(newUser);
        });
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String cookieName = AuthenticationServiceImpl.getRefreshTokenCookieName(user.getRole());
        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        ResponseCookie legacyCookie = ResponseCookie.from(AuthenticationServiceImpl.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, legacyCookie.toString());

        String exchangeCode = UUID.randomUUID().toString();
        AuthenticationResponse authResponse = AuthenticationResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .mfaEnabled(user.isMfaEnabled())
                .build();

        redisService.set("oauth2:code:" + exchangeCode, objectMapper.writeValueAsString(authResponse));
        redisService.setTimeToLive("oauth2:code:" + exchangeCode, 60, TimeUnit.SECONDS);

        String redirectUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("code", exchangeCode)
                .build().toUriString();

        log.info("Redirecting to frontend with exchange code for user: {}", user.getEmail());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}