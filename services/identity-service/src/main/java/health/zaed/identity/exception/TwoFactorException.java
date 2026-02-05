package health.zaed.identity.exception;

/**
 * Exception for 2FA-related errors.
 */
public class TwoFactorException extends AuthException {

    public TwoFactorException(String message) {
        super("2FA_ERROR", message);
    }

    public TwoFactorException(String errorCode, String message) {
        super(errorCode, message);
    }
}
