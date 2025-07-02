package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "address")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends BaseModel {

    String name; // tên người nhận
    String phoneNumber;

    String address; // ví dụ: "Xóm 6, Xã Trù Sơn, Huyện Đô Lương, Nghệ An"

    String province;
    String district;
    String ward;

    boolean isDefault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // foreign key
    User createdBy;
}

