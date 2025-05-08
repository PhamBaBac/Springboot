package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.SupplierRequest;
import com.bacpham.kanban_service.dto.response.SupplierResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
@Mapper(componentModel = "spring")
public interface SupplierMapper {

     @Mapping(target = "categories", ignore = true)
     @Mapping(target = "products", ignore = true)
     Supplier toSupplier(SupplierRequest request);

     @Mapping(target = "categories", expression = "java(mapCategoryTitles(supplier))")
     @Mapping(target = "products", expression = "java(mapProductTitles(supplier))")

     SupplierResponse toSupplierResponse(Supplier supplier);

     default List<String> mapCategoryTitles(Supplier supplier) {
          if (supplier.getCategories() == null) return List.of();
          return supplier.getCategories().stream()
                  .map(Category::getTitle)
                  .toList();
     }

        default List<String> mapProductTitles(Supplier supplier) {
            if (supplier.getProducts() == null) return List.of();
            return supplier.getProducts().stream()
                    .map(Product::getTitle)
                    .toList();
        }


}

