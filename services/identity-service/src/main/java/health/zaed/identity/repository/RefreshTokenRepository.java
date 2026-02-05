package health.zaed.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import health.zaed.identity.model.entity.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entity operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    default Optional<RefreshToken> findByTokenId(String tokenId) {
        return findById(tokenId);
    }

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash AND rt.revokedAt IS NULL AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidByTokenHash(@Param("hash") String tokenHash, @Param("now") Instant now);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now, rt.revokeReason = :reason WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now, rt.revokeReason = :reason WHERE rt.id = :id AND rt.revokedAt IS NULL")
    int revokeById(@Param("id") String id, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR (rt.revokedAt IS NOT NULL AND rt.revokedAt < :threshold)")
    int deleteExpiredAndRevoked(@Param("now") Instant now, @Param("threshold") Instant threshold);

    long countByUserIdAndRevokedAtIsNull(UUID userId);
}
