package health.zaed.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SMS Misr (Egyptian local SMS provider).
 *
 * <p>Used in Phase 2 for cost optimization (~10x cheaper than Twilio).
 */
@Configuration
@ConfigurationProperties(prefix = "smsmisr")
public class SmsMisrConfig {

    private String apiUrl = "https://smsmisr.com/api/v2/";
    private String username;
    private String password;
    private String senderId;

    public boolean isConfigured() {
        return username != null && !username.isBlank() &&
               password != null && !password.isBlank() &&
               senderId != null && !senderId.isBlank();
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}
