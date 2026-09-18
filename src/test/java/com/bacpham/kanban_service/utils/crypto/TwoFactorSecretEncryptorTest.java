package com.bacpham.kanban_service.utils.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorSecretEncryptorTest {

    private TwoFactorSecretEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new TwoFactorSecretEncryptor("TestSecretKeyFor2FAEncryption2026!#");
    }

    @Test
    @DisplayName("Should encrypt and decrypt 2FA secret successfully")
    void testEncryptAndDecrypt() {
        String originalSecret = "JBSWY3DPEHPK3PXP";

        String encrypted = encryptor.convertToDatabaseColumn(originalSecret);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("enc:"));
        assertNotEquals(originalSecret, encrypted);

        String decrypted = encryptor.convertToEntityAttribute(encrypted);
        assertEquals(originalSecret, decrypted);
    }

    @Test
    @DisplayName("Should handle null or empty values gracefully")
    void testNullOrEmpty() {
        assertNull(encryptor.convertToDatabaseColumn(null));
        assertNull(encryptor.convertToDatabaseColumn(""));
        assertNull(encryptor.convertToEntityAttribute(null));
        assertNull(encryptor.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("Backward compatibility: should return plain secret if not prefixed with enc:")
    void testBackwardCompatibility() {
        String legacyPlainSecret = "LEGACY_PLAINTEXT_SECRET_12345";
        String result = encryptor.convertToEntityAttribute(legacyPlainSecret);
        assertEquals(legacyPlainSecret, result);
    }
}
