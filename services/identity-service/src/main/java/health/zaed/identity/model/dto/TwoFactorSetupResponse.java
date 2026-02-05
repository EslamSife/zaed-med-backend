package health.zaed.identity.model.dto;

import java.util.List;

/**
 * Response when setting up 2FA.
 *
 * @param secret Base32-encoded TOTP secret
 * @param qrCodeImage QR code as base64-encoded PNG data URI
 * @param backupCodes one-time use backup codes (store securely!)
 */
public record TwoFactorSetupResponse(
    String secret,
    String qrCodeImage,
    List<String> backupCodes
) {}
