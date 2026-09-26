package com.bacpham.kanban_service.gemini.tools;

import com.bacpham.kanban_service.dto.response.ProductResponse;
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
            "and", "the", "for", "with", "from", "cua", "cho", "va", "cac",
            "nhung", "mot", "hai", "ba", "bon", "loai", "chiec", "cai", "bo", "la"
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
        Pageable pageable = PageRequest.of(0, pageSize);

        List<Product> products = new ArrayList<>(productRepository.searchProductsForAi(
                cleanKeyword, cleanCategory, cleanSizes, cleanColors, minPrice, maxPrice, pageable
        ));

        Set<String> seenIds = new HashSet<>();
        for (Product p : products) {
            if (p.getId() != null) {
                seenIds.add(p.getId());
            }
        }

        // Fallback: nếu tìm cả cụm từ khóa không ra kết quả (do từ không liền nhau), thử tìm theo các từ khóa có nghĩa nhất
        if (products.isEmpty() && cleanKeyword != null && cleanKeyword.contains(" ")) {
            String[] words = SPLIT_SPACES_PATTERN.split(cleanKeyword);
            List<String> candidateWords = Arrays.stream(words)
                    .map(String::trim)
                    .filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w.toLowerCase()))
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .limit(2)
                    .toList();

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
}