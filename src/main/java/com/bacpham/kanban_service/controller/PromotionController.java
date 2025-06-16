package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ApplyPromotionRequest;
import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.service.impl.PromotionServiceImpl;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class PromotionController {

    PromotionServiceImpl promotionService;

    @PostMapping("/addNew")
    public ApiResponse<PromotionResponse> createPromotion(
            @RequestBody @Validated PromotionRequest request
    ) {
        PromotionResponse created = promotionService.createPromotion(request);
        return ApiResponse.<PromotionResponse>builder()
                .result(created)
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<PromotionResponse> updatePromotion(
            @PathVariable String id,
            @RequestBody @Validated PromotionRequest request
    ) {
        PromotionResponse updated = promotionService.updatePromotion(id, request);
        return ApiResponse.<PromotionResponse>builder()
                .result(updated)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getPromotionById(@PathVariable String id) {
        PromotionResponse response = promotionService.getPromotionById(id);
        return ApiResponse.<PromotionResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping
    public ApiResponse<List<PromotionResponse>> getAllPromotions() {
        return ApiResponse.<List<PromotionResponse>>builder()
                .result(promotionService.getAllPromotions())
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePromotion(@PathVariable String id) {
        promotionService.deletePromotion(id);
        return ApiResponse.<Void>builder().message("Deleted successfully").build();
    }
    @GetMapping("/validate")
    public ApiResponse<Boolean> validatePromotion(@RequestParam String code) {
        boolean isValid = promotionService.isPromotionValid(code);
        return ApiResponse.<Boolean>builder()
                .result(isValid)
                .message(isValid ? "Valid promotion" : "Invalid or expired promotion")
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
                .result(applied)
                .message(applied ? "Promotion applied successfully" : "Promotion already used")
                .build();
    }

}
