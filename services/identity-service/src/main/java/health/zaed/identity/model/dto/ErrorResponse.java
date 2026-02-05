package health.zaed.identity.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standardized error response for auth endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    String message,
    Integer attemptsRemaining,
    Integer retryAfter,
    Instant lockedUntil,
    Instant timestamp
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null, null, null, Instant.now());
    }

    public static ErrorResponse withRetry(String error, String message, int retryAfterSeconds) {
        return new ErrorResponse(error, message, null, retryAfterSeconds, null, Instant.now());
    }

    public static ErrorResponse withAttempts(String error, String message, int attemptsRemaining) {
        return new ErrorResponse(error, message, attemptsRemaining, null, null, Instant.now());
    }

    public static ErrorResponse locked(String error, String message, Instant lockedUntil) {
        return new ErrorResponse(error, message, null, null, lockedUntil, Instant.now());
    }
}
