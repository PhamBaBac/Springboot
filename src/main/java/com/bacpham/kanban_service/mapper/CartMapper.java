package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Cart;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;


@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(source = "createdBy", target = "createdBy")
    @Mapping(source = "subProductId", target = "subProduct", qualifiedByName = "mapSubProduct")
    Cart toEntity(CartCreateRequest request);

    @Mapping(source = "subProduct.id", target = "subProductId")
    CartResponse toResponse(Cart cart);

    @Named("mapSubProduct")
    default SubProduct mapSubProduct(String id) {
        if (id == null) return null;
        SubProduct sub = new SubProduct();
        sub.setId(id);
        return sub;
    }

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

    default String map(SubProduct sub) {
        return sub == null ? null : sub.getId();
    }

    default String map(User user) {
        return user == null ? null : user.getId();
    }
}
