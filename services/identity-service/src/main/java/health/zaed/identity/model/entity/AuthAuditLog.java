package health.zaed.identity.model.entity;

import jakarta.persistence.*;
import health.zaed.identity.model.enums.AuthEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log for authentication events.
 *
 * <p>Captures all auth-related events for security monitoring and compliance.
 */
@Entity
@Table(name = "auth_audit_logs", indexes = {
    @Index(name = "idx_auth_audit_user", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_auth_audit_ip", columnList = "ip_address, created_at DESC"),
    @Index(name = "idx_auth_audit_type", columnList = "event_type, created_at DESC"),
    @Index(name = "idx_auth_audit_created", columnList = "created_at DESC")
})
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuthEventType eventType;

    @Column(name = "user_id")
    private UUID userId;

    /**
     * Phone number for OTP events (when user_id might not exist yet).
     */
    @Column(length = 20)
    private String phone;

    /**
     * Email for login events.
     */
    private String email;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    /**
     * Additional details about the event.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * Risk score for fraud detection (0-100, higher = riskier).
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Additional event data as JSON.
     */
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuthAuditLog() {
        this.createdAt = Instant.now();
    }

    public AuthAuditLog(UUID id, AuthEventType eventType, UUID userId, String phone, String email,
                        String ipAddress, String userAgent, boolean success, String failureReason,
                        String details, Integer riskScore, String metadata, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.userId = userId;
        this.phone = phone;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
        this.failureReason = failureReason;
        this.details = details;
        this.riskScore = riskScore;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    // Getters
    public UUID getId() { return id; }
    public AuthEventType getEventType() { return eventType; }
    public UUID getUserId() { return userId; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
    public String getDetails() { return details; }
    public Integer getRiskScore() { return riskScore; }
    public String getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setEventType(AuthEventType eventType) { this.eventType = eventType; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setDetails(String details) { this.details = details; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Creates a success audit log entry.
     */
    public static AuthAuditLog success(AuthEventType eventType, UUID userId, String ipAddress, String userAgent) {
        return AuthAuditLog.builder()
            .eventType(eventType)
            .userId(userId)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .success(true)
            .build();
    }

    /**
     * Creates a failure audit log entry.
     */
    public static AuthAuditLog failure(AuthEventType eventType, String email, String ipAddress,
                                       String userAgent, String reason) {
        return AuthAuditLog.builder()
            .eventType(eventType)
            .email(email)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .success(false)
            .failureReason(reason)
            .build();
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private AuthEventType eventType;
        private UUID userId;
        private String phone;
        private String email;
        private String ipAddress;
        private String userAgent;
        private boolean success;
        private String failureReason;
        private String details;
        private Integer riskScore;
        private String metadata;
        private Instant createdAt = Instant.now();

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder eventType(AuthEventType eventType) { this.eventType = eventType; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder details(String details) { this.details = details; return this; }
        public Builder riskScore(Integer riskScore) { this.riskScore = riskScore; return this; }
        public Builder metadata(String metadata) { this.metadata = metadata; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public AuthAuditLog build() {
            return new AuthAuditLog(id, eventType, userId, phone, email, ipAddress, userAgent,
                    success, failureReason, details, riskScore, metadata, createdAt);
        }
    }
}
