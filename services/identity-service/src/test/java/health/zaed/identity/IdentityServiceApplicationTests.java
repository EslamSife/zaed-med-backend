package health.zaed.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;

/**
 * Smoke test verifying the application context loads correctly.
 *
 * <p>Uses Testcontainers for PostgreSQL and Redis to ensure
 * all beans can be initialized with real database connections.
 *
 * <p>Requires Docker to be running. Skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIf("isDockerAvailable")
class IdentityServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context initializes without errors
        // All beans are wired, database migrations run, etc.
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}
