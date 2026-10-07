package com.bacpham.kanban_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isDefault")
    private Boolean isDefault;

    @JsonProperty("default")
    public Boolean getDefault() {
        return isDefault;
    }

    public boolean isDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
