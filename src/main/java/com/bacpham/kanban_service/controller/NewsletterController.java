package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.EmailRequest;
import com.bacpham.kanban_service.entity.Newsletter;
import com.bacpham.kanban_service.entity.Promotion;
import com.bacpham.kanban_service.repository.NewsletterRepository;
import com.bacpham.kanban_service.repository.PromotionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/public/newsletter")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class NewsletterController {

    NewsletterRepository newsletterRepository;
    PromotionRepository promotionRepository;

    static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @PostMapping("/subscribe")
    public ApiResponse<Map<String, Object>> subscribe(@RequestBody EmailRequest request) {
        String email = request != null && request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            return ApiResponse.<Map<String, Object>>builder()
                    .code(400)
                    .message("Địa chỉ email không hợp lệ!")
                    .build();
        }

        boolean alreadySubscribed = newsletterRepository.existsByEmail(email);
        if (!alreadySubscribed) {
            Newsletter newsletter = Newsletter.builder()
                    .email(email)
                    .active(true)
                    .build();
            newsletterRepository.save(newsletter);
        }

        // Lấy mã khuyến mãi khả dụng để gửi tặng khách hàng đăng ký
        List<Promotion> promotions = promotionRepository.findAllByDeletedFalse();
        String promoCode = "WELCOME10";
        Object promoValue = 10;
        if (!promotions.isEmpty()) {
            Promotion firstPromo = promotions.get(0);
            promoCode = firstPromo.getCode();
            promoValue = firstPromo.getValue();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("promoCode", promoCode);
        result.put("discountValue", promoValue);
        result.put("isNew", !alreadySubscribed);

        String message = alreadySubscribed
                ? "Email đã đăng ký trước đó! Mã ưu đãi của bạn: " + promoCode
                : "Đăng ký nhận ưu đãi thành công! Mã ưu đãi của bạn: " + promoCode;

        return ApiResponse.<Map<String, Object>>builder()
                .data(result)
                .message(message)
                .build();
    }
}
