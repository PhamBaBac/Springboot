package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.BillResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.impl.BillServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class  BillController {
    private final BillServiceImpl billService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    public ApiResponse<?> createBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String paymentType
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        billService.createBillFromCart(userId, paymentType);
       return  ApiResponse.builder()
               .message("Bill created successfully")
               .build();
    }
    @GetMapping("/listBills")
    public ApiResponse<List<BillResponse>> getBillById(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        return ApiResponse.<List<BillResponse>>builder()
                .result(billService.getBillsByUserId(userId))
                .message("Bill retrieved successfully")
                .build();
    }
}
