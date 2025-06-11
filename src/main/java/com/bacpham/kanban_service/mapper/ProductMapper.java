package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.request.ProductCreationRequestCSV;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.SupplierResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.mapstruct.*;

import java.util.*;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "subProducts", ignore = true)
    Product toProduct(ProductCreationRequest request);

    @Mapping(source = "supplier.id", target = "supplierId")
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "subProducts", ignore = true)
    void updateProduct(@MappingTarget Product product, ProductCreationRequest request);

// --- PHẦN DÀNH CHO BATCH JOB (SỬ DỤNG @Context) ---

    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "subProducts", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "stringToImageList")
    Product toProductFromCSV(
            ProductCreationRequestCSV request,
            @Context SupplierRepository supplierRepository,
            @Context CategoryRepository categoryRepository
    );

    @AfterMapping
    default void linkAssociationsFromCSV(
            @MappingTarget Product product,
            ProductCreationRequestCSV request,
            @Context SupplierRepository supplierRepository,
            @Context CategoryRepository categoryRepository) {

        // 1. Liên kết Supplier từ tên
        Supplier supplier = supplierRepository.findByName(request.getSupplierName())
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found with name: " + request.getSupplierName()));
        product.setSupplier(supplier);

        // 2. Liên kết Categories từ chuỗi tên
        if (request.getCategoryNames() != null && !request.getCategoryNames().isBlank()) {
            List<String> categoryTitles = Arrays.stream(request.getCategoryNames().split(","))
                    .map(String::trim).toList();
            Set<Category> categories = categoryRepository.findByTitleIn(categoryTitles);

            if (categories.size() != categoryTitles.size()) {
                Set<String> foundTitles = categories.stream().map(Category::getTitle).collect(Collectors.toSet());
                categoryTitles.removeAll(foundTitles);
                throw new EntityNotFoundException("Categories not found: " + String.join(", ", categoryTitles));
            }
            product.setCategories(categories);
        } else {
            product.setCategories(new HashSet<>());
        }
    }

    // Helper method để chuyển chuỗi "url1,url2" thành List<String>
    @Named("stringToImageList")
    default List<String> stringToImageList(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return Arrays.asList(images.split(","));
    }

}