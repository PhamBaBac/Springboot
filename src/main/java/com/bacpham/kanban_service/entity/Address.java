package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "address")
public class Address extends BaseModel {

    private String name; // tên người nhận
    private String phoneNumber;

    private String address; // ví dụ: "Xóm 6, Xã Trù Sơn, Huyện Đô Lương, Nghệ An"

    private String province;
    private String district;
    private String ward;

    private boolean isDefault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // foreign key
    private User createdBy;
}

