package com.bacpham.kanban_service.helper.exception;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public enum ErrorCode {
    UNCATEGORIZED(9999, "Uncategorized", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND(1001, "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(1002, "User already exists", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(1003, "Invalid date of birth", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_USERNAME(1005, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1006, "Invalid key", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(1007, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(1008, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1009, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_DOB(1010, "Invalid date of birth {min}", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(1011, "Product not found", HttpStatus.NOT_FOUND),
    SUB_PRODUCT_NOT_FOUND(1012, "Sub product not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(1013, "Category not found", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_FOUND(1014, "Supplier not found", HttpStatus.NOT_FOUND),
    TFA_NOT_ENABLED(1015, "TFA is not enabled" , HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(1016, "Wrong password", HttpStatus.BAD_REQUEST),
    PASSWORDS_NOT_MATCH(1017, "Passwords do not match", HttpStatus.BAD_REQUEST),

    PROMOTION_NOT_FOUND(1018, "Promotion not found" , HttpStatus.NOT_FOUND),
    PRODUCT_SLUG_NOT_MATCH(1019, "Product slug does not match", HttpStatus.BAD_REQUEST),
    CART_NOT_FOUND(1020, "Cart not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK(1021,"In sufficient stock" ,HttpStatus.BAD_REQUEST ),
    PROMOTION_ALREADY_USED(1022, "Promotion already used", HttpStatus.BAD_REQUEST),
    PROMOTION_OUT_OF_STOCK(1023, "Promotion out of stock", HttpStatus.BAD_REQUEST),
    PROMOTION_EXPIRED(1024, "Promotion expired", HttpStatus.BAD_REQUEST),
    UNKNOWN(1025, "unknown", HttpStatus.INTERNAL_SERVER_ERROR ),
    BILL_NOT_FOUND(1026,"Bill not found", HttpStatus.NOT_FOUND ),
    INVALID_INPUT(1027,"Invalid input", HttpStatus.BAD_REQUEST),
    MESSAGE_TOO_LONG(1028, "Message too long", HttpStatus.BAD_REQUEST),
    CHAT_HISTORY_NOT_FOUND(1029, "Chat history not found", HttpStatus.NOT_FOUND),
    INVALID_VERIFICATION_CODE(1030, "Invalid verification code", HttpStatus.BAD_REQUEST),
    ADDRESS_NOT_FOUND(1031, "Address not found", HttpStatus.NOT_FOUND),
    CANNOT_CANCEL_ORDER(1032, "Cannot cancel order", HttpStatus.BAD_REQUEST),
    NO_COMPLETED_ORDER_FOR_REVIEW(1033, "No completed order for review", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS_FOR_ORDER(1034, "Review already exists for this order", HttpStatus.BAD_REQUEST),;


    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
