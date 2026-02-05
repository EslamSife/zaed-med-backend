package health.zaed.identity.service;

import io.jsonwebtoken.Claims;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import health.zaed.identity.config.JwtConfig;
import health.zaed.identity.exception.AuthException;
import health.zaed.identity.exception.InvalidTokenException;
import health.zaed.identity.exception.RateLimitException;
import health.zaed.identity.exception.TwoFactorException;
import health.zaed.identity.model.dto.LoginRequest;
import health.zaed.identity.model.dto.LoginResponse;
import health.zaed.identity.model.dto.TokenResponse;
import health.zaed.identity.model.dto.TwoFactorVerifyRequest;
import health.zaed.identity.model.entity.AuthAuditLog;
import health.zaed.identity.model.entity.RefreshToken;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.enums.AuthEventType;
import health.zaed.identity.model.enums.OtpContext;
import health.zaed.identity.repository.AuthAuditLogRepository;
import health.zaed.identity.repository.RefreshTokenRepository;
import health.zaed.identity.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Main authentication service orchestrating login, token management, and auditing.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthAuditLogRepository auditLogRepository;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthAuditLogRepository auditLogRepository,
                       JwtService jwtService,
                       TwoFactorService twoFactorService,
                       PasswordEncoder passwordEncoder,
                       JwtConfig jwtConfig) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.jwtService = jwtService;
        this.twoFactorService = twoFactorService;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
    }

    @Transactional
    public @NonNull LoginResponse login(
            @NonNull LoginRequest request,
            @NonNull String ipAddress,
            @Nullable String userAgent) {
        String email = request.email().toLowerCase().trim();

        checkAccountLockout(email, ipAddress);

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                auditLoginFailure(null, email, null, ipAddress, userAgent, "USER_NOT_FOUND");
                return new AuthException("INVALID_CREDENTIALS", "Invalid email or password");
            });

        if (user.getCredential() == null ||
            !passwordEncoder.matches(request.password(), user.getCredential().getPasswordHash())) {
            auditLoginFailure(user.getId(), email, null, ipAddress, userAgent, "INVALID_PASSWORD");
            throw new AuthException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        if (!user.isActive()) {
            auditLoginFailure(user.getId(), email, null, ipAddress, userAgent, "ACCOUNT_DISABLED");
            throw new AuthException("ACCOUNT_DISABLED", "Account is disabled");
        }

        if (twoFactorService.is2FAEnabled(user.getId())) {
            String tempToken = jwtService.generate2FATempToken(user.getId());
            auditLog(user.getId(), email, null, ipAddress, userAgent,
                AuthEventType.TWO_FACTOR_CHALLENGE, true, "2FA required");
            return LoginResponse.requires2FA(tempToken);
        }

        return generateLoginResponse(user, request.deviceId(), ipAddress, userAgent);
    }

    @Transactional
    public @NonNull LoginResponse verify2FA(
            @NonNull TwoFactorVerifyRequest request,
            @NonNull String tempToken,
            @NonNull String ipAddress,
            @Nullable String userAgent) {
        Claims claims;
        try {
            claims = jwtService.validateToken(tempToken);
        } catch (InvalidTokenException e) {
            throw new TwoFactorException("INVALID_2FA_TOKEN", "Invalid or expired 2FA session");
        }

        if (!"2fa_pending".equals(jwtService.getTokenType(claims))) {
            throw new TwoFactorException("INVALID_2FA_TOKEN", "Invalid 2FA token type");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new TwoFactorException("User not found"));

        boolean verified = false;
        String method = "totp";

        if (request.code() != null && !request.code().isBlank()) {
            verified = twoFactorService.verifyCode(userId, request.code());
        } else if (request.recoveryCode() != null && !request.recoveryCode().isBlank()) {
            verified = twoFactorService.verifyRecoveryCode(userId, request.recoveryCode());
            method = "recovery";
        }

        if (!verified) {
            auditLog(userId, user.getEmail(), null, ipAddress, userAgent,
                AuthEventType.TWO_FACTOR_FAILED, false, "Invalid " + method + " code");
            throw new TwoFactorException("INVALID_CODE", "Invalid verification code");
        }

        auditLog(userId, user.getEmail(), null, ipAddress, userAgent,
            AuthEventType.TWO_FACTOR_SUCCESS, true, "2FA verified via " + method);

        return generateLoginResponse(user, request.deviceId(), ipAddress, userAgent);
    }

    @Transactional
    public @NonNull TokenResponse refreshToken(
            @NonNull String refreshToken,
            @NonNull String ipAddress) {
        Claims claims;
        try {
            claims = jwtService.validateToken(refreshToken);
        } catch (InvalidTokenException e) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        if (!"refresh".equals(jwtService.getTokenType(claims))) {
            throw new InvalidTokenException("Invalid token type");
        }

        String tokenId = claims.getId();
        UUID userId = UUID.fromString(claims.getSubject());

        RefreshToken storedToken = refreshTokenRepository.findByTokenId(tokenId)
            .orElseThrow(() -> new InvalidTokenException("Token not found or revoked"));

        if (!storedToken.isValid()) {
            log.warn("Attempted use of invalid refresh token for user: {}", userId);
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now(), "SUSPICIOUS");
            throw new InvalidTokenException("Token has been revoked");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidTokenException("User not found"));

        if (!user.isActive()) {
            throw new AuthException("ACCOUNT_DISABLED", "Account is disabled");
        }

        storedToken.revoke("ROTATION");
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newTokenId = UUID.randomUUID().toString();
        String newRefreshToken = jwtService.generateRefreshToken(user, newTokenId, storedToken.getDeviceId());

        RefreshToken newStoredToken = RefreshToken.builder()
            .id(newTokenId)
            .userId(user.getId())
            .tokenHash(hashToken(newRefreshToken))
            .deviceId(storedToken.getDeviceId())
            .deviceInfo(storedToken.getDeviceInfo())
            .ipAddress(ipAddress)
            .expiresAt(Instant.now().plusSeconds(jwtConfig.getRefreshTokenExpiry()))
            .build();
        refreshTokenRepository.save(newStoredToken);

        log.debug("Refresh token rotated for user: {}", userId);
        return TokenResponse.of(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpiry());
    }

    @Transactional
    public void logout(String refreshToken, String ipAddress, String userAgent) {
        try {
            Claims claims = jwtService.validateToken(refreshToken);
            String tokenId = claims.getId();
            UUID userId = UUID.fromString(claims.getSubject());

            refreshTokenRepository.findByTokenId(tokenId)
                .ifPresent(token -> {
                    token.revoke("LOGOUT");
                    refreshTokenRepository.save(token);
                });

            auditLog(userId, null, null, ipAddress, userAgent,
                AuthEventType.LOGOUT, true, null);

        } catch (Exception e) {
            log.debug("Logout with invalid token: {}", e.getMessage());
        }
    }

    @Transactional
    public void logoutAll(UUID userId, String ipAddress, String userAgent) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now(), "LOGOUT_ALL");
        auditLog(userId, null, null, ipAddress, userAgent,
            AuthEventType.LOGOUT, true, "All devices");
    }

    public String generateTempToken(String phone, OtpContext context, UUID referenceId, String trackingCode) {
        return jwtService.generateTempToken(phone, context, referenceId, trackingCode);
    }

    private LoginResponse generateLoginResponse(User user, String deviceId, String ipAddress, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
        String tokenId = UUID.randomUUID().toString();
        String refreshTokenStr = jwtService.generateRefreshToken(user, tokenId, deviceId);

        // Store refresh token
        RefreshToken refreshToken = RefreshToken.builder()
            .id(tokenId)
            .userId(user.getId())
            .tokenHash(hashToken(refreshTokenStr))
            .deviceId(deviceId)
            .deviceInfo(userAgent)
            .ipAddress(ipAddress)
            .expiresAt(Instant.now().plusSeconds(jwtConfig.getRefreshTokenExpiry()))
            .build();
        refreshTokenRepository.save(refreshToken);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditLog(user.getId(), user.getEmail(), null, ipAddress, userAgent,
            AuthEventType.LOGIN_SUCCESS, true, null);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
            user.getId().toString(),
            user.getEmail(),
            user.getName(),
            user.getRole().name(),
            user.getPartnerId() != null ? user.getPartnerId().toString() : null
        );

        return LoginResponse.success(
            accessToken,
            refreshTokenStr,
            jwtService.getAccessTokenExpiry(),
            userInfo
        );
    }

    private void checkAccountLockout(String email, String ipAddress) {
        Instant since = Instant.now().minus(LOCKOUT_MINUTES, ChronoUnit.MINUTES);

        // Check failed attempts by email
        long failedByEmail = auditLogRepository.countFailedLoginsByEmailSince(email, since);
        if (failedByEmail >= MAX_FAILED_LOGINS) {
            int retryAfter = (int) (LOCKOUT_MINUTES * 60 - ChronoUnit.SECONDS.between(since, Instant.now()));
            throw new RateLimitException(
                "Account temporarily locked due to too many failed attempts",
                Math.max(retryAfter, 60)
            );
        }

        // Check failed attempts by IP
        long failedByIp = auditLogRepository.countFailedAttemptsByIpSince(
            ipAddress, AuthEventType.LOGIN_FAILED, since);
        if (failedByIp >= MAX_FAILED_LOGINS * 2) {
            throw new RateLimitException("Too many failed attempts from this IP", LOCKOUT_MINUTES * 60);
        }
    }

    private void auditLoginFailure(UUID userId, String email, String phone,
                                   String ipAddress, String userAgent, String details) {
        auditLog(userId, email, phone, ipAddress, userAgent, AuthEventType.LOGIN_FAILED, false, details);
    }

    private void auditLog(UUID userId, String email, String phone,
                          String ipAddress, String userAgent, AuthEventType eventType,
                          boolean success, String details) {
        AuthAuditLog log = new AuthAuditLog();
        log.setUserId(userId);
        log.setEmail(email);
        log.setPhone(phone);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setEventType(eventType);
        log.setSuccess(success);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
