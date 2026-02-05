package health.zaed.identity.model.dto;

/**
 * Response after successful OTP verification.
 *
 * @param verified whether the OTP was valid
 * @param tempToken temporary JWT token for limited actions
 * @param expiresIn seconds until the temp token expires
 * @param tokenType the token type (always "Bearer")
 */
public record OtpVerifyResponse(
    boolean verified,
    String tempToken,
    int expiresIn,
    String tokenType
) {
    public static OtpVerifyResponse success(String tempToken, int expiresIn) {
        return new OtpVerifyResponse(true, tempToken, expiresIn, "Bearer");
    }
}
