package com.bacpham.kanban_service.helper.exception;

import lombok.AccessLevel;
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
    UNCATEGORIZED(1999, "Lỗi hệ thống chưa được phân loại. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Khóa hoặc trường dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_INPUT(1002, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    UNKNOWN(1003, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_CONNECTION_ERROR(1004, "Không thể kết nối đến cơ sở dữ liệu. Vui lòng kiểm tra lại kết nối mạng hoặc thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    CONNECTION_TIMEOUT(1005, "Kết nối đến máy chủ hoặc dịch vụ quá thời gian chờ (Timeout). Vui lòng thử lại sau.", HttpStatus.GATEWAY_TIMEOUT),
    NETWORK_CONNECTION_ERROR(1006, "Lỗi kết nối mạng hoặc máy chủ không phản hồi. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    EXTERNAL_SERVICE_ERROR(1007, "Lỗi kết nối đến dịch vụ bên thứ ba. Vui lòng thử lại sau.", HttpStatus.BAD_GATEWAY),
    FILE_TOO_LARGE(1008, "Kích thước tập tin tải lên vượt quá giới hạn cho phép", HttpStatus.PAYLOAD_TOO_LARGE),
    RESOURCE_NOT_FOUND(1009, "Không tìm thấy tài nguyên hoặc đường dẫn yêu cầu", HttpStatus.NOT_FOUND),
    UNSUPPORTED_MEDIA_TYPE(1010, "Định dạng dữ liệu gửi lên (Content-Type) không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // ==================================================
    // 2xxx - User / Authentication
    // ==================================================
    USER_NOT_FOUND(2001, "Không tìm thấy thông tin tài khoản", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(2002, "Tài khoản hoặc email này đã tồn tại trên hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(2003, "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt", HttpStatus.BAD_REQUEST),
    INVALID_USERNAME(2004, "Tên người dùng phải có ít nhất {min} ký tự", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(2005, "Ngày sinh không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_DOB(2006, "Độ tuổi không hợp lệ, yêu cầu tối thiểu {min} tuổi", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(2007, "Tài khoản hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(2008, "Phiên đăng nhập đã hết hạn hoặc chưa xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2009, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    TFA_NOT_ENABLED(2010, "Xác thực hai yếu tố (2FA) chưa được kích hoạt", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(2011, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    PASSWORDS_NOT_MATCH(2012, "Mật khẩu mới và xác nhận mật khẩu không khớp", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE(2013, "Mã xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(2014, "Định dạng email không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_SIZE_FIRST_NAME(2015, "Tên không được vượt quá {max} ký tự", HttpStatus.BAD_REQUEST),
    INVALID_FIRST_NAME_PATTERN(2016, "Tên chỉ được chứa các chữ cái và khoảng trắng", HttpStatus.BAD_REQUEST),
    INVALID_SIZE_LAST_NAME(2017, "Họ không được vượt quá {max} ký tự", HttpStatus.BAD_REQUEST),
    OTP_MAX_ATTEMPTS_EXCEEDED(2018, "Bạn đã nhập sai mã xác thực quá số lần cho phép. Vui lòng yêu cầu mã mới.", HttpStatus.TOO_MANY_REQUESTS),
    RESEND_CODE_COOLDOWN(2019, "Vui lòng đợi 60 giây trước khi yêu cầu gửi lại mã xác thực", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_EXCHANGE_CODE(2020, "Mã trao đổi xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    OAUTH2_ACCOUNT_CANNOT_RESET_PASSWORD(2021, "Tài khoản này được đăng nhập bằng mạng xã hội (Google/GitHub). Vui lòng sử dụng tính năng Đăng nhập mạng xã hội thay vì đặt lại mật khẩu.", HttpStatus.BAD_REQUEST),
    OAUTH2_ACCOUNT_CANNOT_CHANGE_PASSWORD(2022, "Tài khoản đăng nhập mạng xã hội không có mật khẩu nội bộ. Vui lòng quản lý bảo mật trên tài khoản mạng xã hội của bạn.", HttpStatus.BAD_REQUEST),
    // ==================================================
    // 3xxx - Product / Category / Supplier
    // ==================================================
    PRODUCT_NOT_FOUND(3001, "Không tìm thấy sản phẩm", HttpStatus.NOT_FOUND),
    SUB_PRODUCT_NOT_FOUND(3002, "Không tìm thấy biến thể sản phẩm", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(3003, "Không tìm thấy danh mục", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_FOUND(3004, "Không tìm thấy nhà cung cấp", HttpStatus.NOT_FOUND),
    PRODUCT_SLUG_NOT_MATCH(3005, "Đường dẫn định danh sản phẩm (slug) không khớp", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(3006, "Không tìm thấy file hình ảnh", HttpStatus.NOT_FOUND),

    // ==================================================
    // 4xxx - Promotions
    // ==================================================
    PROMOTION_NOT_FOUND(4001, "Mã khuyến mãi không tồn tại", HttpStatus.NOT_FOUND),
    PROMOTION_ALREADY_USED(4002, "Mã khuyến mãi đã được sử dụng", HttpStatus.BAD_REQUEST),
    PROMOTION_OUT_OF_STOCK(4003, "Mã khuyến mãi đã hết lượt sử dụng", HttpStatus.BAD_REQUEST),
    PROMOTION_EXPIRED(4004, "Mã khuyến mãi đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_TYPE(4005, "Loại khuyến mãi không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_VALUE(4006, "Giá trị khuyến mãi không hợp lệ", HttpStatus.BAD_REQUEST),

    // ==================================================
    // 5xxx - Cart / Order / Billing
    // ==================================================
    CART_NOT_FOUND(5001, "Không tìm thấy giỏ hàng", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK(5002, "Số lượng sản phẩm trong kho không đủ", HttpStatus.BAD_REQUEST),
    BILL_NOT_FOUND(5003, "Không tìm thấy hóa đơn", HttpStatus.NOT_FOUND),
    CANNOT_CANCEL_ORDER(5004, "Không thể hủy đơn hàng ở trạng thái hiện tại", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_STATUS_TRANSITION(5005, "Chuyển trạng thái đơn hàng không hợp lệ", HttpStatus.BAD_REQUEST),
    ORDER_PROCESSING_IN_PROGRESS(5006, "Đơn hàng đang được xử lý, vui lòng không gửi lại liên tục", HttpStatus.CONFLICT),
    ORDER_ALREADY_PROCESSED(5007, "Đơn hàng đã được tạo thành công trước đó", HttpStatus.OK),
    CANNOT_CANCEL_SHIPPED_ORDER(5008, "Đơn hàng đã được đóng gói hoặc đang vận chuyển, không thể tự hủy", HttpStatus.BAD_REQUEST),

    // ==================================================
    // 6xxx - Reviews / Chat / Messages
    // ==================================================
    MESSAGE_TOO_LONG(6001, "Tin nhắn quá dài, vui lòng rút gọn lại", HttpStatus.BAD_REQUEST),
    CHAT_HISTORY_NOT_FOUND(6002, "Không tìm thấy lịch sử cuộc trò chuyện", HttpStatus.NOT_FOUND),
    NO_COMPLETED_ORDER_FOR_REVIEW(6003, "Bạn chỉ có thể đánh giá sau khi đã hoàn thành đơn hàng", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS_FOR_ORDER(6004, "Bạn đã gửi đánh giá cho đơn hàng này rồi", HttpStatus.BAD_REQUEST),
    REVIEW_REJECTED_BY_MODERATION(6005, "Nội dung đánh giá bị từ chối do vi phạm tiêu chuẩn cộng đồng", HttpStatus.BAD_REQUEST),
    // ==================================================
    // 7xxx - Address / Delivery / Shipment
    // ==================================================
    ADDRESS_NOT_FOUND(7001, "Không tìm thấy thông tin địa chỉ", HttpStatus.NOT_FOUND),
    SHIPMENT_NOT_FOUND(7002, "Không tìm thấy thông tin kiện hàng", HttpStatus.NOT_FOUND),
    ORDER_ITEM_NOT_IN_ORDER(7003, "Sản phẩm không thuộc về đơn hàng này", HttpStatus.BAD_REQUEST),
    INVALID_SHIPMENT_QUANTITY(7004, "Số lượng đóng gói vượt quá số lượng trong đơn hàng", HttpStatus.BAD_REQUEST);

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

