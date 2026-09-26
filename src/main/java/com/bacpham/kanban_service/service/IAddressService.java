package com.bacpham.kanban_service.service;

import java.util.List;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.response.AddressResponse;

public interface IAddressService {
    AddressResponse createAddress(AddressCreateRequest request, String userId);

    List<AddressResponse> getAddresses(String userId);

}
