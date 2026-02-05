package health.zaed.identity.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request for partners and admins.
 *
 * @param email the user's email address
 * @param password the user's password
 * @param deviceId optional device identifier for multi-device management
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password,

    String deviceId
) {}
