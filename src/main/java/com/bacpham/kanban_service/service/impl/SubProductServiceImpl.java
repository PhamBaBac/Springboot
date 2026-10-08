package com.bacpham.kanban_service.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.SubProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.service.ISubProductService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubProductServiceImpl implements ISubProductService {
    SubProductRepository subProductRepository;
    ProductRepository productRepository;
    SubProductMapper subProductMapper;
    GenericRedisService<String, String, Object> redisService;

    public SubProductResponse createSubProduct(SubProductCreationRequest request) {
        SubProduct subProduct = subProductMapper.toSubProduct(request);

        Product product = null;
        if (request.getProductId() != null && !request.getProductId().isEmpty()) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            subProduct.setProduct(product);
        }
        if (request.getQty() != null) {
            subProduct.setStock(request.getQty());
            subProduct.setQty(request.getQty());
        }
        syncAttributes(subProduct, request);

        if (subProduct.getSku() == null || subProduct.getSku().trim().isEmpty()) {
            String pPrefix = (product != null && product.getTitle() != null)
                    ? product.getTitle().replaceAll("[^a-zA-Z0-9]", "").toUpperCase()
                    : "SP";
            if (pPrefix.length() > 6) pPrefix = pPrefix.substring(0, 6);
            String sizePart = (subProduct.getSize() != null && !subProduct.getSize().isBlank())
                    ? "-" + subProduct.getSize().replaceAll("[^a-zA-Z0-9]", "").toUpperCase()
                    : "";
            String randomPart = java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            subProduct.setSku(pPrefix + sizePart + "-" + randomPart);
        }

        subProduct = subProductRepository.save(subProduct);
        evictProductCache();

        return subProductMapper.toSubProductResponse(subProduct);
    }

    @Override
    public Map<String, List<?>> getSubProducts() {
        return getSubProducts(null, null);
    }

    @Override
    public Map<String, List<?>> getSubProducts(List<String> catIds, String search) {
        if (catIds != null && catIds.isEmpty()) {
            catIds = null;
        }
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }

        String catPart = (catIds != null && !catIds.isEmpty())
                ? String.join(",", catIds.stream().sorted().toList())
                : "all";
        String searchPart = (search != null && !search.isBlank())
                ? search.trim().toLowerCase()
                : "none";
        String cacheKey = "shop:filters:" + catPart + ":" + searchPart;

        try {
            Object cached = redisService.get(cacheKey);
            if (cached instanceof Map) {
                return (Map<String, List<?>>) cached;
            }
        } catch (Exception e) {
            log.warn("Redis get failed for filter values key: {}, evicting: {}", cacheKey, e.getMessage());
            try {
                redisService.delete(cacheKey);
            } catch (Exception ignored) {}
        }

        Map<String, List<?>> result = new HashMap<>();
        if (catIds != null) {
            result.put("prices", subProductRepository.findDistinctPricesByCatIds(catIds, search));
        } else {
            result.put("prices", subProductRepository.findDistinctPricesWithoutCatIds(search));
        }

        try {
            redisService.set(cacheKey, result);
            redisService.setTimeToLive(cacheKey, 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis set failed for filter values key: {}", cacheKey, e);
        }

        return result;
    }

    public void delete(String id) {
        SubProduct subProduct = subProductRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        subProduct.setDeleted(true);
        subProductRepository.save(subProduct);
        evictProductCache();
    }

    public SubProductResponse updateSubProduct(SubProductCreationRequest request) {
        SubProduct subProduct = subProductRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        // @BeanMapping(IGNORE) in mapper already protects null fields,
        // but we add explicit guards for price & discount to be safe.
        Double oldPrice = subProduct.getPrice();
        Double oldDiscount = subProduct.getDiscount();

        subProductMapper.updateSubProduct(subProduct, request);

        // Restore price if request didn't send it (or sent null)
        if (request.getPrice() == null) {
            subProduct.setPrice(oldPrice);
        }
        // Restore discount if request didn't send it (null means "no change")
        if (request.getDiscount() == null) {
            subProduct.setDiscount(oldDiscount);
        }

        if (request.getQty() != null) {
            subProduct.setStock(request.getQty());
            subProduct.setQty(request.getQty());
        }
        if (request.getSku() != null && !request.getSku().trim().isEmpty()) {
            subProduct.setSku(request.getSku().trim());
        }
        syncAttributes(subProduct, request);
        subProduct = subProductRepository.save(subProduct);
        evictProductCache();

        return subProductMapper.toSubProductResponse(subProduct);
    }

    private void evictProductCache() {
        try {
            redisService.deleteKeysMatching("shop:products:*");
            redisService.deleteKeysMatching("product:page:*");
            redisService.deleteKeysMatching("shop:filters:*");
            // Phase 2: invalidate home-aggregate & product detail caches
            redisService.deleteKeysMatching("home:bestSellers*");
            redisService.deleteKeysMatching("home:newArrivals:*");
            redisService.deleteKeysMatching("home:flashSale:*");
            redisService.deleteKeysMatching("product:detail:*");
        } catch (Exception e) {
            log.warn("Failed to evict product cache: {}", e.getMessage());
        }
    }

    private void syncAttributes(SubProduct subProduct, SubProductCreationRequest request) {
        Map<String, String> attrs = request.getAttributes();
        if (attrs != null && !attrs.isEmpty()) {
            subProduct.setAttributes(attrs);
            if (subProduct.getColor() == null || subProduct.getColor().isEmpty()) {
                for (Map.Entry<String, String> entry : attrs.entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    if (k.contains("màu") || k.contains("color")) {
                        subProduct.setColor(entry.getValue());
                        break;
                    }
                }
            }
            if (subProduct.getSize() == null || subProduct.getSize().isEmpty()) {
                for (Map.Entry<String, String> entry : attrs.entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    if (k.contains("size") || k.contains("kích") || k.contains("dung lượng") || k.contains("bộ nhớ") || k.contains("storage")) {
                        subProduct.setSize(entry.getValue());
                        break;
                    }
                }
            }
        }
    }

    public List<SubProductResponse> getAllSubProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<SubProduct> subProducts = subProductRepository.findAllByProductAndDeletedFalse(product);

        return subProducts.stream()
                .map(subProductMapper::toSubProductResponse)
                .collect(Collectors.toList());
    }
    public SubProduct findById(String subProductId) {
        return subProductRepository.findById(subProductId)
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
    }
}
