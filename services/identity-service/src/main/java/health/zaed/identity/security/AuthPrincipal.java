package health.zaed.identity.security;

import java.util.UUID;

/**
 * Principal object stored in SecurityContext after authentication.
 *
 * @param subject the subject claim (user ID or phone:number)
 * @param tokenType the type of token (access, temp, 2fa_pending)
 * @param role the user role (for access tokens)
 * @param partnerId the partner ID (for partner users)
 * @param context the OTP context (for temp tokens)
 * @param referenceId the reference ID (for temp tokens)
 * @param trackingCode the tracking code (for temp tokens)
 */
public record AuthPrincipal(
    String subject,
    String tokenType,
    String role,
    String partnerId,
    String context,
    String referenceId,
    String trackingCode
) {
    public UUID getUserId() {
        if (subject != null && !subject.startsWith("phone:")) {
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public String getPhone() {
        if (subject != null && subject.startsWith("phone:")) {
            return subject.substring(6);
        }
        return null;
    }

    public UUID getPartnerIdAsUUID() {
        if (partnerId != null) {
            try {
                return UUID.fromString(partnerId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public UUID getReferenceIdAsUUID() {
        if (referenceId != null) {
            try {
                return UUID.fromString(referenceId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public boolean isTempToken() {
        return "temp".equals(tokenType);
    }

    public boolean isAccessToken() {
        return "access".equals(tokenType);
    }
}
