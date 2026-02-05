package health.zaed.identity.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for observability (metrics, tracing, logging).
 *
 * <p>Enables OpenTelemetry-based distributed tracing and metrics export.
 * Configure OTLP endpoint via application.yml.
 *
 * <p>Features:
 * <ul>
 *   <li>Distributed tracing with trace context propagation</li>
 *   <li>Metrics export to OTLP-compatible backends (Grafana, Jaeger, etc.)</li>
 *   <li>Integration with RestClient for HTTP client tracing</li>
 *   <li>Support for @Observed annotation on methods</li>
 * </ul>
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
