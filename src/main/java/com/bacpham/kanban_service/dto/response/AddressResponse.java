package com.bacpham.kanban_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    private String id;  
    private String name; 
    private String phoneNumber; 
    private String address; 
    private String province; 
    private String district; 
    private String ward; 
    private boolean isDefault; 
}
