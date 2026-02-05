package health.zaed.identity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for Spring Framework 7 native API versioning.
 *
 * <p>Uses path-segment versioning strategy where the version is extracted
 * from the URL path at position 1 (e.g., /api/v1/auth/login).
 *
 * <p>Benefits:
 * <ul>
 *   <li>Centralized version configuration</li>
 *   <li>Easy to add v2, v3 endpoints with version = "2", version = "3" attributes</li>
 *   <li>Can switch to header or query-param versioning by changing this config</li>
 *   <li>Uses Spring Framework 7's built-in versioning support</li>
 * </ul>
 */
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .usePathSegment(1)
            .addSupportedVersions("1");
    }
}
