package health.zaed.identity.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to refresh an access token.
 *
 * @param refreshToken the refresh token
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
