package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.BillResponse;
import com.bacpham.kanban_service.entity.Bill;

import java.util.List;

public interface IBillService {
    Bill createBillFromCart(String userId, String paymentType);
    List<BillResponse> getBillsByUserId(String userId);


}