package health.zaed.identity.model.dto;

/**
 * Response containing access and refresh tokens.
 *
 * @param accessToken the new access token
 * @param refreshToken the new refresh token (rotated)
 * @param expiresIn seconds until access token expires
 * @param tokenType always "Bearer"
 */
public record TokenResponse(
    String accessToken,
    String refreshToken,
    int expiresIn,
    String tokenType
) {
    public static TokenResponse of(String accessToken, String refreshToken, int expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
