package health.zaed.identity.model.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity for session management and token revocation.
 *
 * <p>Refresh tokens are stored in the database to enable:
 * <ul>
 *   <li>Token revocation (logout)</li>
 *   <li>Token rotation (security best practice)</li>
 *   <li>Multi-device session management</li>
 *   <li>Audit trail</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_hash", columnList = "token_hash"),
    @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")
})
public class RefreshToken {

    /**
     * Token ID (jti claim in JWT).
     */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * SHA-256 hash of the token for secure lookup.
     */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /**
     * Device fingerprint for multi-device management.
     */
    @Column(name = "device_id")
    private String deviceId;

    /**
     * User agent string for audit purposes.
     */
    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    /**
     * IP address for security monitoring.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Reason for revocation (LOGOUT, PASSWORD_CHANGE, SUSPICIOUS, ADMIN).
     */
    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RefreshToken() {
        this.createdAt = Instant.now();
    }

    public RefreshToken(String id, UUID userId, User user, String tokenHash, String deviceId,
                        String deviceInfo, String ipAddress, Instant lastUsedAt, Instant expiresAt,
                        Instant revokedAt, String revokeReason, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.user = user;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
        this.createdAt = createdAt;
    }

    // Getters
    public String getId() { return id; }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setUser(User user) { this.user = user; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Checks if this token is valid (not expired and not revoked).
     */
    public boolean isValid() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    /**
     * Revokes this token.
     *
     * @param reason the reason for revocation
     */
    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }

    /**
     * Records that this token was used.
     */
    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private UUID userId;
        private User user;
        private String tokenHash;
        private String deviceId;
        private String deviceInfo;
        private String ipAddress;
        private Instant lastUsedAt;
        private Instant expiresAt;
        private Instant revokedAt;
        private String revokeReason;
        private Instant createdAt = Instant.now();

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder tokenHash(String tokenHash) { this.tokenHash = tokenHash; return this; }
        public Builder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public Builder deviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder lastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder revokedAt(Instant revokedAt) { this.revokedAt = revokedAt; return this; }
        public Builder revokeReason(String revokeReason) { this.revokeReason = revokeReason; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public RefreshToken build() {
            return new RefreshToken(id, userId, user, tokenHash, deviceId, deviceInfo,
                    ipAddress, lastUsedAt, expiresAt, revokedAt, revokeReason, createdAt);
        }
    }
}
