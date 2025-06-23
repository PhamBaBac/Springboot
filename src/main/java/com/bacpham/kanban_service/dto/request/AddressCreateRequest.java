package com.bacpham.kanban_service.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AddressCreateRequest {
    private String createdBy; // ID của người tạo địa chỉ, thường là userId
    private String name; // tên người nhận
    private String phoneNumber;
    private String address; // ví dụ: "Xóm 6, Xã Trù Sơn, Huyện Đô Lương, Nghệ An"
    private String province;
    private String district;
    private String ward;
    private boolean isDefault; // địa chỉ mặc định hay không
}
