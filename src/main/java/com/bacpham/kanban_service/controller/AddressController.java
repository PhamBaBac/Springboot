package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.AddressResponse;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.impl.AddressServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {
    private final AddressServiceImpl addressService;
    private final UserRepository userRepository;
    @PostMapping("/create")
    public ApiResponse<AddressResponse> createAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AddressCreateRequest request
    ) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        AddressResponse address = addressService.createAddress(request, userId);
        return  ApiResponse.<AddressResponse>builder()
                .result(address)
                .message("Address created successfully")
                .build();
    }

    @GetMapping("/all")
    public ApiResponse<List<AddressResponse>> getAllAddress( @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        List<AddressResponse> addresses = addressService.getAddresses(userId);
        return ApiResponse.<List<AddressResponse>>builder()
                .message("Fetched cart successfully")
                .result(addresses)
                .build();
    }
}
