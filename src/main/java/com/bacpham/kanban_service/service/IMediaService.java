package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.MediaRequest;
import com.bacpham.kanban_service.dto.response.MediaResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IMediaService {

    MediaResponse saveMedia(MediaRequest request);

    List<MediaResponse> saveAllMedias(List<MediaRequest> requests);

    PageResponse<MediaResponse> getAllMedias(String search, Pageable pageable);

    void deleteMedia(String id);
}
