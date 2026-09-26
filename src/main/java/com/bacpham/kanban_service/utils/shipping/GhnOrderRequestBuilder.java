package com.bacpham.kanban_service.utils.shipping;

import com.bacpham.kanban_service.entity.Address;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.Shipment;
import com.bacpham.kanban_service.entity.ShipmentItem;
import com.bacpham.kanban_service.enums.PaymentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder tao request body cho GHN Open API tao van don.
 * Tach ra khoi GhnShippingService de tuan thu:
 * - SRP: Service chi lo HTTP call; builder chi lo xay dung payload.
 * - OCP: De dang mo rong logic xay dung request ma khong can chuong service.
 * - Testability: Test doc lap viec build request ma khong can mock HTTP.
 */
@Component
@Slf4j
public class GhnOrderRequestBuilder {

    /**
     * Xay dung request body tu doi tuong Shipment (thong so thuc te dong goi)
     */
    public Map<String, Object> buildFromShipment(Shipment shipment) {
        Order order = shipment.getOrder();
        Map<String, Object> body = new HashMap<>();

        body.put("payment_type_id", 2);
        body.put("note", isNotBlank(shipment.getNote()) ? shipment.getNote() : ("Don hang " + (order.getId() != null ? order.getId() : "")));
        body.put("required_note", isNotBlank(shipment.getRequiredNote()) ? shipment.getRequiredNote() : "CHOXEMHANGKHONGTHU");

        body.put("from_name", "Kenny");
        body.put("from_phone", "0989937030");
        body.put("from_address", "123 Cau Giay, Ha Noi");
        body.put("from_ward_code", "1A0101");
        body.put("from_district_id", 1482);

        buildRecipientAddress(body, order.getAddress(), order.getId());

        if (shipment.getCodAmount() != null) {
            body.put("cod_amount", (int) Math.round(shipment.getCodAmount()));
        } else if (order.getPaymentType() == PaymentType.COD) {
            body.put("cod_amount", (int) Math.round(order.getTotal()));
        } else {
            body.put("cod_amount", 0);
        }

        body.put("weight", shipment.getWeight() != null && shipment.getWeight() > 0 ? shipment.getWeight() : 500);
        body.put("length", shipment.getLength() != null && shipment.getLength() > 0 ? shipment.getLength() : 20);
        body.put("width", shipment.getWidth() != null && shipment.getWidth() > 0 ? shipment.getWidth() : 15);
        body.put("height", shipment.getHeight() != null && shipment.getHeight() > 0 ? shipment.getHeight() : 10);
        body.put("service_type_id", 2);

        List<Map<String, Object>> items = new ArrayList<>();
        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            for (ShipmentItem sItem : shipment.getItems()) {
                OrderItem oItem = sItem.getOrderItem();
                String itemName = oItem != null ? resolveItemName(oItem) : "San pham";
                int qty = sItem.getQuantity() != null ? sItem.getQuantity() : 1;
                int price = (oItem != null && oItem.getPriceAtOrderTime() != null)
                        ? oItem.getPriceAtOrderTime().intValue() : 0;

                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("name", itemName);
                itemMap.put("quantity", qty);
                itemMap.put("price", price);
                items.add(itemMap);
            }
        } else {
            buildItemList(body, order);
        }

        if (!items.isEmpty()) {
            body.put("items", items);
        }

        return body;
    }

    /**
     * Xay dung request body de tao van don GHN tu doi tuong Order.
     * @param order Don hang can tao van don
     * @return Map chua cac tham so gui len GHN API
     */
    public Map<String, Object> build(Order order) {
        Map<String, Object> body = new HashMap<>();

        body.put("payment_type_id", 2);
        body.put("note", "Don hang " + (order.getId() != null ? order.getId() : ""));
        body.put("required_note", "CHOXEMHANGKHONGTHU");

        body.put("from_name", "Kenny");
        body.put("from_phone", "0989937030");
        body.put("from_address", "123 Cau Giay, Ha Noi");
        body.put("from_ward_code", "1A0101");
        body.put("from_district_id", 1482);

        buildRecipientAddress(body, order.getAddress(), order.getId());

        if (order.getPaymentType() == PaymentType.COD) {
            body.put("cod_amount", (int) Math.round(order.getTotal()));
        } else {
            body.put("cod_amount", 0);
        }

        body.put("weight", 500);
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("service_type_id", 2);

        buildItemList(body, order);

        return body;
    }

    private void buildRecipientAddress(Map<String, Object> body, Address address, String orderId) {
        if (address != null) {
            body.put("to_name", isNotBlank(address.getName()) ? address.getName() : "Khach hang");
            body.put("to_phone", isNotBlank(address.getPhoneNumber()) ? address.getPhoneNumber() : "0989937030");
            body.put("to_address", isNotBlank(address.getAddress()) ? address.getAddress() : "Viet Nam");
            if (isNotBlank(address.getWard()))     body.put("to_ward_name", address.getWard().trim());
            if (isNotBlank(address.getDistrict())) body.put("to_district_name", address.getDistrict().trim());
            if (isNotBlank(address.getProvince())) body.put("to_province_name", address.getProvince().trim());
            body.put("is_new_to_address", true);
        } else {
            log.warn("Don hang {} khong co dia chi cu the, su dung thong tin mac dinh", orderId);
            body.put("to_name", "Khach hang");
            body.put("to_phone", "0989937030");
            body.put("to_address", "Ha Noi, Viet Nam");
            body.put("is_new_to_address", true);
        }
    }

    private void buildItemList(Map<String, Object> body, Order order) {
        List<Map<String, Object>> items = new ArrayList<>();

        if (order.getItems() != null && !order.getItems().isEmpty()) {
            int totalWeight = 0;
            for (OrderItem item : order.getItems()) {
                String itemName = resolveItemName(item);
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;

                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("name", itemName);
                itemMap.put("quantity", qty);
                itemMap.put("price", item.getPriceAtOrderTime() != null ? item.getPriceAtOrderTime().intValue() : 0);
                itemMap.put("weight", 200);
                totalWeight += 200 * qty;
                items.add(itemMap);
            }
            if (totalWeight > 0) body.put("weight", totalWeight);
        } else {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("name", "Don hang " + (order.getId() != null ? order.getId() : ""));
            itemMap.put("quantity", 1);
            itemMap.put("price", (int) Math.round(order.getTotal()));
            itemMap.put("weight", 500);
            items.add(itemMap);
        }
        body.put("items", items);
    }

    private String resolveItemName(OrderItem item) {
        if (item.getProductTitle() != null && !item.getProductTitle().isBlank()) {
            return item.getProductTitle();
        }
        if (item.getSubProduct() != null
                && item.getSubProduct().getProduct() != null
                && item.getSubProduct().getProduct().getTitle() != null) {
            return item.getSubProduct().getProduct().getTitle();
        }
        return "San pham";
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}