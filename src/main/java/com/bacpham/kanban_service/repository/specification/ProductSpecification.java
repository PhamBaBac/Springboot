package com.bacpham.kanban_service.repository.specification;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.enums.OrderStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ProductSpecification {

    public static Specification<Product> filter(
            List<String> categoryIds,
            String search,
            Double minPrice,
            Double maxPrice
    ) {
        return filter(categoryIds, search, minPrice, maxPrice, "relevance");
    }

    public static Specification<Product> filter(
            List<String> categoryIds,
            String search,
            Double minPrice,
            Double maxPrice,
            String sortBy) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.isNull(root.get("deleted")),
                    cb.isFalse(root.get("deleted"))));

            if (categoryIds != null && !categoryIds.isEmpty()) {
                Join<Product, Category> categoryJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.or(
                        categoryJoin.get("id").in(categoryIds),
                        categoryJoin.get("slug").in(categoryIds)));
            }

            if (search != null && !search.trim().isEmpty()) {
                Set<String> expandedKeywords = com.bacpham.kanban_service.utils.SearchSynonymUtils
                        .expandKeywords(search);
                List<Predicate> searchPredicates = new ArrayList<>();

                for (String kw : expandedKeywords) {
                    String cleanKw = kw.trim().toLowerCase()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_");

                    searchPredicates.add(cb.like(cb.lower(root.get("title")), "%" + cleanKw + "%", '\\'));
                    searchPredicates.add(cb.like(cb.lower(root.get("slug")), "%" + cleanKw + "%", '\\'));
                    searchPredicates.add(cb.like(cb.lower(root.get("description")), "%" + cleanKw + "%", '\\'));
                }

                if (!searchPredicates.isEmpty()) {
                    predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
                }
            }

            boolean hasMinPrice = minPrice != null;
            boolean hasMaxPrice = maxPrice != null;

            if (hasMinPrice || hasMaxPrice) {
                Join<Product, SubProduct> subProductJoin = root.join("subProducts", JoinType.LEFT);

                predicates.add(cb.or(
                        cb.isNull(subProductJoin.get("deleted")),
                        cb.isFalse(subProductJoin.get("deleted"))));

                if (hasMinPrice || hasMaxPrice) {
                    Expression<Double> effectivePrice = cb.selectCase()
                            .when(cb.and(
                                    cb.isNotNull(subProductJoin.get("discount")),
                                    cb.gt(subProductJoin.get("discount"), 0),
                                    cb.lt(subProductJoin.get("discount"), subProductJoin.get("price"))),
                                    subProductJoin.get("discount"))
                            .otherwise(subProductJoin.get("price"))
                            .as(Double.class);

                    if (hasMinPrice) {
                        predicates.add(cb.greaterThanOrEqualTo(effectivePrice, minPrice));
                    }
                    if (hasMaxPrice) {
                        predicates.add(cb.lessThanOrEqualTo(effectivePrice, maxPrice));
                    }
                }
            }

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                applySorting(root, query, cb, sortBy, search);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void applySorting(
            Root<Product> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String sortBy,
            String search) {
        String normalizedSort = (sortBy != null) ? sortBy.trim().toLowerCase() : "relevance";

        switch (normalizedSort) {
            case "latest":
                query.orderBy(cb.desc(root.get("createdAt")));
                break;

            case "price_asc": {
                Subquery<Double> priceSubquery = query.subquery(Double.class);
                Root<SubProduct> subRoot = priceSubquery.from(SubProduct.class);

                Expression<Double> effectivePrice = cb.selectCase()
                        .when(cb.and(
                                cb.isNotNull(subRoot.get("discount")),
                                cb.gt(subRoot.get("discount"), 0),
                                cb.lt(subRoot.get("discount"), subRoot.get("price"))), subRoot.get("discount"))
                        .otherwise(subRoot.get("price"))
                        .as(Double.class);

                priceSubquery.select(cb.min(effectivePrice))
                        .where(
                                cb.equal(subRoot.get("product"), root),
                                cb.isNotNull(subRoot.get("price")),
                                cb.gt(subRoot.get("price"), 0),
                                cb.or(cb.isNull(subRoot.get("deleted")), cb.isFalse(subRoot.get("deleted"))));

                query.orderBy(cb.asc(cb.coalesce(priceSubquery, 999999999.0)), cb.desc(root.get("createdAt")));
                break;
            }

            case "price_desc": {
                Subquery<Double> priceSubquery = query.subquery(Double.class);
                Root<SubProduct> subRoot = priceSubquery.from(SubProduct.class);

                Expression<Double> effectivePrice = cb.selectCase()
                        .when(cb.and(
                                cb.isNotNull(subRoot.get("discount")),
                                cb.gt(subRoot.get("discount"), 0),
                                cb.lt(subRoot.get("discount"), subRoot.get("price"))), subRoot.get("discount"))
                        .otherwise(subRoot.get("price"))
                        .as(Double.class);

                priceSubquery.select(cb.max(effectivePrice))
                        .where(
                                cb.equal(subRoot.get("product"), root),
                                cb.isNotNull(subRoot.get("price")),
                                cb.gt(subRoot.get("price"), 0),
                                cb.or(cb.isNull(subRoot.get("deleted")), cb.isFalse(subRoot.get("deleted"))));

                query.orderBy(cb.desc(cb.coalesce(priceSubquery, 0.0)), cb.desc(root.get("createdAt")));
                break;
            }

            case "topsales": {
                Subquery<Long> salesSubquery = query.subquery(Long.class);
                Root<OrderItem> orderItemRoot = salesSubquery.from(OrderItem.class);
                Join<OrderItem, SubProduct> subProductJoin = orderItemRoot.join("subProduct", JoinType.INNER);
                Join<OrderItem, Order> orderJoin = orderItemRoot.join("order", JoinType.INNER);

                Expression<Long> quantityExpr = cb.selectCase()
                        .when(cb.isNotNull(orderItemRoot.get("quantity")), orderItemRoot.get("quantity").as(Long.class))
                        .otherwise(0L)
                        .as(Long.class);

                salesSubquery.select(cb.coalesce(cb.sum(quantityExpr), 0L))
                        .where(
                                cb.equal(subProductJoin.get("product"), root),
                                cb.equal(orderJoin.get("orderStatus"), OrderStatus.COMPLETED),
                                cb.or(cb.isNull(orderJoin.get("deleted")), cb.isFalse(orderJoin.get("deleted"))));

                query.orderBy(cb.desc(salesSubquery), cb.desc(root.get("createdAt")));
                break;
            }

            case "relevance":
            default:
                if (search != null && !search.trim().isEmpty()) {
                    String cleanKw = search.trim().toLowerCase();
                    Expression<Integer> relevanceCase = cb.selectCase()
                            .when(cb.like(cb.lower(root.get("title")), cleanKw + "%"), 1)
                            .when(cb.like(cb.lower(root.get("title")), "%" + cleanKw + "%"), 2)
                            .otherwise(3)
                            .as(Integer.class);
                    query.orderBy(cb.asc(relevanceCase), cb.desc(root.get("createdAt")));
                } else {
                    query.orderBy(cb.desc(root.get("createdAt")));
                }
                break;
        }
    }
}
