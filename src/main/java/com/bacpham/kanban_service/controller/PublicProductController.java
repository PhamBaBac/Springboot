package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.gemini.service.RecommendationService;
import com.bacpham.kanban_service.service.IProductService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/products")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class PublicProductController {
        IProductService productService;
        RecommendationService recommendationService;

        @GetMapping
        ApiResponse<List<ProductResponse>> getProducts() {
                return ApiResponse.<List<ProductResponse>>builder()
                                .data(productService.getProducts())
                                .build();
        }

        @GetMapping("/page")
        ApiResponse<PageResponse<ProductResponse>> productPage(
                        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                        @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                        @RequestParam(value = "title", required = false) String title) {
                return ApiResponse.<PageResponse<ProductResponse>>builder()
                                .data(productService.getProductPage(page, pageSize, title))
                                .build();
        }

        @GetMapping("/{slug}/{id}")
        ApiResponse<ProductResponse> getProduct(
                        @PathVariable String slug,
                        @PathVariable String id) {
                ProductResponse productResponse = productService.getProductById(slug, id);

                return ApiResponse.<ProductResponse>builder()
                                .data(productResponse)
                                .build();
        }

        @PostMapping("/filter")
        public ApiResponse<PageResponse<ProductResponse>> filterProducts(
                        @RequestParam(value = "catIds", required = false) List<String> catIds,
                        @RequestParam(value = "search", required = false) String search,
                        @RequestParam(value = "sizes", required = false) List<String> sizes,
                        @RequestParam(value = "colors", required = false) List<String> colors,
                        @RequestParam(value = "price", required = false) List<Double> price,
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "pageSize", defaultValue = "12") int pageSize) {
                Pageable pageable = PageRequest.of(page - 1, pageSize);

                Page<ProductResponse> result = productService.getFilteredProducts(
                                catIds,
                                search,
                                sizes,
                                colors,
                                price,
                                pageable);

                PageResponse<ProductResponse> response = PageResponse.<ProductResponse>builder()
                                .currentPage(result.getNumber() + 1)
                                .totalPages(result.getTotalPages())
                                .pageSize(result.getSize())
                                .totalElements(result.getTotalElements())
                                .data(result.getContent())
                                .build();

                return ApiResponse.<PageResponse<ProductResponse>>builder()
                                .data(response)
                                .build();
        }

        @GetMapping("/listProductRecommendations")
        public ApiResponse<List<ProductResponse>> getProductsByIds(@RequestBody List<String> ids) {
                log.info("Nhận yêu cầu gợi ý sản phẩm theo danh sách ID: {}", ids);
                List<ProductResponse> result = productService.getListProductRecommendations(ids);
                return ApiResponse.<List<ProductResponse>>builder()
                                .data(result)
                                .message("Lấy gợi ý sản phẩm thành công")
                                .build();
        }

        @GetMapping({ "/related/{id}", "/ai-related/{id}" })
        public ApiResponse<List<ProductResponse>> getRelatedProducts(
                        @PathVariable String id,
                        @RequestParam(value = "limit", required = false, defaultValue = "4") int limit) {
                int safeLimit = Math.min(Math.max(limit, 1), 4);
                List<ProductResponse> related = recommendationService.getRelatedProductsByAi(id, safeLimit);
                return ApiResponse.<List<ProductResponse>>builder()
                                .data(related)
                                .message("Lấy danh sách sản phẩm liên quan thành công")
                                .build();
        }

        @GetMapping("/category/{categoryId}")
        public ApiResponse<List<ProductResponse>> getProductsByCategory(
                        @PathVariable String categoryId,
                        @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
                Pageable pageable = PageRequest.of(0, Math.max(1, limit));
                Page<ProductResponse> filtered = productService.getFilteredProducts(
                                List.of(categoryId),
                                null,
                                null,
                                null,
                                null,
                                pageable);
                return ApiResponse.<List<ProductResponse>>builder()
                                .data(filtered.getContent())
                                .message("Lấy danh sách sản phẩm theo danh mục thành công")
                                .build();
        }

        @GetMapping("/bestSellers")
        public ApiResponse<List<ProductResponse>> getBestSellers() {
                return ApiResponse.<List<ProductResponse>>builder()
                                .data(productService.getBestSellers())
                                .build();
        }

}
