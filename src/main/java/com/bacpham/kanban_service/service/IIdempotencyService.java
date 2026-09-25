package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.IdempotencyLockResult;

import java.time.Duration;

/**
 * Service quản lý cơ chế Idempotency Key cho API nhạy cảm (Checkout, tạo đơn hàng, thanh toán)
 * Ngăn chặn tuyệt đối tình trạng submit lặp lại, click đúp hoặc network retry gây duplicate đơn hàng.
 */
public interface IIdempotencyService {

    /**
     * Thử lấy khóa idempotency.
     *
     * @param idempotencyKey Khóa do client gửi hoặc được tính toán tự động
     * @param action Tên hành động nghiệp vụ (vd: "order_create")
     * @param inProgressTtl Thời gian timeout tạm thời trong khi đang xử lý request
     * @return IdempotencyLockResult trạng thái khóa (acquired hoặc alreadyCompleted)
     */
    IdempotencyLockResult tryAcquire(String idempotencyKey, String action, Duration inProgressTtl);

    /**
     * Đánh dấu thao tác đã xử lý thành công, lưu kết quả trả về trong thời gian TTL.
     *
     * @param idempotencyKey Khóa idempotency
     * @param action Tên hành động nghiệp vụ
     * @param resultData Dữ liệu kết quả nghiệp vụ (vd: orderId)
     * @param completedTtl Thời gian lưu trữ kết quả thành công (vd: 24 giờ)
     */
    void markCompleted(String idempotencyKey, String action, String resultData, Duration completedTtl);

    /**
     * Giải phóng khóa nếu xử lý thất bại do lỗi ngoại lệ để client có thể thử lại.
     *
     * @param idempotencyKey Khóa idempotency
     * @param action Tên hành động nghiệp vụ
     */
    void release(String idempotencyKey, String action);

    /**
     * Sinh fingerprint tự động dựa trên nội dung payload và định danh người dùng
     * làm cơ chế phòng thủ (defensive deduplication) khi client không truyền header Idempotency-Key.
     *
     * @param userId ID người dùng
     * @param paymentType Loại thanh toán
     * @param request Payload tạo đơn hàng
     * @return Chuỗi SHA-256 fingerprint
     */
    String generateFingerprint(String userId, String paymentType, OrderCreateRequest request);
}
