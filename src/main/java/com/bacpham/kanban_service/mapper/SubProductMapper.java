package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.FilterSubProductResponse;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.entity.SubProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SubProductMapper {

    SubProduct toSubProduct(SubProductCreationRequest request);

    SubProductResponse toSubProductResponse(SubProduct subProduct);

    FilterSubProductResponse toFilterSubProductResponse(SubProduct subProduct);
    
    void updateSubProduct(@MappingTarget SubProduct subProduct, SubProductCreationRequest request);
}