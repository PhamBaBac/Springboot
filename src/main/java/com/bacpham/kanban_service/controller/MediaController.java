package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.MediaRequest;
import com.bacpham.kanban_service.dto.response.MediaResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.service.IMediaService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medias")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class MediaController {

    IMediaService mediaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MediaResponse> saveMedia(@RequestBody @Validated MediaRequest request) {
        return ApiResponse.<MediaResponse>builder()
                .data(mediaService.saveMedia(request))
                .build();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MediaResponse>> saveAllMedias(@RequestBody List<MediaRequest> requests) {
        return ApiResponse.<List<MediaResponse>>builder()
                .data(mediaService.saveAllMedias(requests))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<MediaResponse>> getAllMedias(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "24") int size
    ) {
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.<PageResponse<MediaResponse>>builder()
                .data(mediaService.getAllMedias(search, pageable))
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteMedia(@PathVariable String id) {
        mediaService.deleteMedia(id);
        return ApiResponse.<Void>builder().build();
    }
}
