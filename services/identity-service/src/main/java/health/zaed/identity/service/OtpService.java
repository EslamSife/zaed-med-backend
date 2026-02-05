package health.zaed.identity.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import health.zaed.identity.config.OtpConfig;
import health.zaed.identity.exception.OtpException;
import health.zaed.identity.exception.RateLimitException;
import health.zaed.identity.model.enums.OtpContext;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * OTP generation, storage, and verification service.
 *
 * <p>OTPs are stored in Redis with TTL for automatic expiration.
 * Rate limiting is enforced per phone number.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OtpConfig otpConfig;
    private final SmsGateway smsGateway;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp_attempts:";
    private static final String OTP_RATE_LIMIT_PREFIX = "otp_rate:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate,
                      PasswordEncoder passwordEncoder,
                      OtpConfig otpConfig,
                      SmsGateway smsGateway) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.otpConfig = otpConfig;
        this.smsGateway = smsGateway;
    }

    public int sendOtp(
            @NonNull String phone,
            @NonNull String channel,
            @NonNull OtpContext context,
            @NonNull UUID referenceId) {
        checkRateLimit(phone);

        String otp = generateOtp();
        log.debug("Generated OTP for phone: {}***{}", phone.substring(0, 6), phone.substring(phone.length() - 2));

        String key = buildOtpKey(phone, context, referenceId);
        String hashedOtp = passwordEncoder.encode(otp);
        redisTemplate.opsForValue().set(key, hashedOtp, Duration.ofSeconds(otpConfig.getExpirySeconds()));

        String attemptsKey = OTP_ATTEMPTS_PREFIX + key;
        redisTemplate.delete(attemptsKey);

        incrementRateLimit(phone);

        boolean sent = smsGateway.sendOtp(phone, otp, channel);
        if (!sent) {
            log.error("Failed to send OTP to {}", maskPhone(phone));
            throw new OtpException("Failed to send OTP. Please try again.");
        }

        log.info("OTP sent successfully to {}", maskPhone(phone));
        return otpConfig.getExpirySeconds();
    }

    public boolean verifyOtp(
            @NonNull String phone,
            @NonNull String otp,
            @NonNull OtpContext context,
            @NonNull UUID referenceId) {
        String key = buildOtpKey(phone, context, referenceId);
        String attemptsKey = OTP_ATTEMPTS_PREFIX + key;

        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= otpConfig.getMaxAttempts()) {
            log.warn("Max OTP attempts exceeded for {}", maskPhone(phone));
            throw new OtpException("TOO_MANY_ATTEMPTS", "Maximum verification attempts exceeded. Request a new OTP.",
                otpConfig.getExpirySeconds());
        }

        String storedHash = redisTemplate.opsForValue().get(key);
        if (storedHash == null) {
            log.debug("OTP not found or expired for {}", maskPhone(phone));
            throw new OtpException("OTP_EXPIRED", "OTP has expired. Please request a new one.", 0);
        }

        if (!passwordEncoder.matches(otp, storedHash)) {
            redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, Duration.ofSeconds(otpConfig.getExpirySeconds()));

            int remaining = otpConfig.getMaxAttempts() - attempts - 1;
            log.debug("Invalid OTP for {}, {} attempts remaining", maskPhone(phone), remaining);
            throw new OtpException("INVALID_OTP", "Invalid OTP code", remaining);
        }

        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);

        log.info("OTP verified successfully for {}", maskPhone(phone));
        return true;
    }

    public int getRetryAfter(String phone) {
        String rateLimitKey = OTP_RATE_LIMIT_PREFIX + phone;
        Long ttl = redisTemplate.getExpire(rateLimitKey);
        return ttl != null && ttl > 0 ? ttl.intValue() : 0;
    }

    private void checkRateLimit(String phone) {
        String rateLimitKey = OTP_RATE_LIMIT_PREFIX + phone;
        String countStr = redisTemplate.opsForValue().get(rateLimitKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;

        if (count >= otpConfig.getRateLimitPerHour()) {
            Long ttl = redisTemplate.getExpire(rateLimitKey);
            int retryAfter = ttl != null && ttl > 0 ? ttl.intValue() : 3600;
            log.warn("Rate limit exceeded for {}", maskPhone(phone));
            throw new RateLimitException("Maximum OTP requests exceeded. Try again later.", retryAfter);
        }
    }

    private void incrementRateLimit(String phone) {
        String rateLimitKey = OTP_RATE_LIMIT_PREFIX + phone;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            // First request - set 1 hour expiry
            redisTemplate.expire(rateLimitKey, Duration.ofHours(1));
        }
    }

    private String generateOtp() {
        int bound = (int) Math.pow(10, otpConfig.getLength());
        int otp = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + otpConfig.getLength() + "d", otp);
    }

    private String buildOtpKey(String phone, OtpContext context, UUID referenceId) {
        return OTP_KEY_PREFIX + phone + ":" + context.name() + ":" + referenceId;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "****";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
    }
}
