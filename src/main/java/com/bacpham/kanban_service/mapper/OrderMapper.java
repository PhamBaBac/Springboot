package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.OrderDetailResponse;
import com.bacpham.kanban_service.dto.response.OrderResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.SubProduct;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(source = "user.firstname", target = "userName")
    @Mapping(source = "order", target = "nameRecipient", qualifiedByName = "resolveRecipientName")
    @Mapping(source = "order", target = "address", qualifiedByName = "resolveAddress")
    @Mapping(source = "order", target = "phoneNumber", qualifiedByName = "resolvePhoneNumber")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "paymentType", target = "paymentType")
    @Mapping(source = "orderStatus", target = "orderStatus")
    @Mapping(source = "items", target = "orderResponses")
    @Mapping(source = "cancelReason", target = "cancelReason")
    @Mapping(source = "trackingCode", target = "trackingCode")
    @Mapping(source = "shippingStatus", target = "shippingStatus")
    @Mapping(source = "createdAt", target = "createdAt")
    OrderDetailResponse toOrderDetailResponse(Order order);

    @Mapping(source = "id", target = "orderItemId")
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "item", target = "subProductId", qualifiedByName = "resolveSubProductId")
    @Mapping(source = "item", target = "title", qualifiedByName = "resolveTitle")
    @Mapping(source = "item", target = "size", qualifiedByName = "resolveSize")
    @Mapping(source = "item", target = "color", qualifiedByName = "resolveColor")
    @Mapping(source = "item", target = "attributes", qualifiedByName = "resolveAttributes")
    @Mapping(source = "quantity", target = "qty")
    @Mapping(source = "priceAtOrderTime", target = "price")
    @Mapping(source = "item", target = "image", qualifiedByName = "resolveImage")
    @Mapping(source = "item", target = "totalPrice", qualifiedByName = "resolveTotalPrice")
    @Mapping(source = "order.orderStatus", target = "orderStatus")
    @Mapping(source = "order.trackingCode", target = "trackingCode")
    @Mapping(source = "originalPrice", target = "originalPrice")
    @Mapping(source = "cost", target = "cost")
    @Mapping(source = "discountAmount", target = "discountAmount")
    @Mapping(source = "skuCode", target = "skuCode")
    OrderResponse toOrderResponse(OrderItem item);

    @Named("resolveRecipientName")
    default String resolveRecipientName(Order order) {
        if (order.getRecipientName() != null && !order.getRecipientName().isBlank()) {
            return order.getRecipientName();
        }
        return order.getAddress() != null ? order.getAddress().getName() : null;
    }

    @Named("resolvePhoneNumber")
    default String resolvePhoneNumber(Order order) {
        if (order.getRecipientPhone() != null && !order.getRecipientPhone().isBlank()) {
            return order.getRecipientPhone();
        }
        return order.getAddress() != null ? order.getAddress().getPhoneNumber() : null;
    }

    @Named("resolveAddress")
    default String resolveAddress(Order order) {
        if (order.getShippingAddress() != null && !order.getShippingAddress().isBlank()) {
            return order.getShippingAddress();
        }
        return order.getAddress() != null ? order.getAddress().getAddress() : null;
    }

    @Named("resolveSubProductId")
    default String resolveSubProductId(OrderItem item) {
        if (item.getSubProduct() != null && item.getSubProduct().getId() != null) {
            return item.getSubProduct().getId();
        }
        return item.getSkuCode();
    }

    @Named("resolveTitle")
    default String resolveTitle(OrderItem item) {
        if (item.getProductTitle() != null && !item.getProductTitle().isBlank()) {
            return item.getProductTitle();
        }
        if (item.getSubProduct() != null && item.getSubProduct().getProduct() != null) {
            return item.getSubProduct().getProduct().getTitle();
        }
        return null;
    }

    @Named("resolveSize")
    default String resolveSize(OrderItem item) {
        if (item.getSize() != null && !item.getSize().isBlank()) {
            return item.getSize();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getSize() : null;
    }

    @Named("resolveColor")
    default String resolveColor(OrderItem item) {
        if (item.getColor() != null && !item.getColor().isBlank()) {
            return item.getColor();
        }
        return item.getSubProduct() != null ? item.getSubProduct().getColor() : null;
    }

    @Named("resolveAttributes")
    default Map<String, String> resolveAttributes(OrderItem item) {
        Map<String, String> raw = (item.getAttributesSnapshot() != null && !item.getAttributesSnapshot().isEmpty())
                ? item.getAttributesSnapshot()
                : (item.getSubProduct() != null ? item.getSubProduct().getAttributes() : null);
        if (raw == null) {
            return null;
        }
        Map<String, String> filtered = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key != null) {
                String normalized = key.trim().toLowerCase().replaceAll("[-_]", "");
                if ("discounttype".equals(normalized)
                        || "discountvalue".equals(normalized)
                        || "discountamount".equals(normalized)
                        || "discount".equals(normalized)
                        || "price".equals(normalized)
                        || "cost".equals(normalized)
                        || "stock".equals(normalized)
                        || "qty".equals(normalized)
                        || "reservedstock".equals(normalized)) {
                    continue;
                }
                filtered.put(key, entry.getValue());
            }
        }
        return filtered;
    }

    @Named("resolveImage")
    default String resolveImage(OrderItem item) {
        if (item.getImage() != null && !item.getImage().isBlank()) {
            return item.getImage();
        }
        return mapFirstImage(item.getSubProduct());
    }

    @Named("resolveTotalPrice")
    default double resolveTotalPrice(OrderItem item) {
        if (item.getTotalPrice() != null) {
            return item.getTotalPrice();
        }
        double price = item.getPriceAtOrderTime() != null ? item.getPriceAtOrderTime() : 0.0;
        int qty = item.getQuantity() != null ? item.getQuantity() : 0;
        return price * qty;
    }

    @Named("mapFirstImage")
    default String mapFirstImage(SubProduct subProduct) {
        if (subProduct != null && subProduct.getImages() != null && !subProduct.getImages().isEmpty()) {
            return subProduct.getImages().get(0);
        }
        return null;
    }
}
