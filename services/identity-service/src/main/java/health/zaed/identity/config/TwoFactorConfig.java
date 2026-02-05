package health.zaed.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Two-Factor Authentication configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "zaed.identity.2fa")
public class TwoFactorConfig {

    /**
     * Issuer name shown in authenticator apps.
     */
    private String issuer = "Zaed";

    /**
     * Number of backup codes to generate.
     */
    private int backupCodesCount = 10;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getBackupCodesCount() {
        return backupCodesCount;
    }

    public void setBackupCodesCount(int backupCodesCount) {
        this.backupCodesCount = backupCodesCount;
    }

    public int getRecoveryCodeCount() {
        return backupCodesCount;
    }
}
