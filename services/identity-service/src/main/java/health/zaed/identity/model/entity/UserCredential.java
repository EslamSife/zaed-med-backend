package health.zaed.identity.model.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User credentials for partners and admins (email/password authentication).
 *
 * <p>Donors and requesters don't have credentials - they use OTP only.
 */
@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt = Instant.now();

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password")
    private boolean mustChangePassword = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserCredential() {
        this.passwordChangedAt = Instant.now();
        this.failedLoginAttempts = 0;
        this.mustChangePassword = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UserCredential(UUID userId, User user, String passwordHash, Instant passwordChangedAt,
                          int failedLoginAttempts, Instant lockedUntil, boolean mustChangePassword,
                          Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.user = user;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setUser(User user) { this.user = user; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if the account is currently locked.
     */
    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Increments failed login attempts and locks account if threshold reached.
     *
     * @param lockoutThreshold number of attempts before lockout
     * @param lockoutDurationMinutes how long to lock the account
     */
    public void recordFailedLogin(int lockoutThreshold, int lockoutDurationMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= lockoutThreshold) {
            this.lockedUntil = Instant.now().plusSeconds(lockoutDurationMinutes * 60L);
        }
    }

    /**
     * Resets failed login attempts after successful login.
     */
    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID userId;
        private User user;
        private String passwordHash;
        private Instant passwordChangedAt = Instant.now();
        private int failedLoginAttempts = 0;
        private Instant lockedUntil;
        private boolean mustChangePassword = false;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder passwordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; return this; }
        public Builder failedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; return this; }
        public Builder lockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; return this; }
        public Builder mustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public UserCredential build() {
            return new UserCredential(userId, user, passwordHash, passwordChangedAt,
                    failedLoginAttempts, lockedUntil, mustChangePassword, createdAt, updatedAt);
        }
    }
}
