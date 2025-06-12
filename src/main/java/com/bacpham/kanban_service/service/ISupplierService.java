package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.SupplierRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.SupplierResponse;

import java.util.Date;
import java.util.List;

public interface ISupplierService {
    SupplierResponse createSupplier(SupplierRequest request);
    PageResponse<SupplierResponse> getSupplierResponsePage(int page, int pageSize);
    void deleteSupplier(String id);
    SupplierResponse updateSupplier(String id, SupplierRequest request);
    List<SupplierResponse> findAllSupplier();
    List<SupplierResponse> findSuppliersByDateRange(Date start, Date end);
}
