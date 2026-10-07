package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.AddressResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {
        private final IAddressService addressService;
        private final UserRepository userRepository;

        @PostMapping("/create")
        public ApiResponse<AddressResponse> createAddress(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestBody AddressCreateRequest request) {

                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                String userId = user.getId();
                AddressResponse address = addressService.createAddress(request, userId);
                return ApiResponse.<AddressResponse>builder()
                                .data(address)
                                .message("Tạo địa chỉ thành công")
                                .build();
        }

        @GetMapping("/all")
        public ApiResponse<List<AddressResponse>> getAllAddress(@AuthenticationPrincipal UserDetails userDetails) {
                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                String userId = user.getId();
                List<AddressResponse> addresses = addressService.getAddresses(userId);
                return ApiResponse.<List<AddressResponse>>builder()
                                .message("Lấy danh sách địa chỉ thành công")
                                .data(addresses)
                                .build();
        }

        @PutMapping({"/update-address", "/{id}"})
        public ApiResponse<AddressResponse> updateAddress(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestParam(value = "id", required = false) String paramId,
                        @PathVariable(value = "id", required = false) String pathId,
                        @RequestBody AddressCreateRequest request) {
                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                String addressId = paramId != null && !paramId.isBlank() ? paramId : pathId;
                AddressResponse address = addressService.updateAddress(addressId, request, user.getId());
                return ApiResponse.<AddressResponse>builder()
                                .data(address)
                                .message("Cập nhật địa chỉ thành công")
                                .build();
        }

        @DeleteMapping("/{id}")
        public ApiResponse<Void> deleteAddress(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable String id) {
                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                addressService.deleteAddress(id, user.getId());
                return ApiResponse.<Void>builder()
                                .message("Xóa địa chỉ thành công")
                                .build();
        }

        @RequestMapping(value = "/{id}/set-default", method = {RequestMethod.PATCH, RequestMethod.PUT})
        public ApiResponse<AddressResponse> setDefaultAddress(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable String id) {
                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                AddressResponse address = addressService.setDefaultAddress(id, user.getId());
                return ApiResponse.<AddressResponse>builder()
                                .data(address)
                                .message("Đặt làm địa chỉ mặc định thành công")
                                .build();
        }
}
