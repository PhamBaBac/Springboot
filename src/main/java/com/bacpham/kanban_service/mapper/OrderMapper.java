package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.OrderDetailResponse;
import com.bacpham.kanban_service.dto.response.OrderResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.SubProduct;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(source = "user.firstname", target = "userName")
    @Mapping(source = "address.name", target = "nameRecipient")
    @Mapping(source = "address.address", target = "address")
    @Mapping(source = "address.phoneNumber", target = "phoneNumber")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "paymentType", target = "paymentType")
    @Mapping(source = "orderStatus", target = "orderStatus")
    @Mapping(source = "items", target = "orderResponses")
    @Mapping(source = "createdAt", target = "createdAt")
    OrderDetailResponse toOrderDetailResponse(Order order);

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "subProduct.product.title", target = "title")
    @Mapping(source = "subProduct.size", target = "size")
    @Mapping(source = "quantity", target = "qty")
    @Mapping(source = "priceAtOrderTime", target = "price")
    @Mapping(source = "subProduct", target = "image", qualifiedByName = "mapFirstImage")
    @Mapping(target = "totalPrice", expression = "java(item.getPriceAtOrderTime() * item.getQuantity())")
    @Mapping(source = "order.orderStatus", target = "orderStatus")
    OrderResponse toOrderResponse(OrderItem item);

    @Named("mapFirstImage")
    default String mapFirstImage(SubProduct subProduct) {
        if (subProduct.getImages() != null && !subProduct.getImages().isEmpty()) {
            return subProduct.getImages().get(0);
        }
        return null;
    }

}
