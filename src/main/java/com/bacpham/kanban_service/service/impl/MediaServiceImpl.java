package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.MediaRequest;
import com.bacpham.kanban_service.dto.response.MediaResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.entity.Media;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.MediaMapper;
import com.bacpham.kanban_service.repository.MediaRepository;
import com.bacpham.kanban_service.service.IMediaService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class MediaServiceImpl implements IMediaService {

    MediaRepository mediaRepository;
    MediaMapper mediaMapper;

    @Override
    @Transactional
    public MediaResponse saveMedia(MediaRequest request) {
        // Kiểm tra xem URL đã tồn tại chưa để tránh tạo bản ghi trùng lặp
        Optional<Media> existing = mediaRepository.findByUrlAndDeletedFalse(request.getUrl());
        if (existing.isPresent()) {
            return mediaMapper.toMediaResponse(existing.get());
        }

        Media media = mediaMapper.toMedia(request);
        media.setCreatedBy(getCurrentUser());
        media = mediaRepository.save(media);
        log.info("Saved media: id={}, url={}", media.getId(), media.getUrl());
        return mediaMapper.toMediaResponse(media);
    }

    @Override
    @Transactional
    public List<MediaResponse> saveAllMedias(List<MediaRequest> requests) {
        List<MediaResponse> responses = new ArrayList<>();
        if (requests == null || requests.isEmpty()) {
            return responses;
        }

        for (MediaRequest req : requests) {
            responses.add(saveMedia(req));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MediaResponse> getAllMedias(String search, Pageable pageable) {
        Page<Media> pageData;
        if (search != null && !search.trim().isEmpty()) {
            pageData = mediaRepository.findByFileNameContainingIgnoreCaseAndDeletedFalse(search.trim(), pageable);
        } else {
            pageData = mediaRepository.findAllByDeletedFalse(pageable);
        }

        List<MediaResponse> content = pageData.getContent().stream()
                .map(mediaMapper::toMediaResponse)
                .toList();

        return PageResponse.<MediaResponse>builder()
                .currentPage(pageData.getNumber() + 1)
                .totalPages(pageData.getTotalPages())
                .pageSize(pageData.getSize())
                .totalElements(pageData.getTotalElements())
                .data(content)
                .build();
    }

    @Override
    @Transactional
    public void deleteMedia(String id) {
        Media media = mediaRepository.findById(id)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        media.setDeleted(true);
        mediaRepository.save(media);
        log.info("Soft deleted media id={}", id);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
