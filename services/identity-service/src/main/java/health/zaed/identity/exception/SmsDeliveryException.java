package health.zaed.identity.exception;

/**
 * Exception thrown when SMS delivery fails due to transient issues.
 *
 * <p>This exception is designed for use with {@code @Retryable} to trigger
 * automatic retry on transient failures such as:
 * <ul>
 *   <li>Network timeouts</li>
 *   <li>Gateway unavailability</li>
 *   <li>Rate limiting (429 responses)</li>
 *   <li>Temporary service errors (5xx responses)</li>
 * </ul>
 *
 * <p>Validation errors or permanent failures should NOT use this exception,
 * as they should not be retried.
 *
 * @see health.zaed.identity.config.ResilienceConfig
 */
public class SmsDeliveryException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    /**
     * Creates a retryable SMS delivery exception.
     *
     * @param message the error message
     */
    public SmsDeliveryException(String message) {
        super(message);
        this.errorCode = "SMS_DELIVERY_ERROR";
        this.retryable = true;
    }

    /**
     * Creates an SMS delivery exception with custom error code.
     *
     * @param errorCode the error code for categorization
     * @param message   the error message
     */
    public SmsDeliveryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = true;
    }

    /**
     * Creates an SMS delivery exception with cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SMS_DELIVERY_ERROR";
        this.retryable = true;
    }

    /**
     * Creates an SMS delivery exception with full details.
     *
     * @param errorCode the error code for categorization
     * @param message   the error message
     * @param cause     the underlying cause
     * @param retryable whether this error should trigger retry
     */
    public SmsDeliveryException(String errorCode, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /**
     * Returns the error code for categorization and monitoring.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Indicates whether this exception should trigger a retry attempt.
     *
     * @return true if the operation should be retried
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Factory method for network timeout errors.
     *
     * @param cause the underlying cause
     * @return a retryable SmsDeliveryException
     */
    public static SmsDeliveryException networkTimeout(Throwable cause) {
        return new SmsDeliveryException("SMS_NETWORK_TIMEOUT", "SMS gateway network timeout", cause, true);
    }

    /**
     * Factory method for gateway unavailable errors.
     *
     * @param gatewayName the name of the unavailable gateway
     * @return a retryable SmsDeliveryException
     */
    public static SmsDeliveryException gatewayUnavailable(String gatewayName) {
        return new SmsDeliveryException("SMS_GATEWAY_UNAVAILABLE",
                "SMS gateway unavailable: " + gatewayName);
    }

    /**
     * Factory method for rate limit errors.
     *
     * @return a retryable SmsDeliveryException
     */
    public static SmsDeliveryException rateLimited() {
        return new SmsDeliveryException("SMS_RATE_LIMITED",
                "SMS gateway rate limit exceeded");
    }

    /**
     * Factory method for server errors (5xx responses).
     *
     * @param statusCode the HTTP status code
     * @param cause      the underlying cause
     * @return a retryable SmsDeliveryException
     */
    public static SmsDeliveryException serverError(int statusCode, Throwable cause) {
        return new SmsDeliveryException("SMS_SERVER_ERROR",
                "SMS gateway server error: " + statusCode, cause, true);
    }
}
