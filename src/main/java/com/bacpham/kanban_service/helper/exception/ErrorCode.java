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

    // ==================================================
    // 1xxx - Common / System Errors
    // ==================================================
    UNCATEGORIZED(1999, "Uncategorized", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid key", HttpStatus.BAD_REQUEST),
    INVALID_INPUT(1002, "Invalid input", HttpStatus.BAD_REQUEST),
    UNKNOWN(1003, "Unknown", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==================================================
    // 2xxx - User / Authentication
    // ==================================================
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(2002, "User already exists", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(2003, "Password must be at least 8 characters and include uppercase, lowercase, number, and special character", HttpStatus.BAD_REQUEST),
    INVALID_USERNAME(2004, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(2005, "Invalid date of birth", HttpStatus.BAD_REQUEST),
    INVALID_DOB(2006, "Invalid date of birth {min}", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(2007, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(2008, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2009, "You do not have permission", HttpStatus.FORBIDDEN),
    TFA_NOT_ENABLED(2010, "TFA is not enabled", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(2011, "Wrong password", HttpStatus.BAD_REQUEST),
    PASSWORDS_NOT_MATCH(2012, "Passwords do not match", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE(2013, "Invalid verification code", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(2014, "Invalid email format", HttpStatus.BAD_REQUEST),
    INVALID_SIZE_FIRST_NAME(2015, "First name must be at most {max} characters", HttpStatus.BAD_REQUEST),
    INVALID_FIRST_NAME_PATTERN(2016, "First name must contain only letters and spaces", HttpStatus.BAD_REQUEST),
    INVALID_SIZE_LAST_NAME(2017, "Last name must be at most {max} characters", HttpStatus.BAD_REQUEST),
    // ==================================================
    // 3xxx - Product / Category / Supplier
    // ==================================================
    PRODUCT_NOT_FOUND(3001, "Product not found", HttpStatus.NOT_FOUND),
    SUB_PRODUCT_NOT_FOUND(3002, "Sub product not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(3003, "Category not found", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_FOUND(3004, "Supplier not found", HttpStatus.NOT_FOUND),
    PRODUCT_SLUG_NOT_MATCH(3005, "Product slug does not match", HttpStatus.BAD_REQUEST),

    // ==================================================
    // 4xxx - Promotions
    // ==================================================
    PROMOTION_NOT_FOUND(4001, "Promotion not found", HttpStatus.NOT_FOUND),
    PROMOTION_ALREADY_USED(4002, "Promotion already used", HttpStatus.BAD_REQUEST),
    PROMOTION_OUT_OF_STOCK(4003, "Promotion out of stock", HttpStatus.BAD_REQUEST),
    PROMOTION_EXPIRED(4004, "Promotion expired", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_TYPE(4005, "Invalid promotion type", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_VALUE(4006, "Invalid promotion value", HttpStatus.BAD_REQUEST),

    // ==================================================
    // 5xxx - Cart / Order / Billing
    // ==================================================
    CART_NOT_FOUND(5001, "Cart not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK(5002, "Insufficient stock", HttpStatus.BAD_REQUEST),
    BILL_NOT_FOUND(5003, "Bill not found", HttpStatus.NOT_FOUND),
    CANNOT_CANCEL_ORDER(5004, "Cannot cancel order", HttpStatus.BAD_REQUEST),

    // ==================================================
    // 6xxx - Reviews / Chat / Messages
    // ==================================================
    MESSAGE_TOO_LONG(6001, "Message too long", HttpStatus.BAD_REQUEST),
    CHAT_HISTORY_NOT_FOUND(6002, "Chat history not found", HttpStatus.NOT_FOUND),
    NO_COMPLETED_ORDER_FOR_REVIEW(6003, "No completed order for review", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS_FOR_ORDER(6004, "Review already exists for this order", HttpStatus.BAD_REQUEST),
    REVIEW_REJECTED_BY_MODERATION(6005, "Review rejected by moderation", HttpStatus.BAD_REQUEST),
    // ==================================================
    // 7xxx - Address / Delivery
    // ==================================================
    ADDRESS_NOT_FOUND(7001, "Address not found", HttpStatus.NOT_FOUND),;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}

