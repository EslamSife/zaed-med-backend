package health.zaed.identity.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitException extends AuthException {

    private final int retryAfterSeconds;

    public RateLimitException(String message, int retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
