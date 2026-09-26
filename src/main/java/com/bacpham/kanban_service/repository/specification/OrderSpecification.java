package com.bacpham.kanban_service.repository.specification;

import com.bacpham.kanban_service.entity.*;
import com.bacpham.kanban_service.enums.OrderStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderSpecification {

    public static Specification<Order> filter(
            String status,
            String search,
            String startDate,
            String endDate
    ) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.isNull(root.get("deleted")),
                    cb.isFalse(root.get("deleted"))
            ));

            if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
                try {
                    OrderStatus orderStatusEnum = OrderStatus.valueOf(status.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("orderStatus"), orderStatusEnum));
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (search != null && !search.trim().isEmpty()) {
                String cleanKw = search.trim().toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + cleanKw + "%";

                Join<Order, User> userJoin = root.join("user", JoinType.LEFT);
                Join<Order, Address> addressJoin = root.join("address", JoinType.LEFT);
                Join<Order, OrderItem> itemJoin = root.join("items", JoinType.LEFT);
                Join<OrderItem, SubProduct> subProductJoin = itemJoin.join("subProduct", JoinType.LEFT);
                Join<SubProduct, Product> productJoin = subProductJoin.join("product", JoinType.LEFT);

                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("id")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(root.get("trackingCode")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(userJoin.get("email")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(userJoin.get("firstname")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(userJoin.get("lastname")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(addressJoin.get("name")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(addressJoin.get("phoneNumber")), pattern, '\\'));
                searchPredicates.add(cb.like(cb.lower(productJoin.get("title")), pattern, '\\'));

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            if (startDate != null && !startDate.trim().isEmpty()) {
                try {
                    LocalDate start = LocalDate.parse(startDate.trim());
                    Date startDateTime = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
                } catch (Exception ignored) {
                }
            }

            if (endDate != null && !endDate.trim().isEmpty()) {
                try {
                    LocalDate end = LocalDate.parse(endDate.trim());
                    Date endDateTime = Date.from(end.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
                } catch (Exception ignored) {
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
