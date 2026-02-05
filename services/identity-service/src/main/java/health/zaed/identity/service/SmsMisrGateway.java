package health.zaed.identity.service;

import health.zaed.identity.config.SmsMisrConfig;
import health.zaed.identity.exception.SmsDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * SMS Misr implementation of SMS Gateway.
 *
 * <p>Used for Phase 2 (Production scale) in Egypt.
 * ~10x cheaper than Twilio for Egyptian numbers.
 *
 * <p>Resilience features:
 * <ul>
 *   <li>Automatic retry with exponential backoff on transient failures</li>
 *   <li>Concurrency limiting to protect the downstream SMS gateway</li>
 * </ul>
 *
 * @see <a href="../../../architecture/adr/004-sms-gateway-strategy.md">ADR-004</a>
 */
@Service
@Profile("smsmisr")
public class SmsMisrGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SmsMisrGateway.class);

    private final SmsMisrConfig config;
    private final RestClient restClient;

    private static final String OTP_TEMPLATE_AR = "زائد: رمز التحقق الخاص بك هو %s";

    public SmsMisrGateway(SmsMisrConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
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
            log.warn("SMS Misr not configured - OTP not sent");
            log.info("DEV MODE - OTP for {}: {}", phone, otp);
            return true;
        }

        String message = String.format(OTP_TEMPLATE_AR, otp);
        String localPhone = convertToLocalFormat(phone);

        return doSendSms(localPhone, message, phone);
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
            log.warn("SMS Misr not configured - notification not sent");
            return true;
        }

        String localPhone = convertToLocalFormat(phone);
        return doSendSms(localPhone, message, phone);
    }

    @Override
    public boolean supportsWhatsApp() {
        return false; // SMS Misr is SMS-only
    }

    @Override
    public String getCostPerSms() {
        return "~EGP 0.15";
    }

    private boolean doSendSms(String localPhone, String message, String originalPhone) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> response = restClient.post()
                    .uri(config.getApiUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildRequest(localPhone, message))
                    .retrieve()
                    .body(Map.class);

            if (response != null && "success".equalsIgnoreCase(response.get("status"))) {
                log.debug("SMS Misr sent message to {}", maskPhone(originalPhone));
                return true;
            }

            log.error("SMS Misr failed: {}", response);
            return false;

        } catch (ResourceAccessException e) {
            log.warn("SMS Misr network error (will retry): {}", e.getMessage());
            throw SmsDeliveryException.networkTimeout(e);

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            if (status == 429) {
                log.warn("SMS Misr rate limited (will retry)");
                throw SmsDeliveryException.rateLimited();
            }

            if (status >= 500) {
                log.warn("SMS Misr server error {} (will retry)", status);
                throw SmsDeliveryException.serverError(status, e);
            }

            log.error("SMS Misr client error {}: {}", status, e.getMessage());
            return false;

        } catch (SmsDeliveryException e) {
            throw e;

        } catch (Exception e) {
            log.error("SMS Misr unexpected error: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildRequest(String phone, String message) {
        return String.format(
                "username=%s&password=%s&sender=%s&mobile=%s&message=%s&language=2",
                config.getUsername(),
                config.getPassword(),
                config.getSenderId(),
                phone,
                encodeMessage(message)
        );
    }

    private String convertToLocalFormat(String phone) {
        if (phone.startsWith("+20")) {
            return "0" + phone.substring(3);
        }
        return phone;
    }

    private String encodeMessage(String message) {
        return java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
    }
}
