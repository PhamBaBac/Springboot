package com.bacpham.kanban_service.gemini.tools;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductSearchTools {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 10;
    private static final Pattern SPLIT_SPACES_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "the", "for", "with", "from",
            "cua", "của", "cho", "va", "và", "cac", "các",
            "nhung", "những", "mot", "một", "hai", "ba", "bon", "bốn",
            "loai", "loại", "chiec", "chiếc", "cai", "cái", "bo", "bộ",
            "la", "là", "hay", "hoac", "hoặc", "con", "còn",
            "khong", "không", "co", "có", "shop", "hoi", "hỏi",
            "tim", "tìm", "mua", "xem", "nao", "nào", "nay", "này",
            "minh", "mình", "em", "anh", "chi", "chị", "ban", "bạn"
    );

    private static final ThreadLocal<List<ProductResponse>> CURRENT_PRODUCTS = new ThreadLocal<>();

    public static List<ProductResponse> getAndClearFoundProducts() {
        List<ProductResponse> list = CURRENT_PRODUCTS.get();
        CURRENT_PRODUCTS.remove();
        return list != null ? list : Collections.emptyList();
    }

    public static void clearFoundProducts() {
        CURRENT_PRODUCTS.remove();
    }

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductSearchTools(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Tool(description = """
        Tìm kiếm các sản phẩm trong cửa hàng dựa trên yêu cầu của khách hàng.
        Dùng công cụ này khi khách hàng hỏi mua đồ, tìm kiếm sản phẩm, phụ kiện, hỏi giá cả, kích cỡ/thông số, màu sắc, thương hiệu.
        Hãy phân tích câu hỏi của khách hàng để trích xuất các tiêu chí tìm kiếm phù hợp nhất.
    """)
    public List<ProductResponse> productSearchTool(
            @ToolParam(description = "Từ khóa tìm kiếm sản phẩm (ví dụ tên sản phẩm, dòng máy, loại hàng, thương hiệu). Để null nếu chỉ tìm theo danh mục.") String keyword,
            @ToolParam(description = "Tên danh mục hoặc loại sản phẩm (ví dụ: 'Nhà bếp', 'Điện tử', 'Gia dụng', 'Thời trang', 'Nội thất'). Để null nếu không rõ.") String categoryName,
            @ToolParam(description = "Danh sách kích cỡ hoặc thông số phân loại sản phẩm (ví dụ: size S/M/L, dung tích, công suất, kích thước). Để null nếu khách không chỉ định.") List<String> sizes,
            @ToolParam(description = "Danh sách màu sắc cần tìm (ví dụ: 'đen', 'trắng', 'xanh', 'xám', 'bạc'). Để null nếu khách không chỉ định màu.") List<String> colors,
            @ToolParam(description = "Mức giá tối thiểu của sản phẩm (VNĐ).") Double minPrice,
            @ToolParam(description = "Mức giá tối đa của sản phẩm (VNĐ).") Double maxPrice,
            @ToolParam(description = "Số lượng sản phẩm tối đa cần lấy (mặc định lấy 6, tối đa 10).") Integer limit
    ) {
        log.info("AI invoking productSearchTool -> keyword='{}', category='{}', sizes={}, colors={}, minPrice={}, maxPrice={}, limit={}",
                keyword, categoryName, sizes, colors, minPrice, maxPrice, limit);

        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanCategory = (categoryName != null && !categoryName.trim().isEmpty()) ? categoryName.trim() : null;

        List<String> cleanSizes = (sizes != null && !sizes.isEmpty())
                ? sizes.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .distinct()
                        .toList()
                : null;
        if (cleanSizes != null && cleanSizes.isEmpty()) cleanSizes = null;

        List<String> cleanColors = (colors != null && !colors.isEmpty())
                ? colors.stream()
                        .filter(c -> c != null && !c.isBlank())
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .distinct()
                        .toList()
                : null;
        if (cleanColors != null && cleanColors.isEmpty()) cleanColors = null;

        int pageSize = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int fetchSize = (cleanKeyword != null) ? Math.min(pageSize * 3, 20) : pageSize;
        Pageable pageable = PageRequest.of(0, fetchSize);

        List<Product> products = new ArrayList<>(productRepository.searchProductsForAi(
                cleanKeyword, cleanCategory, cleanSizes, cleanColors, minPrice, maxPrice, pageable
        ));

        // Nếu có keyword, chấm điểm độ liên quan (ưu tiên Title > Category > Description)
        if (cleanKeyword != null && !products.isEmpty()) {
            final String kw = cleanKeyword;
            products.sort((p1, p2) -> Integer.compare(calculateRelevance(p2, kw), calculateRelevance(p1, kw)));

            // Nếu có các sản phẩm khớp Title hoặc Category (score >= 100), loại bỏ các sản phẩm chỉ khớp trong description
            boolean hasHighRelevance = products.stream().anyMatch(p -> calculateRelevance(p, kw) >= 100);
            if (hasHighRelevance) {
                products = products.stream()
                        .filter(p -> calculateRelevance(p, kw) >= 100)
                        .collect(Collectors.toCollection(ArrayList::new));
            }

            if (products.size() > pageSize) {
                products = new ArrayList<>(products.subList(0, pageSize));
            }
        }

        Set<String> seenIds = new HashSet<>();
        for (Product p : products) {
            if (p.getId() != null) {
                seenIds.add(p.getId());
            }
        }

        // Fallback: nếu tìm cả cụm từ khóa không ra kết quả (do từ không liền nhau hoặc hỏi nhiều loại đồ),
        // thử tìm theo các từ khóa có nghĩa nhất (hỗ trợ từ tiếng Việt >= 2 ký tự như "áo", "ví", "mũ")
        if (products.isEmpty() && cleanKeyword != null && cleanKeyword.contains(" ")) {
            String[] words = SPLIT_SPACES_PATTERN.split(cleanKeyword);
            List<String> candidateWords = Arrays.stream(words)
                    .map(String::trim)
                    .filter(w -> w.length() >= 2 && !STOP_WORDS.contains(w.toLowerCase()))
                    .distinct()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .limit(3)
                    .toList();

            if (!candidateWords.isEmpty()) {
                int perWordQuota = Math.max(2, pageSize / candidateWords.size());
                for (String word : candidateWords) {
                    if (products.size() >= pageSize) break;
                    List<Product> partial = productRepository.searchProductsForAi(
                            word, cleanCategory, cleanSizes, cleanColors, minPrice, maxPrice, PageRequest.of(0, Math.min(perWordQuota * 2, 10))
                    );
                    partial.sort((p1, p2) -> Integer.compare(calculateRelevance(p2, word), calculateRelevance(p1, word)));

                    boolean hasHigh = partial.stream().anyMatch(p -> calculateRelevance(p, word) >= 100);
                    if (hasHigh) {
                        partial = partial.stream()
                                .filter(p -> calculateRelevance(p, word) >= 100)
                                .toList();
                    }

                    int addedForWord = 0;
                    for (Product p : partial) {
                        if (p.getId() != null && seenIds.add(p.getId())) {
                            products.add(p);
                            addedForWord++;
                            if (addedForWord >= perWordQuota || products.size() >= pageSize) {
                                break;
                            }
                        }
                    }
                }

                // Nếu vẫn chưa đủ pageSize, bù thêm từ các kết quả còn lại
                if (products.size() < pageSize) {
                    for (String word : candidateWords) {
                        if (products.size() >= pageSize) break;
                        List<Product> partial = productRepository.searchProductsForAi(
                                word, cleanCategory, cleanSizes, cleanColors, minPrice, maxPrice, pageable
                        );
                        for (Product p : partial) {
                            if (p.getId() != null && seenIds.add(p.getId())) {
                                products.add(p);
                                if (products.size() >= pageSize) break;
                            }
                        }
                    }
                }
            }
        }

        log.info("AI productSearchTool found {} products", products.size());

        List<String> productIds = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<ProductResponse> result;
        if (productIds.isEmpty()) {
            result = Collections.emptyList();
        } else {
            // Batch fetch cùng associations để triệt tiêu N+1 queries khi map DTO
            Map<String, Product> productMap = productRepository.findByIdsWithAssociations(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (p1, p2) -> p1));

            result = productIds.stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .map(productMapper::toProductResponse)
                    .toList();
        }

        List<ProductResponse> existing = CURRENT_PRODUCTS.get();
        if (existing == null || existing.isEmpty()) {
            CURRENT_PRODUCTS.set(new ArrayList<>(result));
        } else {
            Set<String> existingIds = existing.stream()
                    .map(ProductResponse::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (ProductResponse pr : result) {
                if (pr.getId() != null && existingIds.add(pr.getId())) {
                    existing.add(pr);
                }
            }
        }

        return result;
    }

    /**
     * Chấm điểm độ liên quan giữa sản phẩm và từ khóa:
     * - Khớp tiêu đề sản phẩm (Title): 200 - 1000 điểm
     * - Khớp danh mục sản phẩm (Category): 100 điểm
     * - Khớp mô tả chi tiết (Description): 10 điểm
     */
    private int calculateRelevance(Product product, String keyword) {
        if (keyword == null || keyword.isBlank() || product == null) {
            return 0;
        }
        String kw = keyword.toLowerCase().trim();
        int score = 0;

        String title = product.getTitle();
        if (title != null) {
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.equals(kw)) {
                score += 1000;
            } else if (lowerTitle.startsWith(kw + " ") || lowerTitle.contains(" " + kw + " ") || lowerTitle.endsWith(" " + kw)) {
                score += 500;
            } else if (lowerTitle.contains(kw)) {
                score += 200;
            }
        }

        if (product.getCategories() != null) {
            for (Category c : product.getCategories()) {
                if (c != null && c.getTitle() != null) {
                    String lowerCat = c.getTitle().toLowerCase();
                    if (lowerCat.contains(kw)) {
                        score += 100;
                        break;
                    }
                }
            }
        }

        if (product.getDescription() != null && product.getDescription().toLowerCase().contains(kw)) {
            score += 10;
        }

        return score;
    }
}