package health.zaed.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties.
 *
 * <p>Phase 1 uses HS256 (symmetric). See ADR-008 for RS256 migration plan.
 */
@Configuration
@ConfigurationProperties(prefix = "zaed.identity.jwt")
public class JwtConfig {

    /**
     * Signing algorithm: HS256 (Phase 1) or RS256 (Phase 2).
     */
    private String algorithm = "HS256";

    /**
     * Secret key for HS256 (must be at least 256 bits / 32 bytes).
     */
    private String secret;

    /**
     * Path to RSA private key (Phase 2, auth service only).
     */
    private String privateKeyPath;

    /**
     * Path to RSA public key (Phase 2, all services).
     */
    private String publicKeyPath;

    /**
     * Token issuer claim.
     */
    private String issuer = "zaed.org";

    /**
     * Access token expiry in seconds (default: 1 hour).
     */
    private int accessTokenExpiry = 3600;

    /**
     * Refresh token expiry in seconds (default: 7 days).
     */
    private int refreshTokenExpiry = 604800;

    /**
     * Temporary token expiry in seconds (default: 15 minutes).
     */
    private int tempTokenExpiry = 900;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public void setAccessTokenExpiry(int accessTokenExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
    }

    public int getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    public void setRefreshTokenExpiry(int refreshTokenExpiry) {
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public int getTempTokenExpiry() {
        return tempTokenExpiry;
    }

    public void setTempTokenExpiry(int tempTokenExpiry) {
        this.tempTokenExpiry = tempTokenExpiry;
    }
}
