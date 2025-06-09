package com.bacpham.kanban_service.tfa;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Service
@Slf4j
public class TwoFactorAuthenticationService {
    public String generateNewSecret() {
        return new DefaultSecretGenerator().generate();
    }

    public String generateQrCodeImageUri(String secret) {
        QrData data = new QrData.Builder()
                .label("BacPham coding 2FA example")
                .secret(secret)
                .issuer("BacPham-coding")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        QrGenerator qrGenerator = new ZxingPngQrGenerator();
        byte[] imageData = new byte[0];
        try {
            imageData = qrGenerator.generate(data);
        } catch (QrGenerationException e) {
            log.error("Error while generating QR code", e);
        }

        return getDataUriForImage(imageData, qrGenerator.getImageMimeType());
    }

    public boolean isOtpValid(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

        return codeVerifier.isValidCode(secret, code);
    }

    public boolean isOtpNotValid(String secret, String code) {
        return !isOtpValid(secret, code);
    }

}
