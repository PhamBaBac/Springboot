package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.entity.SupportMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupportMessageMapper {

   SupportMessage toSupportMessage(SupportMessageRequest request);

   SupportMessageResponse toSupportMessageResponse(SupportMessage supportMessage);
}