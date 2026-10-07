package com.bacpham.kanban_service.repository.specification;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SupplierSpecification {

    public static Specification<Supplier> filter(String status, String search) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            // 1. Loại bỏ các nhà cung cấp đã bị xóa mềm (deleted = true)
            predicates.add(cb.or(
                    cb.isNull(root.get("deleted")),
                    cb.isFalse(root.get("deleted"))
            ));

            // 2. Lọc theo trạng thái
            // - "all" / "tat_ca" / "tất cả": Tất cả
            // - "active" / "hoat_dong" / "hoạt động": Hoạt động (active = 1)
            // - "taking" / "dang_lay_hang" / "đang lấy hàng": Đang lấy hàng (isTaking = 1)
            // - "stopped" / "ngung_lay" / "ngừng lấy": Ngừng lấy (isTaking != 1 hoặc isTaking IS NULL)
            // - "inactive" / "khoa" / "khóa": Khóa (active != 1 hoặc active IS NULL)
            if (status != null && !status.trim().isEmpty()) {
                String normalizedStatus = status.trim().toLowerCase();
                switch (normalizedStatus) {
                    case "active":
                    case "hoat_dong":
                    case "hoạt động":
                        predicates.add(cb.equal(root.get("active"), 1));
                        break;

                    case "taking":
                    case "dang_lay_hang":
                    case "đang lấy hàng":
                    case "dang_lay":
                    case "đang lấy":
                        predicates.add(cb.equal(root.get("isTaking"), 1));
                        break;

                    case "stopped":
                    case "ngung_lay":
                    case "ngừng lấy":
                    case "ngung_lay_hang":
                    case "ngừng lấy hàng":
                        predicates.add(cb.or(
                                cb.isNull(root.get("isTaking")),
                                cb.notEqual(root.get("isTaking"), 1)
                        ));
                        break;

                    case "inactive":
                    case "locked":
                    case "khoa":
                    case "khóa":
                        predicates.add(cb.or(
                                cb.isNull(root.get("active")),
                                cb.notEqual(root.get("active"), 1)
                        ));
                        break;

                    case "all":
                    case "tat_ca":
                    case "tất cả":
                    default:
                        // "Tất cả" hoặc không khớp trạng thái đặc biệt: không áp thêm điều kiện trạng thái
                        break;
                }
            }

            // 3. Tìm kiếm theo từ khóa (tên, SĐT, email, danh mục, sản phẩm)
            if (search != null && !search.trim().isEmpty()) {
                String cleanKw = search.trim().toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + cleanKw + "%";

                Join<Supplier, Category> categoryJoin = root.join("categories", JoinType.LEFT);
                Join<Supplier, Product> productJoin = root.join("products", JoinType.LEFT);

                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(root.get("contact")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(root.get("email")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(categoryJoin.get("title")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(productJoin.get("title")), pattern, '\\'));

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
