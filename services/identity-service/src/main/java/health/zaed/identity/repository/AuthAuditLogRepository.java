package health.zaed.identity.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import health.zaed.identity.model.entity.AuthAuditLog;
import health.zaed.identity.model.enums.AuthEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AuthAuditLog entity operations.
 */
@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    Page<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuthAuditLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.phone = :phone AND a.eventType = :eventType AND a.createdAt > :since")
    long countByPhoneAndEventTypeSince(
        @Param("phone") String phone,
        @Param("eventType") AuthEventType eventType,
        @Param("since") Instant since
    );

    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.ipAddress = :ip AND a.eventType = :eventType AND a.success = false AND a.createdAt > :since")
    long countFailedAttemptsByIpSince(
        @Param("ip") String ipAddress,
        @Param("eventType") AuthEventType eventType,
        @Param("since") Instant since
    );

    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.email = :email AND a.eventType = 'LOGIN_FAILED' AND a.createdAt > :since")
    long countFailedLoginsByEmailSince(
        @Param("email") String email,
        @Param("since") Instant since
    );

    List<AuthAuditLog> findByUserIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
        UUID userId, AuthEventType eventType, Instant since
    );
}
