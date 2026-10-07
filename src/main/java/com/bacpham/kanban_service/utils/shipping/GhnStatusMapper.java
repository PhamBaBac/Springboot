package com.bacpham.kanban_service.utils.shipping;

import org.springframework.stereotype.Component;

/**
 * Utility class chuyen doi ma trang thai GHN sang ten hien thi tieng Viet.
 *
 * Tach ra khoi GhnShippingService de tuan thu:
 * - SRP: Service chi lo HTTP call, mapper chi lo anh xa trang thai.
 * - OCP: Them trang thai moi chi can sua class nay, khong anh huong den service.
 * - Testability: Co the test doc lap, co the mock trong unit test.
 */
@Component
public class GhnStatusMapper {

    /**
     * Anh xa ma trang thai GHN sang ten tieng Viet tuong ung.
     * @param status Ma trang thai tu GHN (vd: "delivered", "cancel", ...)
     * @return Ten tieng Viet mo ta trang thai
     */
    public String toDisplayName(String status) {
        if (status == null) return "Không xác định";
        return switch (status.toLowerCase()) {
            case "ready_to_pick"          -> "Mới tạo đơn - Chờ lấy hàng";
            case "picking"                -> "Shipper đang đi lấy hàng";
            case "cancel"                 -> "Đơn hàng đã hủy";
            case "money_collect_picking"  -> "Đang thu tiền người gửi";
            case "picked"                 -> "Đã lấy hàng thành công";
            case "storing"                -> "Hàng đã nhập kho GHN";
            case "transporting"           -> "Đang luân chuyển hàng giữa các kho";
            case "sorting"                -> "Đang phân loại hàng hóa";
            case "delivering"             -> "Shipper đang trên đường giao hàng";
            case "money_collect_delivering" -> "Shipper đang giao hàng & thu tiền";
            case "delivered"              -> "Giao hàng thành công";
            case "delivery_fail"          -> "Giao hàng không thành công";
            case "waiting_to_return"      -> "Chờ xác nhận chuyển hoàn";
            case "return"                 -> "Đang chuyển hoàn về người gửi";
            case "return_transporting"    -> "Đang luân chuyển hàng hoàn";
            case "return_sorting"         -> "Đang phân loại hàng hoàn";
            case "returning"              -> "Shipper đang trả lại hàng cho shop";
            case "return_fail"            -> "Trả hàng không thành công";
            case "returned"               -> "Đã hoàn trả hàng về shop";
            case "exception"              -> "Đơn hàng gặp sự cố ngoại lệ";
            case "damage"                 -> "Hàng hóa bị hư hỏng";
            case "lost"                   -> "Hàng hóa bị thất lạc";
            default                       -> status;
        };
    }
}
