package health.zaed.identity.model.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Request to verify 2FA during login.
 *
 * <p>Either code (TOTP) or recoveryCode must be provided.
 *
 * @param code the 6-digit TOTP code from authenticator app
 * @param recoveryCode a recovery code (if TOTP unavailable)
 * @param deviceId client device identifier for token binding
 */
public record TwoFactorVerifyRequest(
    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be 6 digits")
    String code,

    String recoveryCode,

    String deviceId
) {
    public TwoFactorVerifyRequest {
        // At least one must be provided
        if ((code == null || code.isBlank()) && (recoveryCode == null || recoveryCode.isBlank())) {
            throw new IllegalArgumentException("Either TOTP code or recovery code must be provided");
        }
    }
}
