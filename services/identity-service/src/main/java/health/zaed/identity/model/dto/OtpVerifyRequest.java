package health.zaed.identity.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request to verify an OTP.
 *
 * @param phone the phone number that received the OTP
 * @param otp the 6-digit OTP code
 * @param context the context (DONATION or REQUEST)
 * @param referenceId the ID of the donation or request being verified
 * @param trackingCode the tracking code for the donation/request
 */
public record OtpVerifyRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+20[0-9]{10}$", message = "Phone must be Egyptian number in E.164 format")
    String phone,

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    String otp,

    @NotBlank(message = "Context is required")
    @Pattern(regexp = "^(DONATION|REQUEST)$", message = "Context must be DONATION or REQUEST")
    String context,

    @NotNull(message = "Reference ID is required")
    UUID referenceId,

    String trackingCode
) {}
