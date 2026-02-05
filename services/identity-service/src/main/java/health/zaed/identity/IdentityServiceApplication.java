package health.zaed.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Zaed Identity Service - Authentication and Authorization for Zaed Med Connect.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Phone OTP authentication for donors/requesters</li>
 *   <li>Email/Password + JWT authentication for partners</li>
 *   <li>Email/Password + 2FA authentication for admins</li>
 *   <li>Token management (access, refresh, temp tokens)</li>
 * </ul>
 *
 * @see <a href="../../architecture/AUTH.md">AUTH.md</a> for full documentation
 * @see <a href="../../architecture/adr/002-phone-first-authentication.md">ADR-002</a> for auth strategy
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IdentityServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
