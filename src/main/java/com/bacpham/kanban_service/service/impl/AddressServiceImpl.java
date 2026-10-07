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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressServiceImpl implements IAddressService {
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddressResponse createAddress(AddressCreateRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        var address = addressMapper.toAddress(request);
        address.setCreatedBy(user);

        List<Address> existingAddresses = addressRepository.findByCreatedBy(user);

        boolean shouldBeDefault = request.isDefault() || existingAddresses.isEmpty();
        address.setDefault(shouldBeDefault);

        if (shouldBeDefault) {
            for (Address existing : existingAddresses) {
                if (existing.isDefault()) {
                    existing.setDefault(false);
                    addressRepository.save(existing);
                }
            }
        }

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

    @Override
    @Transactional
    public AddressResponse updateAddress(String id, AddressCreateRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        address.setName(request.getName());
        address.setPhoneNumber(request.getPhoneNumber());
        address.setAddress(request.getAddress());
        address.setProvince(request.getProvince());
        address.setDistrict(request.getDistrict());
        address.setWard(request.getWard());

        if (request.isDefault()) {
            List<Address> existingAddresses = addressRepository.findByCreatedBy(user);
            for (Address existing : existingAddresses) {
                if (!existing.getId().equals(address.getId()) && existing.isDefault()) {
                    existing.setDefault(false);
                    addressRepository.save(existing);
                }
            }
            address.setDefault(true);
        }

        var savedAddress = addressRepository.save(address);
        return addressMapper.toResponse(savedAddress);
    }

    @Override
    @Transactional
    public void deleteAddress(String id, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            List<Address> remaining = addressRepository.findByCreatedBy(user);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(String id, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address targetAddress = addressRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        List<Address> addresses = addressRepository.findByCreatedBy(user);
        for (Address addr : addresses) {
            boolean isTarget = addr.getId().equals(targetAddress.getId());
            if (addr.isDefault() != isTarget) {
                addr.setDefault(isTarget);
                addressRepository.save(addr);
            }
        }

        targetAddress.setDefault(true);
        return addressMapper.toResponse(targetAddress);
    }
}
