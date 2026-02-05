package health.zaed.identity.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Twilio configuration for SMS and WhatsApp messaging.
 */
@Configuration
@ConfigurationProperties(prefix = "zaed.twilio")
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    private String accountSid;
    private String authToken;
    private String verifyServiceSid;
    private String fromNumber;
    private String whatsappFrom;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isBlank() &&
            authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialized successfully");
        } else {
            log.warn("Twilio credentials not configured - SMS will be disabled");
        }
    }

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank() &&
               authToken != null && !authToken.isBlank();
    }

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getVerifyServiceSid() {
        return verifyServiceSid;
    }

    public void setVerifyServiceSid(String verifyServiceSid) {
        this.verifyServiceSid = verifyServiceSid;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public String getWhatsappFrom() {
        return whatsappFrom;
    }

    public void setWhatsappFrom(String whatsappFrom) {
        this.whatsappFrom = whatsappFrom;
    }
}
