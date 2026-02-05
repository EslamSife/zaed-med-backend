package health.zaed.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password configuration and encoder bean.
 */
@Configuration
@ConfigurationProperties(prefix = "zaed.identity.password")
public class PasswordConfig {

    /**
     * BCrypt strength (cost factor). Higher = slower but more secure.
     * Recommended: 12 for production.
     */
    private int bcryptStrength = 12;

    /**
     * Minimum password length.
     */
    private int minLength = 8;

    /**
     * Require at least one uppercase letter.
     */
    private boolean requireUppercase = true;

    /**
     * Require at least one number.
     */
    private boolean requireNumber = true;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.length() < minLength) {
            return false;
        }
        if (requireUppercase && !password.chars().anyMatch(Character::isUpperCase)) {
            return false;
        }
        if (requireNumber && !password.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        return true;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public boolean isRequireUppercase() {
        return requireUppercase;
    }

    public void setRequireUppercase(boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
    }

    public boolean isRequireNumber() {
        return requireNumber;
    }

    public void setRequireNumber(boolean requireNumber) {
        this.requireNumber = requireNumber;
    }
}
