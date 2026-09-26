package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.gemini.dto.GeminiImageRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.gemini.dto.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewModerationService {

    private final RestClient restClient;

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    public boolean isReviewApproved(String comment, List<String> base64Images) {
        List<Part> parts = new ArrayList<>();

        if (comment != null && !comment.isBlank()) {
            parts.add(Part.fromText(buildModerationPrompt(comment)));
        }

        if (base64Images != null) {
            for (String base64 : base64Images) {
                parts.add(Part.fromImage("image/jpeg", base64));
            }
        }

        GeminiImageRequest request = GeminiImageRequest.fromParts(parts);
        String result = callGemini(request);
        return "APPROVED".equals(result);
    }

    private String callGemini(GeminiImageRequest request) {
        List<String> modelsToTry = List.of("gemini-2.5-flash", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-flash-latest");
        for (String currentModel : modelsToTry) {
            try {
                String url = "/v1beta/models/%s:generateContent".formatted(currentModel);
                ResponseEntity<GeminiResponse> response = restClient.post()
                        .uri(uriBuilder -> uriBuilder.path(url).queryParam("key", geminiApiKey).build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toEntity(GeminiResponse.class);

                String result = Optional.ofNullable(response.getBody())
                        .flatMap(GeminiResponse::getFirstCandidateText)
                        .orElse(null);

                if (result != null && !result.isBlank()) {
                    return result.trim().toUpperCase();
                }
            } catch (Exception e) {
                log.warn("Review moderation with model [{}] failed: {}. Trying next...", currentModel, e.getMessage());
            }
        }
        return "APPROVED";
    }

    private String buildModerationPrompt(String content) {
        return """
        Bạn là một hệ thống kiểm duyệt nội dung người dùng. Dưới đây là nội dung đánh giá sản phẩm của người dùng:

        "%s"

        Yêu cầu kiểm duyệt:
        - Nếu nội dung chứa từ ngữ **không phù hợp**, **phân biệt vùng miền**, **xúc phạm**, **tục tĩu**, **khiêu dâm**, **kích động bạo lực**, hoặc mang tính **chính trị nhạy cảm** → Trả về: "REJECTED"
        - Nếu nội dung chứa **liên kết (link)** đến website khác, **spam**, **quảng cáo**, hoặc cố tình **dẫn dụ người dùng** ra ngoài → Trả về: "REJECTED"
        - Nếu nội dung bình thường, tích cực hoặc trung lập → Trả về: "APPROVED"

        Chỉ trả về một từ duy nhất: APPROVED hoặc REJECTED.
        """.formatted(content);
    }
}
