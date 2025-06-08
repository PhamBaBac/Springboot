package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.gemini.dto.GeminiRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.UserActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecommendationService {

    private final UserActivityRepository userActivityRepository;
    private final ProductRepository productRepository;
    private final RestClient restClient;

    @Value("${spring.ai.openai.api-key}")
    private String GEMINI_API_KEY;

    public RecommendationService(
            @Qualifier("geminiRestClient") RestClient restClient,

            UserActivityRepository userActivityRepository,
            ProductRepository productRepository
    ) {
        this.userActivityRepository = userActivityRepository;
        this.productRepository = productRepository;
        this.restClient = restClient;
    }

    public String getRecommendationsForUser(User user) {
        List<UserActivity> activities = userActivityRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        if (activities.isEmpty()) {
            log.warn("No activities found for user: {}. Cannot generate recommendations.", user.getEmail());
            return "[]";
        }

        String prompt = buildSmartPrompt(activities);
        log.info("Constructed Gemini Prompt for user {}: {}", user.getEmail(), prompt);

        GeminiRequest requestBody = GeminiRequest.fromText(prompt);
        String geminiEndpoint = "/v1beta/models/gemini-1.5-flash-latest:generateContent";

        ResponseEntity<GeminiResponse> responseEntity = restClient.post()
                .uri(uriBuilder -> uriBuilder.path(geminiEndpoint).queryParam("key", GEMINI_API_KEY).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toEntity(GeminiResponse.class);

        String rawResponse = responseEntity.getBody() != null ?
                responseEntity.getBody().getFirstCandidateText().orElse("[]") :
                "[]";

        log.info("Gemini raw recommendation response: {}", rawResponse);

        // BƯỚC MỚI: DỌN DẸP VÀ TRÍCH XUẤT JSON
        String cleanedJson = extractJsonArrayString(rawResponse);
        log.info("Cleaned JSON response: {}", cleanedJson);

        return cleanedJson;
    }
    private String extractJsonArrayString(String rawText) {
        // Biểu thức chính quy để tìm chuỗi bắt đầu bằng '[' và kết thúc bằng ']'
        Pattern pattern = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        if (matcher.find()) {
            return matcher.group(0); // Trả về chuỗi JSON array đầu tiên tìm thấy
        }

        return "[]"; // Trả về mảng rỗng nếu không tìm thấy
    }


    private String buildSmartPrompt(List<UserActivity> activities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một chuyên gia tư vấn bán hàng. Dựa trên các sản phẩm người dùng đã xem gần đây, hãy gợi ý ít hơn hoặc bằng 5 sản phẩm không trùng nhau gần đây nhất mà họ có thể thích.\n");
        prompt.append("**Lịch sử xem sản phẩm của người dùng:**\n");

        List<String> viewedProductIds = activities.stream()
                .filter(a -> a.getActionType() == ActionType.VIEW_PRODUCT && a.getEntityId() != null)
                .map(UserActivity::getEntityId)
                .distinct()
                .toList();

        if (viewedProductIds.isEmpty()) {
            prompt.append("Người dùng chưa xem sản phẩm nào cụ thể.\n");
        } else {
            List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);
            for (Product p : viewedProducts) {
                prompt.append(String.format("- Tên: \"%s\". Thuộc danh mục: %s. Nhà cung cấp: %s.\n",
                        p.getTitle(),
                        p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")),
                        p.getSupplier().getName()));
            }
        }

        prompt.append("\n**Các sản phẩm có trong cửa hàng để bạn gợi ý (chỉ chọn từ danh sách này):**\n");
        List<Product> candidateProducts = findCandidateProducts(viewedProductIds);

        if (candidateProducts.isEmpty()) {
            prompt.append("Hiện không có sản phẩm nào phù hợp để gợi ý. Dựa vào lịch sử xem hàng, hãy đưa ra mô tả về phong cách người dùng có thể thích.\n");
        } else {
            for (Product p : candidateProducts) {
                prompt.append(String.format("- ID: \"%s\", Tên: \"%s\"\n", p.getId(), p.getTitle()));
            }
        }

        prompt.append("\n**YÊU CẦU QUAN TRỌNG:** Chỉ trả lời bằng một mảng JSON chứa ID của sản phẩm được gợi ý. Nếu không thể gợi ý sản phẩm, trả về một mảng rỗng `[]`.");

        return prompt.toString();
    }

    private List<Product> findCandidateProducts(List<String> viewedProductIds) {
        if (!viewedProductIds.isEmpty()) {
            List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);
            Set<Category> viewedCategories = viewedProducts.stream()
                    .flatMap(p -> p.getCategories().stream())
                    .collect(Collectors.toSet());

            if (!viewedCategories.isEmpty()) {
                List<Product> candidates = productRepository.findTop20ByCategoriesInAndIdNotIn(viewedCategories, viewedProductIds);
                if (!candidates.isEmpty()) {
                    return candidates;
                }
            }
            return productRepository.findTop20ByIdNotInOrderByCreatedAtDesc(viewedProductIds);
        }
        return productRepository.findTop20ByOrderByCreatedAtDesc();
    }
}