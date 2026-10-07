package com.bacpham.kanban_service.configuration.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Số lượng request tối đa trong khoảng thời gian duration.
     */
    int limit() default 10;

    /**
     * Thời gian cửa sổ (tính bằng giây). Mặc định 60 giây.
     */
    int duration() default 60;

    /**
     * Loại giới hạn: theo IP, theo User ID hoặc ưu tiên User ID nếu đã đăng nhập.
     */
    RateLimitType type() default RateLimitType.IP;

    /**
     * Tiền tố định danh cho endpoint (ví dụ: "login", "register", "send_otp").
     */
    String prefix() default "default";

    /**
     * Thông báo lỗi tùy chỉnh khi vượt ngưỡng (để trống sẽ dùng thông báo mặc định).
     */
    String message() default "";
}
