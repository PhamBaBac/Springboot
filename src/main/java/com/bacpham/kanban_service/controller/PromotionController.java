package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ApplyPromotionRequest;
import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.service.IPromotionService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class PromotionController {

    IPromotionService promotionService;

    @PostMapping("/addNew")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PromotionResponse> createPromotion(
            @RequestBody @Validated PromotionRequest request
    ) {
        PromotionResponse created = promotionService.createPromotion(request);
        return ApiResponse.<PromotionResponse>builder()
                .data(created)
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PromotionResponse> updatePromotion(
            @PathVariable String id,
            @RequestBody @Validated PromotionRequest request
    ) {
        PromotionResponse updated = promotionService.updatePromotion(id, request);
        return ApiResponse.<PromotionResponse>builder()
                .data(updated)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getPromotionById(@PathVariable String id) {
        PromotionResponse response = promotionService.getPromotionById(id);
        return ApiResponse.<PromotionResponse>builder()
                .data(response)
                .build();
    }

    @GetMapping
    public ApiResponse<List<PromotionResponse>> getAllPromotions() {
        return ApiResponse.<List<PromotionResponse>>builder()
                .data(promotionService.getAllPromotions())
                .build();
    }
    @GetMapping("/code/{code}")
    public ApiResponse<PromotionResponse> getPromotionByCode(@PathVariable String code) {
        log.info("getPromotionByCode:", code);
        PromotionResponse response = promotionService.getPromotionByNameCode(code);
        return ApiResponse.<PromotionResponse>builder()
                .data(response)
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePromotion(@PathVariable String id) {
        promotionService.deletePromotion(id);
        return ApiResponse.<Void>builder().message("Xóa mã khuyến mãi thành công").build();
    }

    @GetMapping("/check/{code}")
    public ApiResponse<Boolean> checkPromotionCode(
            @PathVariable String code
    ) {
        boolean isValid = promotionService.isPromotionValid(code);
        return ApiResponse.<Boolean>builder()
                .data(isValid)
                .message(isValid ? "Mã khuyến mãi hợp lệ" : "Mã khuyến mãi không hợp lệ hoặc đã hết hạn")
                .build();
    }


    @PostMapping("/apply")
    public ApiResponse<Boolean> applyPromotion(
            @RequestBody ApplyPromotionRequest request
    ) {
        String userId = request.getUserId();
        String code = request.getCode();
        boolean applied = promotionService.applyPromotionCode(userId, code);
        return ApiResponse.<Boolean>builder()
                .data(applied)
                .message(applied ? "Áp dụng mã khuyến mãi thành công" : "Mã khuyến mãi đã được sử dụng")
                .build();
    }

}
