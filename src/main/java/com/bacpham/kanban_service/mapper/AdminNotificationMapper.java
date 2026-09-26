package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.entity.AdminNotification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminNotificationMapper {

    @Mapping(target = "isRead", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    AdminNotification toAdminNotification(AdminNotificationRequest request);

    AdminNotificationResponse toAdminNotificationResponse(AdminNotification adminNotification);
}
