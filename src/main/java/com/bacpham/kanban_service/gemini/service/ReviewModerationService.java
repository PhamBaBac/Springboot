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

    private static final String MODEL_ENDPOINT = "/v1beta/models/gemini-1.5-flash-latest:generateContent";

    public boolean isReviewApproved(String comment, List<String> base64Images) {
        List<Part> parts = new ArrayList<>();

        if (comment != null && !comment.isBlank()) {
            parts.add(Part.fromText(buildModerationPrompt(comment)));
        }

        if (base64Images != null) {
            for (String base64 : base64Images) {
                parts.add(Part.fromImage("image/jpeg", base64)); // bạn có thể thay đổi thành image/png nếu cần
            }
        }

        GeminiImageRequest request = GeminiImageRequest.fromParts(parts);
        String result = callGemini(request);
        return "APPROVED".equals(result);
    }

    private String callGemini(GeminiImageRequest request) {
        try {
            ResponseEntity<GeminiResponse> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path(MODEL_ENDPOINT).queryParam("key", geminiApiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(GeminiResponse.class);

            return Optional.ofNullable(response.getBody())
                    .flatMap(GeminiResponse::getFirstCandidateText)
                    .orElse("REJECTED")
                    .trim()
                    .toUpperCase();

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return "REJECTED";
        }
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
