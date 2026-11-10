package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.ReviewStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewProductResponse {
    String id;
    String createdBy;
    String userFirstname;
    String userLastname;
    String userEmail;
    String userAvatar;
    String orderId;
    String subProductId;
    String productId;
    String productName;
    String productSku;
    String productThumbnail;
    String color;
    String size;
    String comment;
    Integer star;
    List<String> images;
    ReviewStatus status;
    String shopReply;
    Date repliedAt;
    String repliedByName;
    Boolean isFeatured;
    String hiddenReason;
    Date createdAt;
    Date updatedAt;
}