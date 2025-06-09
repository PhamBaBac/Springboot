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
import org.springframework.data.domain.Pageable;
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
            return "[]";
        }

        String prompt = buildSmartPrompt(activities);

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

        String cleanedJson = extractJsonArrayString(rawResponse);

        return cleanedJson;
    }
    private String extractJsonArrayString(String rawText) {

        Pattern pattern = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        if (matcher.find()) {
            return matcher.group(0);
        }

        return "[]";
    }


    private String buildSmartPrompt(List<UserActivity> activities) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
        Bạn là một chuyên gia tư vấn bán hàng thông minh. Nhiệm vụ của bạn là phân tích hành vi người dùng để đề xuất sản phẩm mà họ có thể thích, dựa vào lịch sử xem sản phẩm gần đây.

        - Phân tích xu hướng từ các sản phẩm người dùng đã xem.
        - Ưu tiên gợi ý các sản phẩm cùng danh mục với những sản phẩm đã xem.
        - Chỉ được chọn sản phẩm trong danh sách ứng viên mà tôi cung cấp bên dưới.
        - Trả về mảng JSON chứa các ID sản phẩm (dưới dạng chuỗi), tối đa 1 5 sản phẩm. Nếu không gợi ý được gì thì trả về: []

        """);

        prompt.append("### Lịch sử xem sản phẩm của người dùng:\n");

        List<String> viewedProductIds = activities.stream()
                .filter(a -> a.getActionType() == ActionType.VIEW_PRODUCT && a.getEntityId() != null)
                .map(UserActivity::getEntityId)
                .distinct()
                .toList();

        if (viewedProductIds.isEmpty()) {
            prompt.append("- Người dùng chưa xem sản phẩm nào cụ thể.\n");
        } else {
            List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);
            for (Product p : viewedProducts) {
                prompt.append(String.format(
                        "- Tên: \"%s\"\n  Danh mục: %s\n  Nhà cung cấp: %s\n\n",
                        p.getTitle(),
                        p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")),
                        p.getSupplier().getName()));
            }
        }

        prompt.append("### Danh sách sản phẩm có sẵn để bạn lựa chọn:\n");

        List<Product> candidateProducts = findCandidateProducts(viewedProductIds);

        if (candidateProducts.isEmpty()) {
            prompt.append("- Không có sản phẩm ứng viên cụ thể.\n");
        } else {
            for (Product p : candidateProducts) {
                prompt.append(String.format(
                        "- ID: \"%s\"\n  Tên: \"%s\"\n  Danh mục: %s\n\n",
                        p.getId(),
                        p.getTitle(),
                        p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", "))));
            }
        }

        prompt.append("""
        ### QUY ĐỊNH:
        - Chỉ trả về kết quả dưới dạng: ["product_id_1", "product_id_2", ...]
        - Không viết thêm mô tả nào khác ngoài mảng JSON.
        - Mỗi phần tử là một ID sản phẩm từ danh sách ứng viên.

        Bây giờ, hãy trả về danh sách ID của các sản phẩm được đề xuất.
        """);

        return prompt.toString();
    }


    private List<Product> findCandidateProducts(List<String> viewedProductIds) {
        if (viewedProductIds == null || viewedProductIds.isEmpty()) {
            return List.of(); // Trả về danh sách rỗng nếu không có sản phẩm đã xem
        }

        // Lấy danh sách sản phẩm đã xem từ DB
        List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);

        // Lấy tập các category của sản phẩm đã xem
        Set<Category> categories = viewedProducts.stream()
                .flatMap(p -> p.getCategories().stream())
                .collect(Collectors.toSet());

        if (categories.isEmpty()) {
            return List.of(); // Nếu không có category, trả về rỗng
        }

        // Tạo pageable giới hạn số lượng kết quả (ví dụ 20)
        Pageable pageable = Pageable.ofSize(20);

        // Gọi repo tìm sản phẩm ứng viên
        List<Product> candidates = productRepository.findCandidateProducts(categories, viewedProductIds, pageable);

        return candidates;
    }


}