package health.zaed.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OTP configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "zaed.identity.otp")
public class OtpConfig {

    /**
     * OTP code length (default: 6 digits).
     */
    private int length = 6;

    /**
     * OTP expiry in seconds (default: 5 minutes).
     */
    private int expirySeconds = 300;

    /**
     * Maximum verification attempts per OTP.
     */
    private int maxAttempts = 3;

    /**
     * Maximum OTPs that can be sent to a phone per hour.
     */
    private int rateLimitPerHour = 3;

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getExpirySeconds() {
        return expirySeconds;
    }

    public void setExpirySeconds(int expirySeconds) {
        this.expirySeconds = expirySeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRateLimitPerHour() {
        return rateLimitPerHour;
    }

    public void setRateLimitPerHour(int rateLimitPerHour) {
        this.rateLimitPerHour = rateLimitPerHour;
    }
}
