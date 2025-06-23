package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.AddressCreateRequest;
import com.bacpham.kanban_service.dto.response.AddressResponse;
import com.bacpham.kanban_service.entity.Address;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.AddressMapper;
import com.bacpham.kanban_service.repository.AddressRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressServiceImpl implements IAddressService {
    private final AddressRepository addressRepository;
    private  final AddressMapper addressMapper;
    private final UserRepository userRepository;

    @Override
    public AddressResponse createAddress(AddressCreateRequest request , String userId) {
        var address = addressMapper.toAddress(request);

        var savedAddress = addressRepository.save(address);

        return addressMapper.toResponse(savedAddress);

    }
    @Override
    public List<AddressResponse> getAddresses(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Address> addresses = addressRepository.findByCreatedBy(user);

        return addresses.stream()
                .map(addressMapper::toResponse)
                .collect(Collectors.toList());
    }
}
