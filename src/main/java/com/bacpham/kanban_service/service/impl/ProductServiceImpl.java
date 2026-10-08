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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
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
    RedisTemplate<String, Object> rawRedisTemplate;

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
        redisService.deleteKeysMatching("shop:products:*");
        invalidateHomeCaches();

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
        PageResponse<ProductResponse> cached = null;
        try {
            cached = redisService.get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis get failed for cacheKey: {}, evicting: {}", cacheKey, e.getMessage());
            try {
                redisService.delete(cacheKey);
            } catch (Exception ignored) {}
        }

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
        redisService.deleteKeysMatching("shop:products:*");
        rawRedisTemplate.delete("product:detail:" + id);
        invalidateHomeCaches();
    }

    @Override
    public ProductResponse getProductById(String slug, String id) {
        String cacheKey = "product:detail:" + id;
        try {
            Object cached = rawRedisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof ProductResponse pr) {
                log.info("[Cache HIT] product detail: {}", id);
                return pr;
            } else if (cached != null) {
                rawRedisTemplate.delete(cacheKey);
            }
        } catch (Exception e) {
            log.warn("[Cache] Redis get failed for product detail {}: {}", id, e.getMessage());
            try { rawRedisTemplate.delete(cacheKey); } catch (Exception ignored) {}
        }

        log.info("[Cache MISS] Fetching product detail from DB: {}", id);
        List<Product> products = productRepository.findByIdsWithAssociations(List.of(id));
        Product product = (!products.isEmpty()) ? products.get(0)
                : productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductResponse response = productMapper.toProductResponse(product);

        try {
            rawRedisTemplate.opsForValue().set(cacheKey, response);
            rawRedisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[Cache] Redis set failed for product detail {}: {}", id, e.getMessage());
        }

        return response;
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
        redisService.deleteKeysMatching("shop:products:*");
        rawRedisTemplate.delete("product:detail:" + id);
        invalidateHomeCaches();

        return response;
    }

    @Override
    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            List<Double> priceRange,
            Pageable pageable) {
        return getFilteredProducts(categoryIds, null, priceRange, null, pageable);
    }

    @Override
    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            String search,
            List<Double> priceRange,
            Pageable pageable) {
        return getFilteredProducts(categoryIds, search, priceRange, null, pageable);
    }

    @Override
    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            String search,
            List<Double> priceRange,
            String sortBy,
            Pageable pageable) {
        if (categoryIds != null && categoryIds.isEmpty()) {
            categoryIds = null;
        }
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }

        List<String> resolvedCategoryIds = resolveAllCategoryIds(categoryIds);

        Double minPrice = (priceRange != null && !priceRange.isEmpty()) ? priceRange.get(0) : null;
        Double maxPrice = (priceRange != null && priceRange.size() > 1) ? priceRange.get(1) : null;

        String cacheKey = buildFilterCacheKey(resolvedCategoryIds, search, minPrice, maxPrice, sortBy, pageable);
        try {
            PageResponse<ProductResponse> cached = redisService.get(cacheKey);
            if (cached != null) {
                log.info("Returning cached shop filter page for key: {}", cacheKey);
                return new PageImpl<>(cached.getData(), pageable, cached.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("Redis get failed for shop filter key: {}, evicting: {}", cacheKey, e.getMessage());
            try {
                redisService.delete(cacheKey);
            } catch (Exception ignored) {}
        }

        Specification<Product> spec = ProductSpecification.filter(
                resolvedCategoryIds,
                search,
                minPrice,
                maxPrice,
                sortBy);

        Page<Product> filteredProductsPage = productRepository.findAll(spec, pageable);
        log.info("Filtered products page: {}", filteredProductsPage);

        Page<ProductResponse> responsePage = filteredProductsPage.map(productMapper::toProductResponse);

        try {
            PageResponse<ProductResponse> toCache = PageResponse.<ProductResponse>builder()
                    .currentPage(responsePage.getNumber() + 1)
                    .pageSize(responsePage.getSize())
                    .totalPages(responsePage.getTotalPages())
                    .totalElements(responsePage.getTotalElements())
                    .data(responsePage.getContent())
                    .build();
            redisService.set(cacheKey, toCache);
            redisService.setTimeToLive(cacheKey, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis set failed for shop filter key: {}, error: {}", cacheKey, e.getMessage());
        }

        return responsePage;
    }

    private String buildFilterCacheKey(
            List<String> resolvedCategoryIds,
            String search,
            Double minPrice,
            Double maxPrice,
            String sortBy,
            Pageable pageable) {
        String catPart = (resolvedCategoryIds != null && !resolvedCategoryIds.isEmpty())
                ? resolvedCategoryIds.stream().sorted().collect(Collectors.joining(","))
                : "all";
        String searchPart = (search != null && !search.isBlank())
                ? search.trim().toLowerCase()
                : "none";
        String pricePart = String.format("%s_%s",
                minPrice != null ? minPrice.toString() : "min",
                maxPrice != null ? maxPrice.toString() : "max");
        String sortPart = (sortBy != null && !sortBy.isBlank())
                ? sortBy.trim().toLowerCase()
                : "relevance";
        int page = pageable.getPageNumber() + 1;
        int size = pageable.getPageSize();

        String rawKey = String.format("cat:%s|s:%s|p:%s|sort:%s|pg:%d|sz:%d",
                catPart, searchPart, pricePart, sortPart, page, size);

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return "shop:products:page:" + sb.toString();
        } catch (Exception e) {
            return "shop:products:page:" + Integer.toHexString(rawKey.hashCode());
        }
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
    @SuppressWarnings("unchecked")
    public List<ProductResponse> getBestSellers() {
        final String cacheKey = "home:bestSellers";
        try {
            Object cached = rawRedisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ProductResponse) {
                log.info("[Cache HIT] bestSellers");
                return (List<ProductResponse>) cached;
            }
        } catch (Exception e) {
            log.warn("[Cache] Redis get failed for bestSellers: {}", e.getMessage());
        }

        log.info("[Cache MISS] Fetching bestSellers from DB");
        List<Object[]> result = orderItemRepository.findBestSellerProductIds();
        List<String> productIds = result.stream()
                .map(r -> (String) r[0])
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            productIds = productRepository.findIdsByDeletedFalseOrdered(PageRequest.of(0, 8));
        }

        if (productIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productRepository.findByIdsWithAssociations(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity(), (p1, p2) -> p1));
        List<ProductResponse> response = productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());

        try {
            rawRedisTemplate.opsForValue().set(cacheKey, response);
            rawRedisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[Cache] Redis set failed for bestSellers: {}", e.getMessage());
        }
        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProductResponse> getNewArrivals(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 24);
        final String cacheKey = "home:newArrivals:" + safeLimit;
        try {
            Object cached = rawRedisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ProductResponse) {
                log.info("[Cache HIT] newArrivals limit={}", safeLimit);
                return (List<ProductResponse>) cached;
            }
        } catch (Exception e) {
            log.warn("[Cache] Redis get failed for newArrivals: {}", e.getMessage());
        }

        log.info("[Cache MISS] Fetching newArrivals from DB, limit={}", safeLimit);
        List<String> ids = productRepository.findIdsByDeletedFalseOrdered(PageRequest.of(0, safeLimit));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findByIdsWithAssociations(ids);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity(), (p1, p2) -> p1));
        List<ProductResponse> response = ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());

        try {
            rawRedisTemplate.opsForValue().set(cacheKey, response);
            rawRedisTemplate.expire(cacheKey, 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[Cache] Redis set failed for newArrivals: {}", e.getMessage());
        }
        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProductResponse> getFlashSaleProducts(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 24);
        final String cacheKey = "home:flashSale:" + safeLimit;
        try {
            Object cached = rawRedisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ProductResponse) {
                log.info("[Cache HIT] flashSale limit={}", safeLimit);
                return (List<ProductResponse>) cached;
            }
        } catch (Exception e) {
            log.warn("[Cache] Redis get failed for flashSale: {}", e.getMessage());
        }

        log.info("[Cache MISS] Fetching flashSale from DB, limit={}", safeLimit);
        List<String> ids = productRepository.findFlashSaleProductIds(PageRequest.of(0, safeLimit));
        if (ids == null || ids.isEmpty()) {
            ids = productRepository.findIdsByDeletedFalseOrdered(PageRequest.of(0, safeLimit));
        }
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findByIdsWithAssociations(ids);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity(), (p1, p2) -> p1));
        List<ProductResponse> response = ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());

        try {
            rawRedisTemplate.opsForValue().set(cacheKey, response);
            rawRedisTemplate.expire(cacheKey, 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[Cache] Redis set failed for flashSale: {}", e.getMessage());
        }
        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProductResponse> getRelatedProducts(String productId, int limit) {
        if (productId == null || productId.trim().isEmpty()) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        final String cacheKey = "shop:products:related:" + productId + ":" + safeLimit;

        try {
            Object cached = rawRedisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ProductResponse) {
                log.info("[Cache HIT] related products productId={}, limit={}", productId, safeLimit);
                return (List<ProductResponse>) cached;
            } else if (cached != null) {
                rawRedisTemplate.delete(cacheKey);
            }
        } catch (Exception e) {
            log.warn("[Cache] Redis get failed for related products key {}: {}", cacheKey, e.getMessage());
            try { rawRedisTemplate.delete(cacheKey); } catch (Exception ignored) {}
        }

        log.info("[Cache MISS] Fetching related products from DB for productId={}, limit={}", productId, safeLimit);
        Product currentProduct = productRepository.findById(productId).orElse(null);
        if (currentProduct == null || Boolean.TRUE.equals(currentProduct.getDeleted())) {
            return List.of();
        }

        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();

        try {
            // 1. Ưu tiên 1: Sản phẩm cùng danh mục
            Set<Category> categories = currentProduct.getCategories();
            if (categories != null && !categories.isEmpty()) {
                Set<String> categoryIds = categories.stream()
                        .map(Category::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!categoryIds.isEmpty()) {
                    List<String> catProductIds = productRepository.findRelatedProductIds(
                            categoryIds, productId, PageRequest.of(0, safeLimit * 2));
                    if (catProductIds != null) {
                        candidateIds.addAll(catProductIds);
                    }
                }
            }

            // 2. Ưu tiên 2: Sản phẩm cùng nhà cung cấp (nếu chưa đủ limit)
            if (candidateIds.size() < safeLimit && currentProduct.getSupplier() != null) {
                String supplierId = currentProduct.getSupplier().getId();
                List<String> supplierProductIds = productRepository.findRelatedProductIdsBySupplier(
                        supplierId, productId, PageRequest.of(0, safeLimit));
                if (supplierProductIds != null) {
                    candidateIds.addAll(supplierProductIds);
                }
            }
        } catch (Exception e) {
            log.warn("Error finding related candidate IDs: {}", e.getMessage());
        }

        // 3. Fallback: Bổ sung thêm các sản phẩm mới nhất khác nếu vẫn chưa đủ
        if (candidateIds.size() < safeLimit) {
            try {
                List<String> latestIds = productRepository.findIdsByDeletedFalseOrdered(PageRequest.of(0, safeLimit + 5));
                if (latestIds != null) {
                    for (String id : latestIds) {
                        if (!id.equals(productId)) {
                            candidateIds.add(id);
                            if (candidateIds.size() >= safeLimit) break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding fallback IDs: {}", e.getMessage());
            }
        }

        List<String> finalIds = candidateIds.stream().limit(safeLimit).toList();
        if (finalIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productRepository.findByIdsWithAssociations(finalIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity(), (p1, p2) -> p1));

        List<ProductResponse> response = finalIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());

        try {
            rawRedisTemplate.opsForValue().set(cacheKey, response);
            rawRedisTemplate.expire(cacheKey, 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[Cache] Redis set failed for related products key {}: {}", cacheKey, e.getMessage());
        }

        return response;
    }

    /**
     * Xoá toàn bộ home-aggregate caches: bestSellers, newArrivals, flashSale, related.
     * Gọi mỗi khi có mutation (create/update/delete) product hoặc subproduct.
     */
    private void invalidateHomeCaches() {
        try {
            rawRedisTemplate.delete("home:bestSellers");
            redisService.deleteKeysMatching("home:newArrivals:*");
            redisService.deleteKeysMatching("home:flashSale:*");
            redisService.deleteKeysMatching("shop:products:related:*");
            log.info("[Cache] Invalidated home caches (bestSellers, newArrivals, flashSale, related)");
        } catch (Exception e) {
            log.warn("[Cache] Failed to invalidate home caches: {}", e.getMessage());
        }
    }

}