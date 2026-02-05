package health.zaed.identity.model.enums;

/**
 * Types of authentication events for audit logging.
 */
public enum AuthEventType {

    // Login events
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    LOGOUT_ALL,

    // OTP events
    OTP_SENT,
    OTP_VERIFIED,
    OTP_FAILED,
    OTP_EXPIRED,
    OTP_RATE_LIMITED,

    // 2FA events
    TWO_FA_ENABLED,
    TWO_FA_DISABLED,
    TWO_FA_VERIFIED,
    TWO_FA_FAILED,
    BACKUP_CODE_USED,

    // These are used by AuthService
    TWO_FACTOR_CHALLENGE,
    TWO_FACTOR_SUCCESS,
    TWO_FACTOR_FAILED,

    // Password events
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    // Token events
    TOKEN_REFRESHED,
    TOKEN_REVOKED,
    TOKEN_EXPIRED,

    // Account events
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    ACCOUNT_CREATED,
    ACCOUNT_DISABLED
}
