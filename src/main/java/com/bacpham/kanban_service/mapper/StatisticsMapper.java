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
    @Mapping(source = "item", target = "subProductId", qualifiedByName = "resolveStatSubProductId")
    @Mapping(source = "item", target = "title", qualifiedByName = "resolveStatTitle")
    @Mapping(source = "item", target = "size", qualifiedByName = "resolveStatSize")
    @Mapping(source = "quantity", target = "qty")
    @Mapping(source = "item", target = "cost", qualifiedByName = "resolveStatCost")
    @Mapping(source = "item", target = "price", qualifiedByName = "resolveStatPrice")
    @Mapping(source = "priceAtOrderTime", target = "discount")
    @Mapping(source = "item", target = "image", qualifiedByName = "resolveStatImage")
    @Mapping(source = "item", target = "totalPrice", qualifiedByName = "resolveStatTotalPrice")
    @Mapping(source = "order.orderStatus", target = "orderStatus")
    StatisticsOrderResponse toStatisticsOrderResponse(OrderItem item);

    @Named("resolveStatSubProductId")
    default String resolveStatSubProductId(OrderItem item) {
        if (item.getSkuCode() != null && !item.getSkuCode().isBlank()) {
            return item.getSkuCode();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getId() : null;
    }

    @Named("resolveStatTitle")
    default String resolveStatTitle(OrderItem item) {
        if (item.getProductTitle() != null && !item.getProductTitle().isBlank()) {
            return item.getProductTitle();
        }
        if (item.getSubProduct() != null && item.getSubProduct().getProduct() != null) {
            return item.getSubProduct().getProduct().getTitle();
        }
        return null;
    }

    @Named("resolveStatSize")
    default String resolveStatSize(OrderItem item) {
        if (item.getSize() != null && !item.getSize().isBlank()) {
            return item.getSize();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getSize() : null;
    }

    @Named("resolveStatCost")
    default Double resolveStatCost(OrderItem item) {
        if (item.getCost() != null) {
            return item.getCost();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getCost() : null;
    }

    @Named("resolveStatPrice")
    default Double resolveStatPrice(OrderItem item) {
        if (item.getOriginalPrice() != null) {
            return item.getOriginalPrice();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getPrice() : null;
    }

    @Named("resolveStatImage")
    default String resolveStatImage(OrderItem item) {
        if (item.getImage() != null && !item.getImage().isBlank()) {
            return item.getImage();
        }
        return mapSubProduct(item.getSubProduct());
    }

    @Named("resolveStatTotalPrice")
    default double resolveStatTotalPrice(OrderItem item) {
        if (item.getTotalPrice() != null) {
            return item.getTotalPrice();
        }
        double price = item.getPriceAtOrderTime() != null ? item.getPriceAtOrderTime() : 0.0;
        int qty = item.getQuantity() != null ? item.getQuantity() : 0;
        return price * qty;
    }

    @Named("mapSubProduct")
    default String mapSubProduct(SubProduct subProduct) {
        if (subProduct != null && subProduct.getImages() != null && !subProduct.getImages().isEmpty()) {
            return subProduct.getImages().get(0);
        }
        return null;
    }
}
