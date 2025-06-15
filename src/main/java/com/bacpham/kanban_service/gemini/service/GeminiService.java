package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.gemini.dto.GeminiImageRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.gemini.dto.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class GeminiService {

    private final RestClient restClient;
    private final String geminiApiKey;
    private static final String DEFAULT_MODEL = "gemini-1.5-flash-latest";

    public GeminiService(@Qualifier("geminiRestClient") RestClient restClient,
                         @Value("${spring.ai.openai.api-key}") String geminiApiKey) {
        this.restClient = restClient;
        this.geminiApiKey = geminiApiKey;
    }
    public String generateContent(String textPrompt) {
        log.info("Sending text prompt to Gemini: '{}'", textPrompt);
        GeminiRequest requestBody = GeminiRequest.fromText(textPrompt);
        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);

        String reply = extractTextFromResponse(responseEntity);
        log.info("Gemini reply: {}", reply);
        return reply;
    }


    public String generateContent(String textPrompt, MultipartFile imageFile) throws IOException {
        log.info("Sending prompt '{}' and image '{}' to Gemini", textPrompt, imageFile.getOriginalFilename());

        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType();

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(textPrompt));
        parts.add(Part.fromImage(mimeType, base64Image));

        GeminiImageRequest requestBody = GeminiImageRequest.fromParts(parts);
        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);

        String reply = extractTextFromResponse(responseEntity);
        log.info("Gemini multimodal reply: {}", reply);
        return reply;
    }
    private ResponseEntity<GeminiResponse> sendRequestToGemini(String url, Object requestBody) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("key", geminiApiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toEntity(GeminiResponse.class);
    }

    private String extractTextFromResponse(ResponseEntity<GeminiResponse> responseEntity) {
        return responseEntity.getBody() != null ?
                responseEntity.getBody().getFirstCandidateText().orElse("No response text found.") :
                "Failed to get response from API.";
    }
}