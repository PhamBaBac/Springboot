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
import org.springframework.cache.annotation.Cacheable;
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

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);
    private static final Pattern CLEAN_WORDS_PATTERN = Pattern.compile("[^a-zA-Z0-9\\u00C0-\\u1EF9\\s]");
    private static final Pattern SPLIT_SPACES_PATTERN = Pattern.compile("\\s+");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^\\d+$");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "the", "for", "with", "from", "in", "on", "at", "to", "by", "of",
            "new", "hot", "best", "top", "pro", "plus", "super", "mini", "max",
            "cua", "cho", "va", "cac", "nhung", "mot", "hai", "ba", "bon", "nam",
            "dep", "re", "tot", "hang", "chinh", "set", "loai", "chiec", "cai", "bo"
    );

    private final UserActivityRepository userActivityRepository;
    private final ProductRepository productRepository;
    private final RestClient restClient;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    @Value("${gemini.recommendation.models:gemini-2.0-flash,gemini-1.5-flash,gemini-2.5-flash,gemini-flash-latest}")
    private List<String> modelsToTry;

    public RecommendationService(
            @Qualifier("geminiRestClient") RestClient restClient,
            UserActivityRepository userActivityRepository,
            ProductRepository productRepository,
            ProductMapper productMapper,
            ObjectMapper objectMapper
    ) {
        this.userActivityRepository = userActivityRepository;
        this.productRepository = productRepository;
        this.restClient = restClient;
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
    }

    private String callGeminiWithFallback(String prompt) {
        List<String> models = (modelsToTry != null && !modelsToTry.isEmpty())
                ? modelsToTry
                : List.of("gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest");

        GeminiRequest request = GeminiRequest.fromText(prompt);

        for (String currentModel : models) {
            String trimmedModel = currentModel.trim();
            try {
                String url = "/v1beta/models/%s:generateContent".formatted(trimmedModel);
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
                    log.info("Gemini recommendation response from model [{}]: {}", trimmedModel, raw);
                    return raw;
                }
            } catch (Exception e) {
                log.warn("Recommendation AI model [{}] failed ({}). Trying next...", trimmedModel, e.getMessage());
            }
        }
        return "[]";
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

    public List<ProductResponse> getRecommendationProductsForUser(User user) {
        String jsonArray = getRecommendationsForUser(user);
        try {
            List<String> productIds = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
            if (productIds == null || productIds.isEmpty()) {
                return Collections.emptyList();
            }
            return productRepository.findAllById(productIds).stream()
                    .map(productMapper::toProductResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to parse user recommendation IDs: {}", e.getMessage());
            return Collections.emptyList();
        }
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

        List<Product> viewedProducts = viewedProductIds.isEmpty()
                ? Collections.emptyList()
                : productRepository.findAllById(viewedProductIds);

        if (viewedProducts.isEmpty()) {
            prompt.append("- No specific product viewed yet.\n");
        } else {
            viewedProducts.forEach(p -> prompt.append(String.format(
                    "- Title: \"%s\"\n  Category: %s\n  Supplier: %s\n\n",
                    p.getTitle(),
                    p.getCategories() != null ? p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")) : "",
                    p.getSupplier() != null ? p.getSupplier().getName() : "")));
        }

        prompt.append("### Available Candidate Products:\n");

        List<Product> candidates = findCandidateProducts(viewedProducts, viewedProductIds);
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

    private List<Product> findCandidateProducts(List<Product> viewedProducts, List<String> viewedProductIds) {
        if (viewedProducts == null || viewedProducts.isEmpty()) return List.of();

        Set<Category> categories = viewedProducts.stream()
                .filter(p -> p.getCategories() != null)
                .flatMap(p -> p.getCategories().stream())
                .collect(Collectors.toSet());

        if (categories.isEmpty()) return List.of();

        Pageable limit = Pageable.ofSize(20);
        return productRepository.findCandidateProducts(categories, viewedProductIds, limit);
    }

    private String extractJsonArrayString(String rawText) {
        if (rawText == null || rawText.isBlank()) return "[]";
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(rawText);
        return matcher.find() ? matcher.group(0) : "[]";
    }

    private List<String> extractKeywords(String title) {
        if (title == null || title.isBlank()) return List.of();
        String cleaned = CLEAN_WORDS_PATTERN.matcher(title).replaceAll(" ").toLowerCase();
        String[] words = SPLIT_SPACES_PATTERN.split(cleaned);
        Set<String> uniqueKeywords = new LinkedHashSet<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 3 && !STOP_WORDS.contains(trimmed) && !DIGITS_PATTERN.matcher(trimmed).matches()) {
                uniqueKeywords.add(trimmed);
            }
        }
        return new ArrayList<>(uniqueKeywords);
    }

    @Cacheable(value = "related_products", key = "#productId + '_' + #limit", unless = "#result == null || #result.isEmpty()")
    public List<ProductResponse> getRelatedProductsByAi(String productId, int limit) {
        int maxLimit = Math.min(Math.max(limit, 1), 4); // Đảm bảo luôn < 5 (tối đa 4)
        try {
            Product currentProduct = productRepository.findById(productId).orElse(null);
            if (currentProduct == null) {
                return Collections.emptyList();
            }

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
                        candidateMap.put(p.getId(), p);
                    }
                }
            }

            // 2. Truy vấn database theo TỪ KHÓA TIÊU ĐỀ (chỉ truy vấn khi ứng viên từ Category chưa đủ và giới hạn tối đa 3 keywords)
            if (candidateMap.size() < 10) {
                List<String> keywords = extractKeywords(currentProduct.getTitle());
                int kwCount = 0;
                for (String kw : keywords) {
                    if (candidateMap.size() >= 20 || kwCount >= 3) break;
                    List<Product> byKw = productRepository.findByTitleContainingIgnoreCase(kw, Pageable.ofSize(6)).getContent();
                    for (Product p : byKw) {
                        if (!p.getId().equals(productId) && !Boolean.TRUE.equals(p.getDeleted())) {
                            candidateMap.put(p.getId(), p);
                        }
                    }
                    kwCount++;
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

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
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
                truncate(currentProduct.getDescription(), 200)
        ));

        candidates.forEach(p -> prompt.append(String.format(
                "- ID: \"%s\"\n  Title: \"%s\"\n  Category: %s\n\n",
                p.getId(),
                p.getTitle(),
                p.getCategories() != null ? p.getCategories().stream().map(Category::getTitle).collect(Collectors.joining(", ")) : ""
        )));

        prompt.append(String.format("""
        ### CRITICAL RELEVANCE RULES:
        1. LOGICAL RELEVANCE & COMPATIBILITY (STRICTEST REQUIREMENT):
           - Only recommend products that have a direct, meaningful relationship with the viewed product:
             a) Direct Alternatives: Same or adjacent category, comparable usage, or alternative models/variants.
             b) Complementary / Cross-Selling: Accessories, add-ons, or items frequently used alongside or supporting the viewed product.
           - NEVER recommend completely unrelated items with conflicting purposes, incompatible ecosystems, or totally disconnected contexts.
        2. QUALITY OVER QUANTITY:
           - If no candidates in the list are genuinely relevant or complementary to the viewed product, return an empty array: []
           - Do NOT pick unrelated items just to fill the quota.
        3. OUTPUT FORMAT:
           - Return ONLY a single JSON array of product IDs selected from the candidate list, e.g.: ["id1", "id2", "id3"]
           - Total number of selected products must be strictly less than 5 (maximum %d products).
           - Absolutely DO NOT include any explanation, markdown code fences (no ```json), or text outside the JSON array.
        """, maxLimit));

        return prompt.toString();
    }
}
