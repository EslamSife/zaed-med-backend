package health.zaed.identity.service;

import health.zaed.identity.config.SmsMisrConfig;
import health.zaed.identity.exception.SmsDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link SmsMisrGateway}.
 *
 * <p>Tests HTTP client behavior using MockRestServiceServer.
 */
@DisplayName("SmsMisrGateway")
class SmsMisrGatewayTest {

    private static final String API_URL = "https://smsmisr.com/api/v2/";
    private static final String TEST_PHONE = "+201234567890";
    private static final String TEST_OTP = "123456";

    private MockRestServiceServer mockServer;
    private SmsMisrGateway gateway;
    private SmsMisrConfig config;

    @BeforeEach
    void setUp() {
        config = new SmsMisrConfig();
        config.setApiUrl(API_URL);
        config.setUsername("testuser");
        config.setPassword("testpass");
        config.setSenderId("ZAED");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new SmsMisrGateway(config, restClient);
    }

    @Nested
    @DisplayName("sendOtp")
    class SendOtp {

        @Test
        @DisplayName("should send OTP successfully when API returns success")
        void shouldSendOtpSuccessfully() {
            mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

            boolean result = gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS");

            assertThat(result).isTrue();
            mockServer.verify();
        }

        @Test
        @DisplayName("should convert E.164 phone to local format")
        void shouldConvertPhoneToLocalFormat() {
            mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mobile=01234567890")))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

            gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS");

            mockServer.verify();
        }

        @Test
        @DisplayName("should return false when API returns failure status")
        void shouldReturnFalseOnApiFailure() {
            mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"failed\",\"error\":\"Invalid number\"}", MediaType.APPLICATION_JSON));

            boolean result = gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS");

            assertThat(result).isFalse();
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw SmsDeliveryException on network timeout for retry")
        void shouldThrowOnNetworkTimeout() {
            mockServer.expect(requestTo(API_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("Connection timed out")));

            assertThatThrownBy(() -> gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS"))
                .isInstanceOf(SmsDeliveryException.class);
        }

        @Test
        @DisplayName("should throw SmsDeliveryException on rate limit (429) for retry")
        void shouldThrowOnRateLimit() {
            mockServer.expect(requestTo(API_URL))
                .andRespond(withTooManyRequests());

            assertThatThrownBy(() -> gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS"))
                .isInstanceOf(SmsDeliveryException.class);
        }

        @Test
        @DisplayName("should throw SmsDeliveryException on server error (5xx) for retry")
        void shouldThrowOnServerError() {
            mockServer.expect(requestTo(API_URL))
                .andRespond(withServerError());

            assertThatThrownBy(() -> gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS"))
                .isInstanceOf(SmsDeliveryException.class);
        }

        @Test
        @DisplayName("should return false on client error (4xx except 429)")
        void shouldReturnFalseOnClientError() {
            mockServer.expect(requestTo(API_URL))
                .andRespond(withBadRequest());

            boolean result = gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("should send notification successfully")
        void shouldSendNotificationSuccessfully() {
            mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

            boolean result = gateway.sendNotification(TEST_PHONE, "Test notification");

            assertThat(result).isTrue();
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("when not configured")
    class WhenNotConfigured {

        @BeforeEach
        void setUp() {
            config.setUsername("");
            config.setPassword("");
        }

        @Test
        @DisplayName("should return true without making API call (dev mode)")
        void shouldReturnTrueInDevMode() {
            boolean result = gateway.sendOtp(TEST_PHONE, TEST_OTP, "SMS");

            assertThat(result).isTrue();
            // mockServer.verify() would fail if any request was made
        }
    }

    @Nested
    @DisplayName("gateway properties")
    class GatewayProperties {

        @Test
        @DisplayName("should not support WhatsApp")
        void shouldNotSupportWhatsApp() {
            assertThat(gateway.supportsWhatsApp()).isFalse();
        }

        @Test
        @DisplayName("should return cost per SMS")
        void shouldReturnCostPerSms() {
            assertThat(gateway.getCostPerSms()).contains("EGP");
        }
    }
}
