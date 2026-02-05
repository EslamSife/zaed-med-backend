package health.zaed.identity.exception;

/**
 * Exception for OTP-related errors.
 */
public class OtpException extends AuthException {

    private final int remainingAttempts;

    public OtpException(String message) {
        super("OTP_ERROR", message);
        this.remainingAttempts = -1;
    }

    public OtpException(String errorCode, String message, int remainingAttempts) {
        super(errorCode, message);
        this.remainingAttempts = remainingAttempts;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}
