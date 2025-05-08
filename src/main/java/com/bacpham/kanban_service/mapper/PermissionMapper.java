package com.bacpham.kanban_service.mapper;


import com.bacpham.kanban_service.dto.request.PermissionRequest;
import com.bacpham.kanban_service.dto.response.PermissionResponse;
import com.bacpham.kanban_service.entity.Permission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}
