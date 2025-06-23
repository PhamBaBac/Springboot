package com.bacpham.kanban_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    private String id; // ID của địa chỉ
    private String name; // Tên người nhận
    private String phoneNumber; // Số điện thoại người nhận
    private String address; // Địa chỉ chi tiết
    private String province; // Tỉnh/Thành phố
    private String district; // Quận/Huyện
    private String ward; // Phường/Xã
    private boolean isDefault; // Địa chỉ mặc định hay không
}
