package health.zaed.identity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import health.zaed.identity.config.JwtConfig;
import health.zaed.identity.exception.InvalidTokenException;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.enums.OtpContext;
import health.zaed.identity.model.enums.Permission;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT token generation and validation service.
 *
 * <p>Phase 1: Uses HS256 (symmetric) signing.
 * <p>Phase 2: Will migrate to RS256 (asymmetric). See ADR-008.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtConfig jwtConfig;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public @NonNull String generateAccessToken(@NonNull User user) {
        List<String> permissions = Permission.getPermissionsForRole(user.getRole())
            .stream()
            .map(Enum::name)
            .toList();

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("type", "access")
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .claim("permissions", permissions)
            .claim("partnerId", user.getPartnerId() != null ? user.getPartnerId().toString() : null)
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(jwtConfig.getAccessTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public @NonNull String generateRefreshToken(
            @NonNull User user,
            @NonNull String tokenId,
            @Nullable String deviceId) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("type", "refresh")
            .id(tokenId)
            .claim("deviceId", deviceId)
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(jwtConfig.getRefreshTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public @NonNull String generateTempToken(
            @NonNull String phone,
            @NonNull OtpContext context,
            @NonNull UUID referenceId,
            @NonNull String trackingCode) {
        List<String> permissions = List.of(context.getGrantedPermissions())
            .stream()
            .map(Permission::name)
            .toList();

        return Jwts.builder()
            .subject("phone:" + phone)
            .claim("type", "temp")
            .claim("context", context.name())
            .claim("referenceId", referenceId.toString())
            .claim("trackingCode", trackingCode)
            .claim("permissions", permissions)
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(jwtConfig.getTempTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public @NonNull String generate2FATempToken(@NonNull UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "2fa_pending")
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(getSigningKey())
            .compact();
    }

    public @NonNull Claims validateToken(@NonNull String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtConfig.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw new InvalidTokenException("Token has expired");
        } catch (JwtException e) {
            log.debug("Invalid token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Extracts subject from token without validation. For logging only - do not use for authorization.
     */
    public @Nullable String extractSubjectUnsafe(@NonNull String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Decoders.BASE64URL.decode(parts[1]));
                if (payload.contains("\"sub\"")) {
                    int start = payload.indexOf("\"sub\":\"") + 7;
                    int end = payload.indexOf("\"", start);
                    return payload.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.trace("Could not extract subject from token: {}", e.getMessage());
        }
        return null;
    }

    public @Nullable String getTokenType(@NonNull Claims claims) {
        return claims.get("type", String.class);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public int getAccessTokenExpiry() {
        return jwtConfig.getAccessTokenExpiry();
    }

    public int getRefreshTokenExpiry() {
        return jwtConfig.getRefreshTokenExpiry();
    }
}
