package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.response.AddressResponse;
import com.bacpham.kanban_service.entity.Address;

import java.util.List;

public interface IAddressService {
    AddressResponse createAddress(AddressCreateRequest request, String userId);
    List<AddressResponse> getAddresses(String userId);

//    Address getAddressById(String id);
//
//    void updateAddress(String id, AddressCreateRequest request);
//
//    void deleteAddress(String id);
}
