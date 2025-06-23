package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.response.AddressResponse;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Address;
import com.bacpham.kanban_service.entity.Cart;
import com.bacpham.kanban_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AddressMapper {
  @Mapping(source = "createdBy", target = "createdBy")
  Address toAddress(AddressCreateRequest request);

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


  @Mapping(source = "id", target = "id")
  AddressResponse toResponse(Address address);

}
