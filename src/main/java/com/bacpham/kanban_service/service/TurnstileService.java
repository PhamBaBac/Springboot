package com.bacpham.kanban_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TurnstileService {

    private final RestTemplate restTemplate;

    @Value("${application.security.turnstile.secret-key:1x0000000000000000000000000000000AA}")
    private String secretKey;

    @Value("${application.security.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    @Value("${application.security.turnstile.enabled:true}")
    private boolean enabled;

    /**
     * Xác thực token Cloudflare Turnstile gửi lên từ Client.
     *
     * @param token    chuỗi token được sinh ra từ widget Turnstile phía Frontend
     * @param clientIp IP của client (tùy chọn)
     * @return true nếu xác thực thành công hoặc tính năng đang tắt
     */
    public boolean verify(String token, String clientIp) {
        if (!enabled) {
            log.debug("Turnstile verification skipped: disabled in config");
            return true;
        }

        if (token == null || token.isBlank()) {
            log.warn("Turnstile verification failed: missing token");
            return false;
        }

        // Hỗ trợ token test khi dev/test local
        if ("test-pass".equalsIgnoreCase(token) || "dummy-token".equalsIgnoreCase(token)) {
            log.info("Turnstile verification passed with test token");
            return true;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);
            body.add("response", token.trim());
            if (clientIp != null && !clientIp.isBlank() && !"unknown".equals(clientIp)) {
                body.add("remoteip", clientIp.trim());
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            Map<?, ?> response = restTemplate.postForObject(verifyUrl, request, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Turnstile captcha verification successful");
                return true;
            }

            log.warn("Turnstile captcha verification rejected: response={}", response);
            return false;
        } catch (Exception e) {
            log.error("Error during Turnstile captcha verification: {}", e.getMessage());
            return false;
        }
    }
}
