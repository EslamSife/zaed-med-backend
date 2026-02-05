package health.zaed.identity.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request to send an OTP to a phone number.
 *
 * @param phone Egyptian phone number in E.164 format (+20...)
 * @param channel delivery channel: SMS or WHATSAPP
 * @param context the OTP context (DONATION or REQUEST)
 * @param referenceId the ID of the donation or request
 */
public record OtpSendRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+20[0-9]{10}$", message = "Phone must be Egyptian number in E.164 format (+20...)")
    String phone,

    @Pattern(regexp = "^(SMS|WHATSAPP)$", message = "Channel must be SMS or WHATSAPP")
    String channel,

    @NotBlank(message = "Context is required")
    @Pattern(regexp = "^(DONATION|REQUEST)$", message = "Context must be DONATION or REQUEST")
    String context,

    @NotNull(message = "Reference ID is required")
    UUID referenceId
) {
    public OtpSendRequest {
        if (channel == null || channel.isBlank()) {
            channel = "SMS";
        }
    }
}
