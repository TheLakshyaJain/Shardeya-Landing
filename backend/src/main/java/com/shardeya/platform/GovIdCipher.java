package com.shardeya.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Application-level AES-256-GCM encryption for {@code plot_sale.buyer_gov_id_number_enc}
 * (01-DATA-MODEL.md §4: "Encrypted at rest (pgcrypto/KMS)"). Chose app-layer
 * AES over pgcrypto so the key never has to pass through a SQL statement
 * (avoiding it ever landing in a query log) and so encrypt/decrypt is
 * unit-testable without a database at all.
 *
 * <p>Key handling mirrors the JWT secret pattern already used elsewhere
 * (dev-only default, override via {@code GOV_ID_ENCRYPTION_KEY}): the
 * configured string is hashed with SHA-256 to always yield exactly 32 bytes
 * regardless of the input string's length, rather than requiring callers to
 * supply an already-32-byte literal or a separate base64/hex encoding step.
 *
 * <p>Stored format is {@code nonce(12 bytes) || ciphertext || tag(16 bytes)}
 * in one BYTEA column — GCM's authentication tag is appended by the JDK
 * implementation automatically, so there's nothing extra to manage.
 */
@Component
public class GovIdCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public GovIdCipher(@Value("${shardeya.security.gov-id-encryption-key}") String configuredSecret) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(hashed, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive gov-id encryption key", e);
        }
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Gov ID encryption failed", e);
        }
    }

    public String decrypt(byte[] stored) {
        try {
            byte[] nonce = Arrays.copyOfRange(stored, 0, NONCE_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(stored, NONCE_LENGTH_BYTES, stored.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Gov ID decryption failed", e);
        }
    }
}
