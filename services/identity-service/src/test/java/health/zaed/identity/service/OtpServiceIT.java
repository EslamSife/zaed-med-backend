package health.zaed.identity.service;

import health.zaed.identity.IntegrationTestBase;
import health.zaed.identity.config.OtpConfig;
import health.zaed.identity.exception.OtpException;
import health.zaed.identity.exception.RateLimitException;
import health.zaed.identity.model.enums.OtpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link OtpService} with real Redis container.
 *
 * <p>These tests verify OTP lifecycle, rate limiting, and TTL behavior
 * against an actual Redis instance managed by Testcontainers.
 */
@DisplayName("OtpService Integration Tests")
class OtpServiceIT extends IntegrationTestBase {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpConfig otpConfig;

    @MockitoBean
    private SmsGateway smsGateway;

    private OtpService otpService;

    private static final String TEST_PHONE = "+201234567890";
    private static final String TEST_CHANNEL = "sms";

    @BeforeEach
    void setUp() {
        when(smsGateway.sendOtp(anyString(), anyString(), anyString())).thenReturn(true);

        otpService = new OtpService(
            redisTemplate,
            passwordEncoder,
            otpConfig,
            smsGateway
        );
    }

    @Nested
    @DisplayName("sendOtp and verifyOtp")
    class SendAndVerifyOtp {

        @Test
        @DisplayName("should send OTP and store in Redis with TTL")
        void shouldSendOtpAndStoreInRedisWithTTL() throws InterruptedException {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            int expirySeconds = otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId);

            String key = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String storedHash = redisTemplate.opsForValue().get(key);

            assertThat(storedHash).isNotNull();
            assertThat(expirySeconds).isEqualTo(otpConfig.getExpirySeconds());

            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertThat(ttl).isBetween(otpConfig.getExpirySeconds() - 5L, (long) otpConfig.getExpirySeconds());

            verify(smsGateway).sendOtp(eq(TEST_PHONE), anyString(), eq(TEST_CHANNEL));
        }

        @Test
        @DisplayName("should verify correct OTP")
        void shouldVerifyCorrectOtp() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String testOtp = "123456";
            String hashedOtp = passwordEncoder.encode(testOtp);
            redisTemplate.opsForValue().set(otpKey, hashedOtp, java.time.Duration.ofSeconds(300));

            boolean verified = otpService.verifyOtp(TEST_PHONE, testOtp, context, referenceId);

            assertThat(verified).isTrue();
            assertThat(redisTemplate.opsForValue().get(otpKey)).isNull();
        }

        @Test
        @DisplayName("should throw OtpException when OTP not found in Redis")
        void shouldThrowOtpExceptionWhenOtpNotFoundInRedis() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.REQUEST;

            assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, "123456", context, referenceId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("OTP has expired");
        }

        @Test
        @DisplayName("should throw OtpException when OTP is incorrect")
        void shouldThrowOtpExceptionWhenOtpIsIncorrect() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String correctOtp = "123456";
            String hashedOtp = passwordEncoder.encode(correctOtp);
            redisTemplate.opsForValue().set(otpKey, hashedOtp, java.time.Duration.ofSeconds(300));

            assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, "wrong", context, referenceId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Invalid OTP code");

            String attemptsKey = "otp_attempts:" + otpKey;
            String attempts = redisTemplate.opsForValue().get(attemptsKey);
            assertThat(attempts).isEqualTo("1");
        }

        @Test
        @DisplayName("should track failed attempts in Redis")
        void shouldTrackFailedAttemptsInRedis() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.REQUEST;

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String correctOtp = "123456";
            String hashedOtp = passwordEncoder.encode(correctOtp);
            redisTemplate.opsForValue().set(otpKey, hashedOtp, java.time.Duration.ofSeconds(300));

            // Exhaust all attempts (counter increments AFTER the check)
            for (int i = 0; i < otpConfig.getMaxAttempts(); i++) {
                assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, "wrong", context, referenceId))
                    .isInstanceOf(OtpException.class);
            }

            // Next attempt should be blocked with TOO_MANY_ATTEMPTS
            assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, "wrong", context, referenceId))
                .isInstanceOf(OtpException.class)
                .satisfies(e -> {
                    OtpException otpEx = (OtpException) e;
                    assertThat(otpEx.getErrorCode()).isEqualTo("TOO_MANY_ATTEMPTS");
                });
        }

        @Test
        @DisplayName("should reset attempts counter after new OTP is sent")
        void shouldResetAttemptsCounterAfterNewOtpIsSent() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String hashedOtp = passwordEncoder.encode("123456");
            redisTemplate.opsForValue().set(otpKey, hashedOtp, java.time.Duration.ofSeconds(300));

            assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, "wrong", context, referenceId))
                .isInstanceOf(OtpException.class);

            String attemptsKey = "otp_attempts:" + otpKey;
            assertThat(redisTemplate.opsForValue().get(attemptsKey)).isEqualTo("1");

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId);

            assertThat(redisTemplate.opsForValue().get(attemptsKey)).isNull();
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimiting {

        @Test
        @DisplayName("should enforce rate limit in Redis")
        void shouldEnforceRateLimitInRedis() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            for (int i = 0; i < otpConfig.getRateLimitPerHour(); i++) {
                otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, UUID.randomUUID());
            }

            assertThatThrownBy(() -> otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Maximum OTP requests exceeded");
        }

        @Test
        @DisplayName("should store rate limit counter with 1 hour TTL in Redis")
        void shouldStoreRateLimitCounterWith1HourTTLInRedis() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.REQUEST;

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId);

            String rateLimitKey = "otp_rate:" + TEST_PHONE;
            String count = redisTemplate.opsForValue().get(rateLimitKey);
            assertThat(count).isEqualTo("1");

            Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.SECONDS);
            assertThat(ttl).isBetween(3500L, 3600L);
        }

        @Test
        @DisplayName("should return correct retry-after from Redis TTL")
        void shouldReturnCorrectRetryAfterFromRedisTTL() {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            for (int i = 0; i < otpConfig.getRateLimitPerHour(); i++) {
                otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, UUID.randomUUID());
            }

            int retryAfter = otpService.getRetryAfter(TEST_PHONE);
            assertThat(retryAfter).isBetween(3500, 3600);
        }

        @Test
        @DisplayName("should increment rate limit counter in Redis")
        void shouldIncrementRateLimitCounterInRedis() {
            String rateLimitKey = "otp_rate:" + TEST_PHONE;

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, OtpContext.DONATION, UUID.randomUUID());
            assertThat(redisTemplate.opsForValue().get(rateLimitKey)).isEqualTo("1");

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, OtpContext.REQUEST, UUID.randomUUID());
            assertThat(redisTemplate.opsForValue().get(rateLimitKey)).isEqualTo("2");

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, OtpContext.DONATION, UUID.randomUUID());
            assertThat(redisTemplate.opsForValue().get(rateLimitKey)).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("OTP Expiration")
    class OtpExpiration {

        @Test
        @DisplayName("should automatically expire OTP after TTL in Redis")
        void shouldAutomaticallyExpireOtpAfterTTLInRedis() throws InterruptedException {
            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String testOtp = "123456";
            String hashedOtp = passwordEncoder.encode(testOtp);
            redisTemplate.opsForValue().set(otpKey, hashedOtp, java.time.Duration.ofSeconds(2));

            assertThat(redisTemplate.opsForValue().get(otpKey)).isNotNull();

            Thread.sleep(2500);

            assertThat(redisTemplate.opsForValue().get(otpKey)).isNull();
            assertThatThrownBy(() -> otpService.verifyOtp(TEST_PHONE, testOtp, context, referenceId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("OTP has expired");
        }
    }

    @Nested
    @DisplayName("Multiple OTPs")
    class MultipleOtps {

        @Test
        @DisplayName("should handle multiple OTPs for different contexts in Redis")
        void shouldHandleMultipleOtpsForDifferentContextsInRedis() {
            UUID referenceId = UUID.randomUUID();

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, OtpContext.DONATION, referenceId);
            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, OtpContext.REQUEST, referenceId);

            String donationKey = "otp:" + TEST_PHONE + ":DONATION:" + referenceId;
            String requestKey = "otp:" + TEST_PHONE + ":REQUEST:" + referenceId;

            assertThat(redisTemplate.opsForValue().get(donationKey)).isNotNull();
            assertThat(redisTemplate.opsForValue().get(requestKey)).isNotNull();
        }

        @Test
        @DisplayName("should handle multiple OTPs for different reference IDs in Redis")
        void shouldHandleMultipleOtpsForDifferentReferenceIDsInRedis() {
            UUID refId1 = UUID.randomUUID();
            UUID refId2 = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, refId1);
            otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, refId2);

            String key1 = "otp:" + TEST_PHONE + ":" + context.name() + ":" + refId1;
            String key2 = "otp:" + TEST_PHONE + ":" + context.name() + ":" + refId2;

            assertThat(redisTemplate.opsForValue().get(key1)).isNotNull();
            assertThat(redisTemplate.opsForValue().get(key2)).isNotNull();
        }
    }

    @Nested
    @DisplayName("SMS Gateway Failures")
    class SmsGatewayFailures {

        @Test
        @DisplayName("should throw OtpException when SMS gateway fails")
        void shouldThrowOtpExceptionWhenSmsGatewayFails() {
            when(smsGateway.sendOtp(anyString(), anyString(), anyString())).thenReturn(false);

            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            assertThatThrownBy(() -> otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Failed to send OTP");
        }

        @Test
        @DisplayName("should still store OTP in Redis even if SMS fails")
        void shouldStillStoreOtpInRedisEvenIfSmsFails() {
            when(smsGateway.sendOtp(anyString(), anyString(), anyString())).thenReturn(false);

            UUID referenceId = UUID.randomUUID();
            OtpContext context = OtpContext.DONATION;

            assertThatThrownBy(() -> otpService.sendOtp(TEST_PHONE, TEST_CHANNEL, context, referenceId))
                .isInstanceOf(OtpException.class);

            String otpKey = "otp:" + TEST_PHONE + ":" + context.name() + ":" + referenceId;
            String storedHash = redisTemplate.opsForValue().get(otpKey);
            assertThat(storedHash).isNotNull();
        }
    }
}
