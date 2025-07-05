package com.bacpham.kanban_service.configuration.security;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.Provider;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.repository.UserRepository;
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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final GenericRedisService<String, String, String> redisService;
    private final ObjectMapper objectMapper;


    @Value("${application.oauth2.authorized-redirect-uri}")
    private String authorizedRedirectUri;


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
                    email = "no-email-" + providerId + "@github.com";
                }
            }
            default -> throw new IllegalStateException("Unsupported provider: " + provider);
        }

        // Make variables final for lambda
        final String finalEmail = email;
        final String finalFirstName = firstName;
        final String finalLastName = lastName;
        final String finalProviderId = providerId;
        final Provider finalProvider = provider;
        final String finalAvatarUrl = avatarUrl;


        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setFirstname(finalFirstName);
            newUser.setLastname(finalLastName);
            newUser.setRole(Role.USER);
            newUser.setMfaEnabled(false);
            newUser.setProvider(finalProvider);
            newUser.setProviderId(finalProviderId);
            newUser.setAvatarUrl(finalAvatarUrl);
            return userRepository.save(newUser);
        });


        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        redisService.set("accessToken:" + user.getId(), accessToken);
        redisService.setTimeToLive("accessToken:" + user.getId(), 2, TimeUnit.MINUTES);
        redisService.set("refreshToken:" + user.getId(), refreshToken);
        redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        String redirectUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("accessToken", accessToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
