package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.FormItem;
import com.bacpham.kanban_service.dto.request.SupplierFormDTO;
import com.bacpham.kanban_service.dto.request.SupplierRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.SupplierResponse;
import com.bacpham.kanban_service.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class SupplierController {
     SupplierService supplierService;

    @GetMapping("/get-form")
    public SupplierFormDTO getSupplierForm() {
        // Tạo các FormItemDTO cho form
        List<FormItem> formItems = Arrays.asList(
                new FormItem("name", "name", "Supplier name", "Enter supplier name", "default", true, "Enter supplier name", "", 400, null),
                new FormItem("email", "email", "Supplier Email", "Enter supplier Email", "default", false, "", "", 150, null),
                new FormItem("active", "active", "Supplier active", "Enter supplier active", "number", false, "", "", 150, null),
                new FormItem("products", "products", "Supplier product", "Enter supplier product", "default", false, "Enter supplier product", "", 150, null),
                new FormItem("categories", "categories", "Categories", "Select product category", "select", false, "", "", 150, null),
                new FormItem("price", "price", "Buying price", "Enter buying price", "number", false, "", "", 150, null),
                new FormItem("contact", "contact", "Contact Number", "Enter supplier contact number", "tel", false, "", "", 150, null),
                new FormItem("type", "isTaking", "Talking", "", "checkbox", false, "", null, 150, null)
        );

        SupplierFormDTO form = new SupplierFormDTO();
        form.setTitle("Supplier");
        form.setLayout("horizontal");
        form.setLabelCol(6);
        form.setWrapperCol(18);
        form.setFormItems(formItems);

        return form;
    }


     @PostMapping("/add-new")
     ApiResponse<SupplierResponse> createSupplier(@RequestBody @Validated SupplierRequest request) {
         return ApiResponse.<SupplierResponse>builder()
                 .result(supplierService.createSupplier(request))
                 .build();
     }


    @GetMapping("/page")
    ApiResponse<PageResponse<SupplierResponse>> productPage(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.<PageResponse<SupplierResponse>>builder()
                .result(supplierService.getSupplierResponsePage(page, pageSize))
                .build();
    }

    @DeleteMapping("/remove")
    ApiResponse<Void> deleteSupplier(@RequestParam (value = "id", required = false) String id) {
        supplierService.deleteSupplier(id);
        return ApiResponse.<Void>builder().build();
    }

}
