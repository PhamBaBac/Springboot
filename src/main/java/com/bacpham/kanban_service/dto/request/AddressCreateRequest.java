package com.bacpham.kanban_service.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AddressCreateRequest {
    private String createdBy; 
    private String name; 
    private String phoneNumber;
    private String address; 
    private String province;
    private String district;
    private String ward;
    private boolean isDefault; 
}
