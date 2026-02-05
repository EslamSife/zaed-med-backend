package health.zaed.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

/**
 * Base class for integration tests that require full Spring Boot context with Testcontainers.
 *
 * <p>Features:
 * <ul>
 *   <li>PostgreSQL container via {@link TestcontainersConfig}</li>
 *   <li>Redis container via {@link TestcontainersConfig}</li>
 *   <li>Database cleanup before each test</li>
 *   <li>Redis cleanup before each test</li>
 *   <li>Automatic Docker availability check</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class MyControllerIT extends IntegrationTestBase {
 *     @Test
 *     void testEndpoint() { ... }
 * }
 * }</pre>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
public abstract class IntegrationTestBase {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Cleanup database and Redis before each test to ensure test isolation.
     */
    @BeforeEach
    void cleanupBeforeTest() {
        cleanupDatabase();
        cleanupRedis();
    }

    /**
     * Checks if Docker is available for Testcontainers.
     */
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable e) {
            System.err.println("Docker not available - skipping integration tests");
            return false;
        }
    }

    /**
     * Cleanup database tables in correct order (respecting FK constraints).
     */
    private void cleanupDatabase() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("TRUNCATE TABLE auth_audit_logs, refresh_tokens, user_credentials, users RESTART IDENTITY CASCADE");
        }
    }

    /**
     * Cleanup all Redis keys.
     */
    private void cleanupRedis() {
        if (redisTemplate != null) {
            try {
              assert redisTemplate.getConnectionFactory() != null;
              redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands()
                    .flushAll();
            } catch (Exception e) {
                System.err.println("Redis cleanup failed: " + e.getMessage());
            }
        }
    }
}
