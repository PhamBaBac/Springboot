package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.MediaRequest;
import com.bacpham.kanban_service.dto.response.MediaResponse;
import com.bacpham.kanban_service.entity.Media;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface MediaMapper {

    Media toMedia(MediaRequest request);

    MediaResponse toMediaResponse(Media media);

    void updateMedia(@MappingTarget Media media, MediaRequest request);
}
