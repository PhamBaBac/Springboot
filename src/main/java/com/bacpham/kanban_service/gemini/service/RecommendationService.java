package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.gemini.dto.GeminiRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.UserActivityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class RecommendationService {

    private final UserActivityRepository userActivityRepository;
    private final ProductRepository productRepository;
    private final RestClient restClient;
    private final ProductMapper productMapper;

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    private String callGeminiWithFallback(String prompt) {
        List<String> modelsToTry = List.of("gemini-2.5-flash", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-flash-latest");
        GeminiRequest request = GeminiRequest.fromText(prompt);

        for (String currentModel : modelsToTry) {
            try {
                String url = "/v1beta/models/%s:generateContent".formatted(currentModel);
                ResponseEntity<GeminiResponse> response = restClient.post()
                        .uri(uriBuilder -> uriBuilder.path(url).queryParam("key", geminiApiKey).build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toEntity(GeminiResponse.class);

                String raw = Optional.ofNullable(response.getBody())
                        .flatMap(GeminiResponse::getFirstCandidateText)
                        .orElse(null);

                if (raw != null && !raw.isBlank()) {
                    log.info("Gemini recommendation response from model [{}]: {}", currentModel, raw);
                    return raw;
                }
            } catch (Exception e) {
                log.warn("Recommendation AI model [{}] failed ({}). Trying next...", currentModel, e.getMessage());
            }
        }
        return "[]";
    }

    private boolean isPetRelated(Product product) {
        if (product == null) return false;
        String text = (product.getTitle() + " " + (product.getDescription() != null ? product.getDescription() : "")).toLowerCase();
        if (product.getCategories() != null) {
            for (Category c : product.getCategories()) {
                if (c.getTitle() != null) text += " " + c.getTitle().toLowerCase();
            }
        }
        return text.contains("pet") || text.contains("dog") || text.contains("cat")
                || text.contains("chó") || text.contains("mèo") || text.contains("thú cưng")
                || text.contains("cún") || text.contains("labrador") || text.contains("retriever")
                || text.contains("samoyed") || text.contains("alaska") || text.contains("puppy");
    }

    public RecommendationService(
            @Qualifier("geminiRestClient") RestClient restClient,
            UserActivityRepository userActivityRepository,
            ProductRepository productRepository,
            ProductMapper productMapper
    ) {
        this.userActivityRepository = userActivityRepository;
        this.productRepository = productRepository;
        this.restClient = restClient;
        this.productMapper = productMapper;
    }

    public String getRecommendationsForUser(User user) {
        List<UserActivity> activities = userActivityRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        if (activities.isEmpty()) {
            return "[]";
        }

        String prompt = buildPromptFromActivities(activities);
        String raw = callGeminiWithFallback(prompt);
        return extractJsonArrayString(raw);
    }

    private String buildPromptFromActivities(List<UserActivity> activities) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
        You are an intelligent e-commerce recommendation system.
        Your task is to analyze user browsing activity to recommend products they are most likely to purchase.

        - Analyze recent product trends from user browsing history.
        - Prioritize products within the same or complementary categories.
        - Select ONLY from the candidate product list provided below.
        - Return a JSON array containing product IDs (strings), up to 15 products. If no recommendations can be made, return: []

        """);

        prompt.append("### User Browsing History:\n");

        List<String> viewedProductIds = activities.stream()
                .filter(a -> a.getActionType() == ActionType.VIEW_PRODUCT && a.getEntityId() != null)
                .map(UserActivity::getEntityId)
                .distinct()
                .toList();

        if (viewedProductIds.isEmpty()) {
            prompt.append("- No specific product viewed yet.\n");
        } else {
            List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);
            viewedProducts.forEach(p -> prompt.append(String.format(
                    "- Title: \"%s\"\n  Category: %s\n  Supplier: %s\n\n",
                    p.getTitle(),
                    p.getCategories() != null ? p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")) : "",
                    p.getSupplier() != null ? p.getSupplier().getName() : "")));
        }

        prompt.append("### Available Candidate Products:\n");

        List<Product> candidates = findCandidateProducts(viewedProductIds);
        if (candidates.isEmpty()) {
            prompt.append("- No candidate products available.\n");
        } else {
            candidates.forEach(p -> prompt.append(String.format(
                    "- ID: \"%s\"\n  Title: \"%s\"\n  Category: %s\n\n",
                    p.getId(),
                    p.getTitle(),
                    p.getCategories() != null ? p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")) : "")));
        }

        prompt.append("""
        ### MANDATORY RULES:
        - Return ONLY a JSON array of product IDs: ["product_id_1", "product_id_2", ...]
        - Absolutely no other text, markdown blocks, or explanation outside the JSON array.
        - Each item must be a valid ID from the candidate product list.

        Now, return the JSON array of recommended product IDs:
        """);

        return prompt.toString();
    }

    private List<Product> findCandidateProducts(List<String> viewedProductIds) {
        if (viewedProductIds == null || viewedProductIds.isEmpty()) return List.of();

        List<Product> viewedProducts = productRepository.findAllById(viewedProductIds);
        Set<Category> categories = viewedProducts.stream()
                .filter(p -> p.getCategories() != null)
                .flatMap(p -> p.getCategories().stream())
                .collect(Collectors.toSet());

        if (categories.isEmpty()) return List.of();

        Pageable limit = Pageable.ofSize(20);
        return productRepository.findCandidateProducts(categories, viewedProductIds, limit);
    }

    private String extractJsonArrayString(String rawText) {
        if (rawText == null) return "[]";
        Matcher matcher = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(rawText);
        return matcher.find() ? matcher.group(0) : "[]";
    }

    private List<String> extractKeywords(String title) {
        if (title == null || title.isBlank()) return List.of();
        String[] words = title.replaceAll("[^a-zA-Z0-9\\u00C0-\\u1EF9\\s]", " ").toLowerCase().split("\\s+");
        Set<String> stopWords = Set.of(
                "and", "the", "for", "with", "from", "size", "color", "thin", "thick", "high",
                "quality", "handsome", "cute", "new", "hot", "best", "cua", "cho", "va", "cac",
                "mot", "hai", "ba", "bon", "nam", "nu", "dep", "re", "tot", "hang", "chinh", "set"
        );
        List<String> keywords = new ArrayList<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 3 && !stopWords.contains(trimmed) && !trimmed.matches("\\d+")) {
                if (!keywords.contains(trimmed)) {
                    keywords.add(trimmed);
                }
            }
        }
        return keywords;
    }

    public List<ProductResponse> getRelatedProductsByAi(String productId, int limit) {
        int maxLimit = Math.min(Math.max(limit, 1), 4); // Đảm bảo luôn < 5 (tối đa 4)
        try {
            Product currentProduct = productRepository.findById(productId).orElse(null);
            if (currentProduct == null) {
                return Collections.emptyList();
            }

            boolean isCurrentPet = isPetRelated(currentProduct);
            Map<String, Product> candidateMap = new LinkedHashMap<>();

            // 1. Truy vấn database theo THỂ LOẠI (Category)
            Set<Category> categories = currentProduct.getCategories();
            if (categories != null && !categories.isEmpty()) {
                Set<String> categoryIds = categories.stream()
                        .map(Category::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!categoryIds.isEmpty()) {
                    List<Product> catCandidates = productRepository.findRelatedCandidates(categoryIds, productId, Pageable.ofSize(16));
                    for (Product p : catCandidates) {
                        if (isCurrentPet == isPetRelated(p)) {
                            candidateMap.put(p.getId(), p);
                        }
                    }
                }
            }

            // 2. Truy vấn database theo TỪ KHÓA TIÊU ĐỀ (Title keywords)
            List<String> keywords = extractKeywords(currentProduct.getTitle());
            for (String kw : keywords) {
                if (candidateMap.size() >= 20) break;
                List<Product> byKw = productRepository.findByTitleContainingIgnoreCase(kw, Pageable.ofSize(6)).getContent();
                for (Product p : byKw) {
                    if (!p.getId().equals(productId) && !Boolean.TRUE.equals(p.getDeleted())) {
                        if (isCurrentPet == isPetRelated(p)) {
                            candidateMap.put(p.getId(), p);
                        }
                    }
                }
            }

            // Nếu truy vấn dữ liệu không tìm thấy sản phẩm ứng viên nào -> Không hiển thị
            if (candidateMap.isEmpty()) {
                log.info("Không có sản phẩm ứng viên nào theo thể loại và tiêu đề cho: {}", currentProduct.getTitle());
                return Collections.emptyList();
            }

            List<Product> candidates = new ArrayList<>(candidateMap.values());

            // 3. Đưa cho AI phân tích theo thể loại và tiêu đề
            try {
                String prompt = buildRelatedPrompt(currentProduct, candidates, maxLimit);
                String raw = callGeminiWithFallback(prompt);

                String jsonArray = extractJsonArrayString(raw);
                ObjectMapper objectMapper = new ObjectMapper();
                List<String> recommendedIds = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});

                if (recommendedIds != null && !recommendedIds.isEmpty()) {
                    List<ProductResponse> results = new ArrayList<>();
                    for (String id : recommendedIds) {
                        Product p = candidateMap.get(id);
                        if (p != null && results.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                            results.add(productMapper.toProductResponse(p));
                            if (results.size() >= maxLimit) break;
                        }
                    }

                    if (!results.isEmpty()) {
                        return results;
                    }
                }

                // AI phân tích và thấy không có sản phẩm nào thực sự liên quan -> Không hiển thị
                log.info("AI phân tích không có sản phẩm nào phù hợp cho: {}", currentProduct.getTitle());
                return Collections.emptyList();

            } catch (Exception e) {
                log.error("Lỗi khi AI phân tích sản phẩm liên quan: {}", e.getMessage());
                // Dự phòng khi AI lỗi kết nối: Chỉ lấy sản phẩm cùng thể loại chuẩn xác
                return candidates.stream()
                        .filter(p -> p.getCategories() != null && currentProduct.getCategories() != null
                                && p.getCategories().stream().anyMatch(c -> currentProduct.getCategories().contains(c)))
                        .limit(maxLimit)
                        .map(productMapper::toProductResponse)
                        .toList();
            }
        } catch (Exception ex) {
            log.error("Lỗi trong getRelatedProductsByAi: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private String buildRelatedPrompt(Product currentProduct, List<Product> candidates, int maxLimit) {
        StringBuilder prompt = new StringBuilder();
        String catTitles = currentProduct.getCategories() != null
                ? currentProduct.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", "))
                : "Uncategorized";

        prompt.append(String.format("""
        You are an intelligent e-commerce AI recommendation engine.
        A customer is currently viewing the following product:
        - Title: "%s"
        - Category: %s
        - Description: "%s"

        Below is the list of available candidate products in the store:
        """,
                currentProduct.getTitle(),
                catTitles,
                currentProduct.getDescription() != null ? currentProduct.getDescription() : ""
        ));

        candidates.forEach(p -> prompt.append(String.format(
                "- ID: \"%s\"\n  Title: \"%s\"\n  Category: %s\n\n",
                p.getId(),
                p.getTitle(),
                p.getCategories() != null ? p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")) : ""
        )));

        prompt.append(String.format("""
        ### CRITICAL RELEVANCE RULES:
        1. TARGET AUDIENCE COMPATIBILITY (STRICTEST REQUIREMENT):
           - If the viewed product is for pets (dogs, cats, animals), ONLY recommend products specifically intended for pets (pet apparel, pet accessories, pet toys/food).
           - NEVER recommend human clothing, shirts, dresses, or unrelated adult/kid fashion for a pet product!
           - If the viewed product is for humans, do not recommend pet clothes.
        2. QUALITY OVER QUANTITY:
           - If no candidates in the list are truly relevant or appropriate to the viewed product, return an empty array: []
           - Do NOT pick unrelated items just to fill the quota.
        3. OUTPUT FORMAT:
           - Return ONLY a single JSON array of product IDs selected from the candidate list, e.g.: ["id1", "id2", "id3"]
           - Total number of selected products must be strictly less than 5 (maximum %d products).
           - Absolutely DO NOT include any explanation, markdown code fences (no ```json), or text outside the JSON array.
        """, maxLimit));

        return prompt.toString();
    }
}
