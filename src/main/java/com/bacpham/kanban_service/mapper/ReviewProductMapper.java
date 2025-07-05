package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ReviewProductMapper {

    @Mapping(target = "subProduct", source = "subProductId")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "order", source = "orderId")
    Review toEntity(ReviewProductRequest request);

    @Mapping(source = "createdBy.id", target = "createdBy")
    @Mapping(source = "createdBy.firstname", target = "userFirstname")
    @Mapping(source = "createdBy.lastname", target = "userLastname")
    @Mapping(source = "createdBy.avatarUrl", target = "userAvatar")
    @Mapping(source = "subProduct.id", target = "subProductId")
    @Mapping(source = "subProduct.color", target = "color")
    @Mapping(source = "subProduct.size", target = "size")
    ReviewProductResponse toResponse(Review review);

    default User map(String createdBy) {
        try {
            UUID uuid = UUID.fromString(createdBy);
            User user = new User();
            user.setId(uuid.toString());
            return user;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    default SubProduct mapSubProduct(String subProductId) {
        try {
            UUID uuid = UUID.fromString(subProductId);
            SubProduct subProduct = new SubProduct();
            subProduct.setId(uuid.toString());
            return subProduct;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    default Order mapOrder(String orderId) {
        try {
            UUID uuid = UUID.fromString(orderId);
            Order order = new Order();
            order.setId(uuid.toString());
            return order;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
