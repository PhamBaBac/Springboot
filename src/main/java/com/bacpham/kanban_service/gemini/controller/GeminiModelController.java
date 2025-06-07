package com.bacpham.kanban_service.gemini.controller;


import com.bacpham.kanban_service.gemini.dto.GeminiRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.gemini.dto.GeminiImageRequest;
import com.bacpham.kanban_service.gemini.dto.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gemini")
public class GeminiModelController {
    private static final Logger log = LoggerFactory.getLogger(GeminiModelController.class);
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash-latest";
    @Value("${spring.ai.openai.api-key}")
    private String GEMINI_API_KEY;
    private final RestClient restClient;

    public GeminiModelController(RestClient.Builder builder) {
        log.info("Initializing GeminiModelController...");
        this.restClient = builder
                .baseUrl(GEMINI_API_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "Tell me a joke");

        GeminiRequest requestBody = GeminiRequest.fromText(message);

        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("key",  GEMINI_API_KEY)
                        .build())
                .body(requestBody)
                .retrieve()
                .toEntity(GeminiResponse.class);

        String reply = responseEntity.getBody() != null ?
                responseEntity.getBody().getFirstCandidateText().orElse("No response text found.") :
                "Failed to get response from API.";

        log.info("Gemini reply: {}", reply);

        return Map.of("response", reply);
    }

    @PostMapping(value = "/chat/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> chatWithImage(
            @RequestParam("prompt") String prompt,
            @RequestParam("image") MultipartFile imageFile) throws IOException {

        log.info("Gửi prompt '{}' và ảnh '{}' tới Gemini", prompt, imageFile.getOriginalFilename());

        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType();

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt)); // Phần text
        parts.add(Part.fromImage(mimeType, base64Image)); // Phần ảnh

        GeminiImageRequest requestBody = GeminiImageRequest.fromParts(parts);

        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("key", GEMINI_API_KEY)
                        .build())
                .body(requestBody)
                .retrieve()
                .toEntity(GeminiResponse.class);

        String reply = responseEntity.getBody() != null ?
                responseEntity.getBody().getFirstCandidateText().orElse("No response text found.") :
                "Failed to get response from API.";

        log.info("Gemini reply: {}", reply);
        return Map.of("response", reply);
    }


}
