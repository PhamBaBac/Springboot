// src/main/java/com/bacpham/kanban_service/gemini/controller/GeminiModelController.java
package com.bacpham.kanban_service.gemini.controller;

import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.gemini.service.GeminiService;
import com.bacpham.kanban_service.gemini.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class GeminiModelController {

    private final GeminiService geminiService;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "Tell me a joke");

        String reply = geminiService.generateContent(message);

        return Map.of("response", reply);
    }

    @PostMapping(value = "/chat/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> chatWithImage(
            @RequestParam("prompt") String prompt,
            @RequestParam("image") MultipartFile imageFile) throws IOException {

        String reply = geminiService.generateContent(prompt, imageFile);

        return Map.of("response", reply);
    }

    @GetMapping("/recommendations")
    public Map<String, Object> getRecommendations(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return Map.of("error", "User not logged in");
        }
        String recommendationJsonString = recommendationService.getRecommendationsForUser(currentUser);

        try {
            List<String> recommendationList = objectMapper.readValue(recommendationJsonString, new TypeReference<List<String>>() {});
            return Map.of("recommendations", recommendationList);
        } catch (JsonProcessingException e) {
            return Map.of("recommendations", Collections.emptyList(), "error", "Failed to parse recommendations");
        }
    }
}