package health.zaed.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Virtual Threads configuration for JDK 25 and Spring Boot 4.
 *
 * <p>Spring Boot 4 enables virtual threads automatically via the
 * {@code spring.threads.virtual.enabled=true} property in application.yml.
 * This handles Tomcat request processing on virtual threads.
 *
 * <p>This configuration additionally provides:
 * <ul>
 *   <li>Async task execution - {@code @Async} methods use virtual threads</li>
 * </ul>
 *
 * <p>Virtual threads provide lightweight concurrency, enabling high throughput
 * for I/O-bound operations without the overhead of platform threads. This is
 * particularly beneficial for database queries, external API calls, and Redis
 * operations common in the identity service.
 *
 * @see java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
