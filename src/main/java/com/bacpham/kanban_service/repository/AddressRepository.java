package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Address;
import com.bacpham.kanban_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, String> {
    List<Address> findByCreatedBy(User user);
}
