package health.zaed.identity.model.enums;

/**
 * User roles in the Zaed system.
 *
 * <p>Role hierarchy:
 * <ul>
 *   <li>DONOR, REQUESTER - Temporary roles for OTP-verified users (no account)</li>
 *   <li>PARTNER_* - Full account roles for pickup/delivery partners</li>
 *   <li>ADMIN - Full platform access with 2FA required</li>
 * </ul>
 *
 * @see health.zaed.identity.model.enums.Permission for associated permissions
 */
public enum UserRole {

    // Temporary roles (OTP-verified, no persistent account)
    DONOR,
    REQUESTER,

    // Partner roles (full account with email/password)
    PARTNER_PHARMACY,
    PARTNER_NGO,
    PARTNER_VOLUNTEER,

    // Admin role (full account with 2FA required)
    ADMIN;

    /**
     * Checks if this role requires a full account (email/password).
     */
    public boolean requiresAccount() {
        return this != DONOR && this != REQUESTER;
    }

    /**
     * Checks if this role requires 2FA.
     */
    public boolean requires2FA() {
        return this == ADMIN;
    }

    /**
     * Checks if this is a partner role.
     */
    public boolean isPartner() {
        return this == PARTNER_PHARMACY || this == PARTNER_NGO || this == PARTNER_VOLUNTEER;
    }
}
