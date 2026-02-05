package health.zaed.identity.service;

import health.zaed.identity.config.OtpConfig;
import health.zaed.identity.exception.OtpException;
import health.zaed.identity.exception.RateLimitException;
import health.zaed.identity.model.enums.OtpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OtpService}.
 *
 * <p>Tests OTP generation, storage, verification, and rate limiting.
 * Uses mocks for Redis and SMS gateway.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OtpService")
class OtpServiceTest {

    private static final String TEST_PHONE = "+201234567890";
    private static final UUID TEST_REFERENCE_ID = UUID.randomUUID();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SmsGateway smsGateway;

    private OtpConfig otpConfig;
    private PasswordEncoder passwordEncoder;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpConfig = new OtpConfig();
        otpConfig.setLength(6);
        otpConfig.setExpirySeconds(300);
        otpConfig.setMaxAttempts(3);
        otpConfig.setRateLimitPerHour(3);

        passwordEncoder = new BCryptPasswordEncoder();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService = new OtpService(redisTemplate, passwordEncoder, otpConfig, smsGateway);
    }

    @Nested
    @DisplayName("sendOtp")
    class SendOtp {

        @Test
        @DisplayName("should generate and send OTP successfully")
        void shouldGenerateAndSendOtpSuccessfully() {
            // No rate limit
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(smsGateway.sendOtp(eq(TEST_PHONE), anyString(), eq("SMS"))).thenReturn(true);

            int expirySeconds = otpService.sendOtp(TEST_PHONE, "SMS", OtpContext.DONATION, TEST_REFERENCE_ID);

            assertThat(expirySeconds).isEqualTo(300);

            // Verify OTP was sent via SMS gateway
            ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
            verify(smsGateway).sendOtp(eq(TEST_PHONE), otpCaptor.capture(), eq("SMS"));

            String sentOtp = otpCaptor.getValue();
            assertThat(sentOtp).matches("\\d{6}"); // 6-digit OTP

            // Verify OTP was stored in Redis
            verify(valueOperations).set(
                argThat(key -> key != null && key.contains(TEST_PHONE)),
                anyString(),
                eq(Duration.ofSeconds(300))
            );
        }

        @Test
        @DisplayName("should throw RateLimitException when rate limit exceeded")
        void shouldThrowRateLimitExceptionWhenRateLimitExceeded() {
            String rateLimitKey = "otp_rate:" + TEST_PHONE;
            // Simulate rate limit reached
            when(valueOperations.get(rateLimitKey)).thenReturn("3"); // max is 3
            when(redisTemplate.getExpire(rateLimitKey)).thenReturn(1800L); // 30 min remaining

            assertThatThrownBy(() ->
                otpService.sendOtp(TEST_PHONE, "SMS", OtpContext.DONATION, TEST_REFERENCE_ID)
            )
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Maximum OTP requests exceeded");
        }

        @Test
        @DisplayName("should throw OtpException when SMS sending fails")
        void shouldThrowOtpExceptionWhenSmsSendingFails() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(smsGateway.sendOtp(anyString(), anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() ->
                otpService.sendOtp(TEST_PHONE, "SMS", OtpContext.DONATION, TEST_REFERENCE_ID)
            )
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Failed to send OTP");
        }

        @Test
        @DisplayName("should clear previous attempts when sending new OTP")
        void shouldClearPreviousAttemptsWhenSendingNewOtp() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(smsGateway.sendOtp(anyString(), anyString(), anyString())).thenReturn(true);

            otpService.sendOtp(TEST_PHONE, "SMS", OtpContext.DONATION, TEST_REFERENCE_ID);

            // Verify attempts counter was deleted
            verify(redisTemplate).delete(argThat((String key) -> key != null && key.contains("otp_attempts:")));
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtp {

        private String otpKey;
        private String attemptsKey;

        @BeforeEach
        void setUpKeys() {
            // Build the exact keys that OtpService will use
            otpKey = "otp:" + TEST_PHONE + ":" + OtpContext.DONATION.name() + ":" + TEST_REFERENCE_ID;
            attemptsKey = "otp_attempts:" + otpKey;
        }

        @Test
        @DisplayName("should verify valid OTP successfully")
        void shouldVerifyValidOtpSuccessfully() {
            String otp = "123456";
            String hashedOtp = passwordEncoder.encode(otp);

            // No previous attempts - return null for attempts key
            when(valueOperations.get(attemptsKey)).thenReturn(null);
            // Return OTP hash for OTP key
            when(valueOperations.get(otpKey)).thenReturn(hashedOtp);

            boolean result = otpService.verifyOtp(TEST_PHONE, otp, OtpContext.DONATION, TEST_REFERENCE_ID);

            assertThat(result).isTrue();

            // Verify OTP and attempts keys were deleted (one-time use)
            verify(redisTemplate).delete(otpKey);
            verify(redisTemplate).delete(attemptsKey);
        }

        @Test
        @DisplayName("should throw OtpException for expired OTP")
        void shouldThrowOtpExceptionForExpiredOtp() {
            when(valueOperations.get(attemptsKey)).thenReturn(null);
            when(valueOperations.get(otpKey)).thenReturn(null); // OTP expired/not found

            assertThatThrownBy(() ->
                otpService.verifyOtp(TEST_PHONE, "123456", OtpContext.DONATION, TEST_REFERENCE_ID)
            )
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("should throw OtpException for invalid OTP and increment attempts")
        void shouldThrowOtpExceptionForInvalidOtpAndIncrementAttempts() {
            String correctOtp = "123456";
            String wrongOtp = "654321";
            String hashedOtp = passwordEncoder.encode(correctOtp);

            when(valueOperations.get(attemptsKey)).thenReturn("0");
            when(valueOperations.get(otpKey)).thenReturn(hashedOtp);
            when(valueOperations.increment(attemptsKey)).thenReturn(1L);

            assertThatThrownBy(() ->
                otpService.verifyOtp(TEST_PHONE, wrongOtp, OtpContext.DONATION, TEST_REFERENCE_ID)
            )
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Invalid OTP");

            // Verify attempts was incremented
            verify(valueOperations).increment(attemptsKey);
        }

        @Test
        @DisplayName("should throw OtpException when max attempts exceeded")
        void shouldThrowOtpExceptionWhenMaxAttemptsExceeded() {
            // Already at max attempts
            when(valueOperations.get(attemptsKey)).thenReturn("3"); // max is 3

            assertThatThrownBy(() ->
                otpService.verifyOtp(TEST_PHONE, "123456", OtpContext.DONATION, TEST_REFERENCE_ID)
            )
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Maximum verification attempts");
        }
    }

    @Nested
    @DisplayName("getRetryAfter")
    class GetRetryAfter {

        @Test
        @DisplayName("should return TTL when rate limited")
        void shouldReturnTtlWhenRateLimited() {
            when(redisTemplate.getExpire(contains("otp_rate:"))).thenReturn(1800L);

            int retryAfter = otpService.getRetryAfter(TEST_PHONE);

            assertThat(retryAfter).isEqualTo(1800);
        }

        @Test
        @DisplayName("should return 0 when not rate limited")
        void shouldReturnZeroWhenNotRateLimited() {
            when(redisTemplate.getExpire(anyString())).thenReturn(-2L); // Key doesn't exist

            int retryAfter = otpService.getRetryAfter(TEST_PHONE);

            assertThat(retryAfter).isEqualTo(0);
        }
    }
}
