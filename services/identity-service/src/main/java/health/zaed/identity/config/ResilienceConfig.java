package health.zaed.identity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Configuration for Spring Framework 7.0 resilience features.
 *
 * <p>Enables the following declarative resilience annotations:
 * <ul>
 *   <li>{@code @Retryable} - Automatic retry with exponential backoff</li>
 *   <li>{@code @ConcurrencyLimit} - Bulkhead pattern for throttling</li>
 * </ul>
 *
 * <p>These features are used primarily by SMS gateways to handle transient
 * failures and protect downstream services from overload.
 *
 * @see org.springframework.resilience.annotation.Retryable
 * @see org.springframework.resilience.annotation.ConcurrencyLimit
 * @see health.zaed.identity.service.SmsMisrGateway
 * @see health.zaed.identity.service.TwilioSmsGateway
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {

    /*
     * Resilience configuration values are defined directly on the annotations
     * for clarity and type safety. If externalized configuration is needed,
     * use the String variants of annotation attributes with ${property} syntax:
     *
     * @Retryable(
     *     maxAttemptsString = "${sms.retry.max-attempts:3}",
     *     delayString = "${sms.retry.delay:1000}",
     *     multiplierString = "${sms.retry.multiplier:2.0}"
     * )
     */
}
