package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

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


}