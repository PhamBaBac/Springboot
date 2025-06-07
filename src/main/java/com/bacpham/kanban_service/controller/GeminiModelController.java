package com.bacpham.kanban_service.controller;


import com.bacpham.kanban_service.dto.request.GeminiRequest;
import com.bacpham.kanban_service.dto.response.GeminiResponse;
import com.bacpham.kanban_service.dto.response.ModelListResponse;
import com.bacpham.kanban_service.entity.GeminiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

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

    /*
        curl https://generativelanguage.googleapis.com/v1beta/openai/models \                                                                                                                                                                                                  ✔  10s   base 
        -H "Authorization: Bearer GEMINI_API_KEY"
     */
    @GetMapping("/models")
    public List<GeminiModel> models() {
        ResponseEntity<ModelListResponse> response = restClient.get()
                .uri("/v1beta/openai/models")
                .header("Authorization","Bearer " + GEMINI_API_KEY)
                .retrieve()
                .toEntity(ModelListResponse.class);
        return response.getBody().data();
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


}
