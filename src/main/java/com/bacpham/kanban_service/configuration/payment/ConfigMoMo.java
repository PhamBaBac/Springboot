package com.bacpham.kanban_service.configuration.payment;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
@Getter
public class ConfigMoMo {

    @Value("${application.momo.pay-url:${MOMO_PAY_URL:https://test-payment.momo.vn/v2/gateway/api/create}}")
    private String payUrl;

    @Value("${application.momo.return-url:${MOMO_RETURN_URL:http://localhost:8080/api/v1/payment/momo-return}}")
    private String returnUrl;

    @Value("${application.momo.ipn-url:${MOMO_IPN_URL:http://localhost:8080/api/v1/payment/momo-ipn}}")
    private String ipnUrl;

    @Value("${application.momo.partner-code:${MOMO_PARTNER_CODE:MOMO}}")
    private String partnerCode;

    @Value("${application.momo.access-key:${MOMO_ACCESS_KEY:F8BBA842ECF85}}")
    private String accessKey;

    @Value("${application.momo.secret-key:${MOMO_SECRET_KEY:K951B6PE1waDMi640xX0huTrY0hs6AHQ}}")
    private String secretKey;

    /**
     * Tạo mã băm HMAC-SHA256 theo chuẩn bảo mật của MoMo API v2.
     */
    public static String hmacSHA256(String key, String data) {
        try {
            if (key == null || data == null) {
                return "";
            }
            Mac hmac256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac256.init(secretKeySpec);
            byte[] bytes = hmac256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
