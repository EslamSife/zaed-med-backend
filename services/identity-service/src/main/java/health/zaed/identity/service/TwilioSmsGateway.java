package health.zaed.identity.service;

import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.type.PhoneNumber;
import health.zaed.identity.config.TwilioConfig;
import health.zaed.identity.exception.SmsDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Twilio implementation of SMS Gateway.
 *
 * <p>Used for Phase 1 (MVP). See ADR-004 for migration plan to local provider.
 *
 * <p>Resilience features:
 * <ul>
 *   <li>Automatic retry with exponential backoff on transient failures</li>
 *   <li>Concurrency limiting to protect the downstream SMS gateway</li>
 * </ul>
 */
@Service
@Profile("!smsmisr") // Default profile
public class TwilioSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsGateway.class);

    /** Twilio error codes that indicate transient failures (should retry). */
    private static final int ERROR_SERVICE_UNAVAILABLE = 20503;
    private static final int ERROR_RATE_LIMIT = 20429;
    private static final int ERROR_INTERNAL = 20500;

    private final TwilioConfig config;

    private static final String OTP_TEMPLATE_AR = "زائد: رمز التحقق الخاص بك هو %s";
    private static final String OTP_TEMPLATE_EN = "Zaed: Your verification code is %s";

    public TwilioSmsGateway(TwilioConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resilience configuration:
     * <ul>
     *   <li>Retry: Up to 3 retries with exponential backoff (1s, 2s, 4s)</li>
     *   <li>Concurrency: Limited to 10 simultaneous calls</li>
     * </ul>
     * Only transient failures (network errors, rate limits, 5xx) trigger retry.
     */
    @Override
    @Retryable(
            includes = SmsDeliveryException.class,
            maxRetries = 3,
            delay = 1000,
            multiplier = 2.0,
            timeUnit = TimeUnit.MILLISECONDS
    )
    @ConcurrencyLimit(10)
    public boolean sendOtp(String phone, String otp, String channel) {
        if (!config.isConfigured()) {
            log.warn("Twilio not configured - OTP not sent");
            log.info("DEV MODE - OTP for {}: {}", phone, otp);
            return true;
        }

        try {
            if (config.getVerifyServiceSid() != null && !config.getVerifyServiceSid().isBlank()) {
                return sendViaVerify(phone, channel);
            } else {
                return sendViaSms(phone, otp, channel);
            }
        } catch (ApiConnectionException e) {
            log.warn("Twilio connection error (will retry): {}", e.getMessage());
            throw SmsDeliveryException.networkTimeout(e);

        } catch (ApiException e) {
            return handleApiException(e, phone, "OTP");

        } catch (SmsDeliveryException e) {
            throw e;

        } catch (Exception e) {
            log.error("Error sending OTP to {}: {}", maskPhone(phone), e.getMessage());
            return false;
        }
    }

    @Override
    @Retryable(
            includes = SmsDeliveryException.class,
            maxRetries = 3,
            delay = 1000,
            multiplier = 2.0,
            timeUnit = TimeUnit.MILLISECONDS
    )
    @ConcurrencyLimit(10)
    public boolean sendNotification(String phone, String message) {
        if (!config.isConfigured()) {
            log.warn("Twilio not configured - notification not sent");
            return true;
        }

        try {
            Message twilioMessage = Message.creator(
                            new PhoneNumber(phone),
                            new PhoneNumber(config.getFromNumber()),
                            message
                    )
                    .create();

            log.debug("Notification sent to {}, SID: {}", maskPhone(phone), twilioMessage.getSid());
            return twilioMessage.getStatus() != Message.Status.FAILED;

        } catch (ApiConnectionException e) {
            log.warn("Twilio connection error (will retry): {}", e.getMessage());
            throw SmsDeliveryException.networkTimeout(e);

        } catch (ApiException e) {
            return handleApiException(e, phone, "notification");

        } catch (SmsDeliveryException e) {
            throw e;

        } catch (Exception e) {
            log.error("Error sending notification to {}: {}", maskPhone(phone), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean supportsWhatsApp() {
        return config.getWhatsappFrom() != null && !config.getWhatsappFrom().isBlank();
    }

    @Override
    public String getCostPerSms() {
        return "$0.3959";
    }

    public boolean verifyWithTwilio(String phone, String code) {
        if (config.getVerifyServiceSid() == null || config.getVerifyServiceSid().isBlank()) {
            return false;
        }

        try {
            VerificationCheck check = VerificationCheck.creator(config.getVerifyServiceSid())
                    .setTo(phone)
                    .setCode(code)
                    .create();

            return "approved".equals(check.getStatus());
        } catch (ApiException e) {
            log.error("Twilio Verify check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendViaVerify(String phone, String channel) {
        String twilioChannel = "WHATSAPP".equalsIgnoreCase(channel) ? "whatsapp" : "sms";

        Verification verification = Verification.creator(
                        config.getVerifyServiceSid(),
                        phone,
                        twilioChannel
                )
                .create();

        log.debug("Twilio Verify sent to {}, status: {}", maskPhone(phone), verification.getStatus());
        return "pending".equals(verification.getStatus());
    }

    private boolean sendViaSms(String phone, String otp, String channel) {
        String message = String.format(OTP_TEMPLATE_AR, otp);
        PhoneNumber to = new PhoneNumber(phone);
        PhoneNumber from;

        if ("WHATSAPP".equalsIgnoreCase(channel) && config.getWhatsappFrom() != null) {
            from = new PhoneNumber("whatsapp:" + config.getWhatsappFrom());
            to = new PhoneNumber("whatsapp:" + phone);
        } else {
            from = new PhoneNumber(config.getFromNumber());
        }

        Message twilioMessage = Message.creator(to, from, message).create();

        log.debug("SMS sent to {}, SID: {}", maskPhone(phone), twilioMessage.getSid());
        return twilioMessage.getStatus() != Message.Status.FAILED;
    }

    private boolean handleApiException(ApiException e, String phone, String messageType) {
        int errorCode = e.getCode();

        if (isRetryableError(errorCode)) {
            log.warn("Twilio transient error {} (will retry): {}", errorCode, e.getMessage());
            throw SmsDeliveryException.serverError(errorCode, e);
        }

        log.error("Twilio API error sending {} to {}: {} - {}",
                messageType, maskPhone(phone), errorCode, e.getMessage());
        return false;
    }

    private boolean isRetryableError(int errorCode) {
        return errorCode == ERROR_SERVICE_UNAVAILABLE
                || errorCode == ERROR_RATE_LIMIT
                || errorCode == ERROR_INTERNAL
                || (errorCode >= 20500 && errorCode < 20600);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
    }
}
