package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.*;
import com.bacpham.kanban_service.service.IProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.bacpham.kanban_service.repository.specification.ProductSpecification;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProductServiceImpl implements IProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;
    CategoryRepository categoryRepository;
    SupplierRepository supplierRepository;
    OrderItemRepository orderItemRepository;
    GenericRedisService<String, String, PageResponse<ProductResponse>> redisService;

    @Override
    public ProductResponse createProduct(ProductCreationRequest request) {
        log.info("Creating product with request: {}", request.toString());
        Product product = productMapper.toProduct(request);
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            Set<Category> categories = new HashSet<>(categoryRepository.findAllById(request.getCategories()));
            product.setCategories(categories);
        }

        if (request.getSupplierId() == null || request.getSupplierId().trim().isEmpty()) {
            throw new AppException(ErrorCode.SUPPLIER_NOT_FOUND);
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        product.setSupplier(supplier);

        product = productRepository.save(product);
        redisService.deleteKeysMatching("product:page:*");

        return productMapper.toProductResponse(product);
    }

    @Override
    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ProductResponse> getProductPage(int page, int pageSize, String title) {
        if (title != null && !title.isEmpty()) {
            Sort sort = Sort.by("createdAt").descending();
            Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
            Page<Product> pageData = productRepository.findByTitleContainingIgnoreCase(title, pageable);

            return PageResponse.<ProductResponse>builder()
                    .currentPage(page)
                    .pageSize(pageData.getSize())
                    .totalPages(pageData.getTotalPages())
                    .totalElements(pageData.getTotalElements())
                    .data(pageData.getContent().stream().map(productMapper::toProductResponse).toList())
                    .build();
        }

        String cacheKey = String.format("product:page:%d:%d", page, pageSize);
        PageResponse<ProductResponse> cached = redisService.get(cacheKey);

        if (cached != null) {
            log.info("Returning cached product page for key");
            return cached;
        }

        log.info("Fetching product page from database for key");

        long totalElements = productRepository.countByDeletedFalse();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        Pageable idPageable = PageRequest.of(page - 1, pageSize);
        List<String> ids = productRepository.findIdsByDeletedFalseOrdered(idPageable);

        List<Product> products = ids.isEmpty()
                ? Collections.emptyList()
                : productRepository.findByIdsWithAssociations(ids);

        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductResponse> productResponses = ids.stream()
                .map(id -> productMap.get(id))
                .filter(Objects::nonNull)
                .map(productMapper::toProductResponse)
                .toList();

        PageResponse<ProductResponse> response = PageResponse.<ProductResponse>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .data(productResponses)
                .build();

        redisService.set(cacheKey, response);
        redisService.setTimeToLive(cacheKey, 1, TimeUnit.HOURS);

        return response;
    }

    @Override
    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        product.setDeleted(true);
        productRepository.save(product);

        redisService.deleteKeysMatching("product:page:*");
    }

    @Override
    public ProductResponse getProductById(String slug, String id) {
        log.info("Retrieving product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toProductResponse(product);

    }

    @Override
    public ProductResponse updateProduct(String id, ProductCreationRequest request) {
        log.info("Updating product with id: {}, request: {}", id, request);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        productMapper.updateProduct(product, request);

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            Set<Category> categories = new HashSet<>(categoryRepository.findAllById(request.getCategories()));
            product.setCategories(categories);
        }
        if (request.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
            product.setSupplier(supplier);
        }

        product = productRepository.save(product);
        ProductResponse response = productMapper.toProductResponse(product);

        redisService.deleteKeysMatching("product:page:*");

        return response;
    }

    @Override
    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            List<String> sizes,
            List<String> colors,
            List<Double> priceRange,
            Pageable pageable) {
        return getFilteredProducts(categoryIds, null, sizes, colors, priceRange, pageable);
    }

    @Override
    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            String search,
            List<String> sizes,
            List<String> colors,
            List<Double> priceRange,
            Pageable pageable) {
        if (categoryIds != null && categoryIds.isEmpty()) {
            categoryIds = null;
        }
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }
        if (sizes != null && sizes.isEmpty()) {
            sizes = null;
        }
        if (colors != null && colors.isEmpty()) {
            colors = null;
        }

        List<String> resolvedCategoryIds = (search != null && !search.trim().isEmpty())
                ? null
                : resolveAllCategoryIds(categoryIds);

        Double minPrice = (priceRange != null && !priceRange.isEmpty()) ? priceRange.get(0) : null;
        Double maxPrice = (priceRange != null && priceRange.size() > 1) ? priceRange.get(1) : null;

        Specification<Product> spec = ProductSpecification.filter(
                resolvedCategoryIds,
                search,
                sizes,
                colors,
                minPrice,
                maxPrice);

        Page<Product> filteredProductsPage = productRepository.findAll(spec, pageable);
        log.info("Filtered products page: {}", filteredProductsPage);

        return filteredProductsPage.map(productMapper::toProductResponse);
    }

    /**
     * BFS: Từ danh sách categoryIds ban đầu (có thể là cha/con), mở rộng ra tất cả
     * các ID con cháu (descendant) để đảm bảo scope lọc bao phủ toàn bộ nhánh danh
     * mục.
     *
     * Ví dụ: catIds=["ao-dai-parent-id"] → resolve → ["ao-dai-parent-id",
     * "ao-dai-co-dau-id", "ao-dai-tre-em-id", ...]
     * Đảm bảo Bước 2 (lọc giá) luôn chạy trên đúng tập sản phẩm đã xác định ở Bước
     * 1.
     */
    private List<String> resolveAllCategoryIds(List<String> inputIds) {
        if (inputIds == null || inputIds.isEmpty()) {
            return inputIds;
        }
        Set<String> allIds = new LinkedHashSet<>(inputIds);
        Queue<String> queue = new LinkedList<>(inputIds);
        while (!queue.isEmpty()) {
            String parentId = queue.poll();
            List<Category> children = categoryRepository.findByParentIdAndDeletedFalse(parentId);
            for (Category child : children) {
                if (child != null && child.getId() != null && allIds.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return new ArrayList<>(allIds);
    }

    @Override
    public List<ProductResponse> getListProductRecommendations(List<String> ids) {
        List<Product> products = productRepository.findAllById(ids);
        return products.stream()
                .map(productMapper::toProductResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> getBestSellers() {
        List<Object[]> result = orderItemRepository.findBestSellerProductIds();
        List<String> productIds = result.stream()
                .map(r -> (String) r[0])
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAllById(productIds);
        return products.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

}