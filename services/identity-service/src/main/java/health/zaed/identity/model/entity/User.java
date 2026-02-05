package health.zaed.identity.model.entity;

import jakarta.persistence.*;
import health.zaed.identity.model.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing all user types in the system.
 *
 * <p>Users can be:
 * <ul>
 *   <li>Donors/Requesters - identified by phone, may not have full account</li>
 *   <li>Partners - have email/password credentials</li>
 *   <li>Admins - have email/password + 2FA required</li>
 * </ul>
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_phone", columnList = "phone"),
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_role", columnList = "role")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(unique = true)
    private String email;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(name = "is_verified")
    private boolean verified = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "preferred_city", length = 100)
    private String preferredCity;

    @Column(name = "preferred_area", length = 100)
    private String preferredArea;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserCredential credential;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private User2FA twoFactorAuth;

    public User() {
        this.verified = false;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public User(UUID id, String phone, String email, String name, UserRole role,
                boolean verified, boolean active, String preferredCity, String preferredArea,
                UUID partnerId, Instant lastLoginAt, Instant createdAt, Instant updatedAt,
                UserCredential credential, User2FA twoFactorAuth) {
        this.id = id;
        this.phone = phone;
        this.email = email;
        this.name = name;
        this.role = role;
        this.verified = verified;
        this.active = active;
        this.preferredCity = preferredCity;
        this.preferredArea = preferredArea;
        this.partnerId = partnerId;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.credential = credential;
        this.twoFactorAuth = twoFactorAuth;
    }

    // Getters
    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public UserRole getRole() { return role; }
    public boolean isVerified() { return verified; }
    public boolean isActive() { return active; }
    public String getPreferredCity() { return preferredCity; }
    public String getPreferredArea() { return preferredArea; }
    public UUID getPartnerId() { return partnerId; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UserCredential getCredential() { return credential; }
    public User2FA getTwoFactorAuth() { return twoFactorAuth; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setName(String name) { this.name = name; }
    public void setRole(UserRole role) { this.role = role; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public void setActive(boolean active) { this.active = active; }
    public void setPreferredCity(String preferredCity) { this.preferredCity = preferredCity; }
    public void setPreferredArea(String preferredArea) { this.preferredArea = preferredArea; }
    public void setPartnerId(UUID partnerId) { this.partnerId = partnerId; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCredential(UserCredential credential) { this.credential = credential; }
    public void setTwoFactorAuth(User2FA twoFactorAuth) { this.twoFactorAuth = twoFactorAuth; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if this user requires 2FA based on their role.
     */
    public boolean requires2FA() {
        return role != null && role.requires2FA();
    }

    /**
     * Checks if this user has 2FA enabled.
     */
    public boolean has2FAEnabled() {
        return twoFactorAuth != null && twoFactorAuth.isEnabled();
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private String phone;
        private String email;
        private String name;
        private UserRole role;
        private boolean verified = false;
        private boolean active = true;
        private String preferredCity;
        private String preferredArea;
        private UUID partnerId;
        private Instant lastLoginAt;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private UserCredential credential;
        private User2FA twoFactorAuth;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder role(UserRole role) { this.role = role; return this; }
        public Builder verified(boolean verified) { this.verified = verified; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder preferredCity(String preferredCity) { this.preferredCity = preferredCity; return this; }
        public Builder preferredArea(String preferredArea) { this.preferredArea = preferredArea; return this; }
        public Builder partnerId(UUID partnerId) { this.partnerId = partnerId; return this; }
        public Builder lastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder credential(UserCredential credential) { this.credential = credential; return this; }
        public Builder twoFactorAuth(User2FA twoFactorAuth) { this.twoFactorAuth = twoFactorAuth; return this; }

        public User build() {
            return new User(id, phone, email, name, role, verified, active,
                    preferredCity, preferredArea, partnerId, lastLoginAt,
                    createdAt, updatedAt, credential, twoFactorAuth);
        }
    }
}
