package com.bacpham.kanban_service.repository.specification;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> filter(
            List<String> categoryIds,
            String search,
            List<String> sizes,
            List<String> colors,
            Double minPrice,
            Double maxPrice
    ) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.isNull(root.get("deleted")),
                    cb.isFalse(root.get("deleted"))
            ));

            if (categoryIds != null && !categoryIds.isEmpty()) {
                Join<Product, Category> categoryJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.or(
                        categoryJoin.get("id").in(categoryIds),
                        categoryJoin.get("parentId").in(categoryIds)
                ));
            }

            if (search != null && !search.trim().isEmpty()) {
                predicates.add(cb.like(
                        cb.lower(root.get("title")),
                        "%" + search.trim().toLowerCase() + "%"
                ));
            }

            boolean hasSizes = sizes != null && !sizes.isEmpty();
            boolean hasColors = colors != null && !colors.isEmpty();
            boolean hasMinPrice = minPrice != null;
            boolean hasMaxPrice = maxPrice != null;

            if (hasSizes || hasColors || hasMinPrice || hasMaxPrice) {
                Join<Product, SubProduct> subProductJoin = root.join("subProducts", JoinType.LEFT);

                predicates.add(cb.or(
                        cb.isNull(subProductJoin.get("deleted")),
                        cb.isFalse(subProductJoin.get("deleted"))
                ));

                if (hasSizes) {
                    predicates.add(subProductJoin.get("size").in(sizes));
                }
                if (hasColors) {
                    predicates.add(subProductJoin.get("color").in(colors));
                }
                if (hasMinPrice) {
                    predicates.add(cb.greaterThanOrEqualTo(subProductJoin.get("price"), minPrice));
                }
                if (hasMaxPrice) {
                    predicates.add(cb.lessThanOrEqualTo(subProductJoin.get("price"), maxPrice));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
