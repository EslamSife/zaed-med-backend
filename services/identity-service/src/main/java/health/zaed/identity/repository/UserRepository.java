package health.zaed.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import health.zaed.identity.model.entity.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.credential WHERE u.email = :email")
    Optional<User> findByEmailWithCredential(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.twoFactorAuth WHERE u.id = :id")
    Optional<User> findByIdWith2FA(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.credential LEFT JOIN FETCH u.twoFactorAuth WHERE u.email = :email")
    Optional<User> findByEmailWithCredentialAnd2FA(@Param("email") String email);
}
