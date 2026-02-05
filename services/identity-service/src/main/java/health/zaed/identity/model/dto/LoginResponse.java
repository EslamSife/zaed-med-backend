package health.zaed.identity.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response after successful login.
 *
 * <p>Two response modes:
 * <ul>
 *   <li>Success without 2FA: contains accessToken and refreshToken</li>
 *   <li>2FA required: contains requires2FA=true and tempToken</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
    // Success response (no 2FA or 2FA completed)
    String accessToken,
    String refreshToken,
    Integer expiresIn,
    String tokenType,
    UserInfo user,

    // 2FA challenge response
    Boolean requires2FA,
    String tempToken,
    List<String> methods
) {
    /**
     * Creates a successful login response with tokens.
     */
    public static LoginResponse success(String accessToken, String refreshToken, int expiresIn, UserInfo user) {
        return new LoginResponse(
            accessToken,
            refreshToken,
            expiresIn,
            "Bearer",
            user,
            null, null, null
        );
    }

    /**
     * Creates a 2FA challenge response.
     */
    public static LoginResponse requires2FA(String tempToken) {
        return new LoginResponse(
            null, null, null, null, null,
            true,
            tempToken,
            List.of("TOTP")
        );
    }

    /**
     * User information included in login response.
     */
    public record UserInfo(
        String id,
        String email,
        String name,
        String role,
        String partnerId
    ) {}
}
