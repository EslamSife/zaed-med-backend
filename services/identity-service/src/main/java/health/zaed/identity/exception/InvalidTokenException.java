package health.zaed.identity.exception;

/**
 * Exception thrown when a JWT token is invalid, expired, or revoked.
 */
public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super("INVALID_TOKEN", message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super("INVALID_TOKEN", message, cause);
    }
}
