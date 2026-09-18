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
        if (status == null) return "Khong xac dinh";
        return switch (status.toLowerCase()) {
            case "ready_to_pick"          -> "Moi tao don - Cho lay hang";
            case "picking"                -> "Shipper dang di lay hang";
            case "cancel"                 -> "Don hang da huy";
            case "money_collect_picking"  -> "Dang thu tien nguoi gui";
            case "picked"                 -> "Da lay hang thanh cong";
            case "storing"                -> "Hang da nhap kho GHN";
            case "transporting"           -> "Dang luan chuyen hang giua cac kho";
            case "sorting"                -> "Dang phan loai hang hoa";
            case "delivering"             -> "Shipper dang tren duong giao hang";
            case "money_collect_delivering" -> "Shipper dang thu tien khi giao";
            case "delivered"              -> "Giao hang thanh cong";
            case "delivery_fail"          -> "Giao hang khong thanh cong";
            case "waiting_to_return"      -> "Cho xac nhan chuyen hoan";
            case "return"                 -> "Dang chuyen hoan ve nguoi gui";
            case "return_transporting"    -> "Dang luan chuyen hang hoan";
            case "return_sorting"         -> "Dang phan loai hang hoan";
            case "returning"              -> "Shipper dang tra lai hang cho shop";
            case "return_fail"            -> "Tra hang khong thanh cong";
            case "returned"               -> "Da hoan tra hang ve shop";
            case "exception"              -> "Don hang gap su co ngoai le";
            case "damage"                 -> "Hang hoa bi hu hong";
            case "lost"                   -> "Hang hoa bi that lac";
            default                       -> status;
        };
    }
}
