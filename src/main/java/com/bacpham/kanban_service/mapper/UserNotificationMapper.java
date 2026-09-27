package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.UserNotificationResponse;
import com.bacpham.kanban_service.entity.UserNotification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserNotificationMapper {

    UserNotificationResponse toResponse(UserNotification notification);

    List<UserNotificationResponse> toResponseList(List<UserNotification> notifications);
}
