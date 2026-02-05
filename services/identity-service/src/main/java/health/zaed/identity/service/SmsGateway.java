package health.zaed.identity.service;

/**
 * SMS Gateway interface for provider-agnostic messaging.
 *
 * <p>Implementations:
 * <ul>
 *   <li>TwilioSmsGateway - Phase 1 (MVP)</li>
 *   <li>SmsMisrGateway - Phase 2 (Production scale)</li>
 * </ul>
 *
 * @see <a href="../../../architecture/adr/004-sms-gateway-strategy.md">ADR-004</a>
 */
public interface SmsGateway {

    boolean sendOtp(String phone, String otp, String channel);

    boolean sendNotification(String phone, String message);

    boolean supportsWhatsApp();

    String getCostPerSms();
}
