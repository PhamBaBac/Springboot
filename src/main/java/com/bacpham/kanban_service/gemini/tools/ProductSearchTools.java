package com.bacpham.kanban_service.gemini.tools;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component; // <-- THAY ĐỔI: Dùng @Component

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductSearchTools {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductSearchTools(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Tool(description = " Bạn là trợ lý chuyên tìm kiếm sản phẩm. Phân tích câu hỏi của khách hàng và trả về thông tin lọc bao gồm: names, sizes, minPrice, maxPrice.")
    public List<ProductResponse> productSearchTool(
            @ToolParam(description = "Danh sách các từ khóa có trong tên sản phẩm. Ví dụ: ['áo','quần',...]") List<String> names,
            @ToolParam(description = "Danh sách các kích cỡ (size) sản phẩm. Ví dụ: ['S', 'M', 'L',...]") List<String> sizes,
            @ToolParam(description = "Mức giá tối thiểu của sản phẩm") Double minPrice,
            @ToolParam(description = "Mức giá tối đa của sản phẩm") Double maxPrice
    ) {

        if (sizes != null && sizes.isEmpty()) {
            sizes = null;
        }

        Set<Product> result = new HashSet<>();
        if (names != null && !names.isEmpty()) {
            for (String keyword : names) {
                result.addAll(productRepository.findByNameLike(keyword, sizes, minPrice, maxPrice));
            }
        } else {
            result.addAll(productRepository.findByNameLike(null, sizes, minPrice, maxPrice));
        }

        return result.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }
}