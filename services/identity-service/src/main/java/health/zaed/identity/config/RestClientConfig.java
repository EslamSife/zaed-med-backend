package health.zaed.identity.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration for REST client used by SMS gateways.
 *
 * <p>Includes observability support for distributed tracing and metrics.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder, ObservationRegistry observationRegistry) {
        return builder
            .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
            .observationRegistry(observationRegistry)
            .observationConvention(new DefaultClientRequestObservationConvention())
            .build();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
