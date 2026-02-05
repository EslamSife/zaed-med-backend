package health.zaed.identity.model.dto;

/**
 * Response after sending an OTP.
 *
 * @param message success message
 * @param expiresIn seconds until the OTP expires
 * @param retryAfter seconds until a new OTP can be requested
 * @param maskedPhone masked phone number for display
 */
public record OtpSendResponse(
    String message,
    int expiresIn,
    int retryAfter,
    String maskedPhone
) {
    public static OtpSendResponse success(int expirySeconds, int retryAfterSeconds, String maskedPhone) {
        return new OtpSendResponse("OTP sent successfully", expirySeconds, retryAfterSeconds, maskedPhone);
    }
}
