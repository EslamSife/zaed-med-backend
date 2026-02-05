package health.zaed.identity.model.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Two-Factor Authentication settings for a user.
 *
 * <p>Stores TOTP secret (encrypted) and backup codes (hashed).
 */
@Entity
@Table(name = "user_2fa")
public class User2FA {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * TOTP secret - should be encrypted at rest using AES-256.
     * Base32 encoded for authenticator app compatibility.
     */
    @Column(name = "totp_secret_encrypted")
    private String totpSecretEncrypted;

    @Column(name = "is_enabled")
    private boolean enabled = false;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    /**
     * Backup codes - stored as BCrypt hashes.
     * Each code can only be used once.
     */
    @ElementCollection
    @CollectionTable(name = "user_2fa_backup_codes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "code_hash")
    private List<String> backupCodesHash = new ArrayList<>();

    @Column(name = "backup_codes_used")
    private int backupCodesUsed = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public User2FA() {
        this.enabled = false;
        this.backupCodesHash = new ArrayList<>();
        this.backupCodesUsed = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public User2FA(UUID userId, User user, String totpSecretEncrypted, boolean enabled,
                   Instant enabledAt, List<String> backupCodesHash, int backupCodesUsed,
                   Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.user = user;
        this.totpSecretEncrypted = totpSecretEncrypted;
        this.enabled = enabled;
        this.enabledAt = enabledAt;
        this.backupCodesHash = backupCodesHash != null ? backupCodesHash : new ArrayList<>();
        this.backupCodesUsed = backupCodesUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getTotpSecretEncrypted() { return totpSecretEncrypted; }
    public boolean isEnabled() { return enabled; }
    public Instant getEnabledAt() { return enabledAt; }
    public List<String> getBackupCodesHash() { return backupCodesHash; }
    public int getBackupCodesUsed() { return backupCodesUsed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setUser(User user) { this.user = user; }
    public void setTotpSecretEncrypted(String totpSecretEncrypted) { this.totpSecretEncrypted = totpSecretEncrypted; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setEnabledAt(Instant enabledAt) { this.enabledAt = enabledAt; }
    public void setBackupCodesHash(List<String> backupCodesHash) { this.backupCodesHash = backupCodesHash; }
    public void setBackupCodesUsed(int backupCodesUsed) { this.backupCodesUsed = backupCodesUsed; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Enables 2FA for this user.
     */
    public void enable() {
        this.enabled = true;
        this.enabledAt = Instant.now();
    }

    /**
     * Disables 2FA for this user.
     */
    public void disable() {
        this.enabled = false;
        this.totpSecretEncrypted = null;
        this.backupCodesHash.clear();
        this.backupCodesUsed = 0;
    }

    /**
     * Records use of a backup code.
     *
     * @param codeIndex the index of the used code to remove
     */
    public void useBackupCode(int codeIndex) {
        if (codeIndex >= 0 && codeIndex < backupCodesHash.size()) {
            backupCodesHash.remove(codeIndex);
            backupCodesUsed++;
        }
    }

    /**
     * Gets the number of remaining backup codes.
     */
    public int getRemainingBackupCodes() {
        return backupCodesHash.size();
    }

    /**
     * Gets the secret (alias for TwoFactorService compatibility).
     */
    public String getSecret() {
        return totpSecretEncrypted;
    }

    /**
     * Sets the secret (alias for TwoFactorService compatibility).
     */
    public void setSecret(String secret) {
        this.totpSecretEncrypted = secret;
    }

    /**
     * Gets recovery codes (alias for TwoFactorService compatibility).
     */
    public List<String> getRecoveryCodes() {
        return backupCodesHash;
    }

    /**
     * Sets recovery codes (alias for TwoFactorService compatibility).
     */
    public void setRecoveryCodes(List<String> codes) {
        this.backupCodesHash = codes != null ? new ArrayList<>(codes) : new ArrayList<>();
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID userId;
        private User user;
        private String totpSecretEncrypted;
        private boolean enabled = false;
        private Instant enabledAt;
        private List<String> backupCodesHash = new ArrayList<>();
        private int backupCodesUsed = 0;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder totpSecretEncrypted(String totpSecretEncrypted) { this.totpSecretEncrypted = totpSecretEncrypted; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder enabledAt(Instant enabledAt) { this.enabledAt = enabledAt; return this; }
        public Builder backupCodesHash(List<String> backupCodesHash) { this.backupCodesHash = backupCodesHash; return this; }
        public Builder backupCodesUsed(int backupCodesUsed) { this.backupCodesUsed = backupCodesUsed; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public User2FA build() {
            return new User2FA(userId, user, totpSecretEncrypted, enabled, enabledAt,
                    backupCodesHash, backupCodesUsed, createdAt, updatedAt);
        }
    }
}
