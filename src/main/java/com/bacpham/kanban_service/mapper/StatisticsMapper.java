package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.StatisticsOrderResponse;
import com.bacpham.kanban_service.dto.response.StatisticsResponse;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.SubProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface StatisticsMapper {
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "subProduct.id", target = "subProductId")
    @Mapping(source = "subProduct.product.title", target = "title")
    @Mapping(source = "subProduct.size", target = "size")
    @Mapping(source = "quantity", target = "qty")
    @Mapping(source = "subProduct.cost", target = "cost")
    @Mapping(source = "subProduct.price", target = "price")
    @Mapping(source = "priceAtOrderTime", target = "discount")
    @Mapping(source = "subProduct", target = "image", qualifiedByName = "mapSubProduct")
    @Mapping(target = "totalPrice", expression = "java(item.getPriceAtOrderTime() * item.getQuantity())")
    @Mapping(source = "order.orderStatus", target = "orderStatus")
    StatisticsOrderResponse toStatisticsOrderResponse(OrderItem item);

    @Named("mapSubProduct")
    default String mapSubProduct(SubProduct subProduct) {
        if (subProduct.getImages() != null && !subProduct.getImages().isEmpty()) {
            return subProduct.getImages().get(0);
        }
        return null;
    }
}
