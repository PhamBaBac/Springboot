package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.BillResponse;
import com.bacpham.kanban_service.entity.BillItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface BillMapper {

    @Mapping(source = "subProduct.product.title", target = "title")
    @Mapping(source = "subProduct.size", target = "size")
    @Mapping(source = "quantity", target = "qty")
    @Mapping(source = "priceAtOrderTime", target = "price")
    @Mapping(source = "subProduct", target = "image", qualifiedByName = "mapFirstImage")
    @Mapping(target = "totalPrice", expression = "java(item.getPriceAtOrderTime() * item.getQuantity())")
    BillResponse toBillResponse(BillItem item);

    @Named("mapFirstImage")
    default String mapFirstImage(com.bacpham.kanban_service.entity.SubProduct subProduct) {
        if (subProduct.getImages() != null && !subProduct.getImages().isEmpty()) {
            return subProduct.getImages().get(0); // lấy ảnh đầu tiên
        }
        return null;
    }
}


