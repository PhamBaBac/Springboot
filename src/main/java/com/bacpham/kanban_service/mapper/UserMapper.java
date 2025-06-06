package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
//    @Mapping(target = "id", ignore = true)
    UserResponse toUserResponse(User user);
}
