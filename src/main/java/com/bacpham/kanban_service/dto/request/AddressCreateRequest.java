package com.bacpham.kanban_service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isDefault")
    @JsonAlias({"isDefault", "default"})
    private Boolean isDefault;

    public boolean isDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
