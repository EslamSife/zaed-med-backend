package health.zaed.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import health.zaed.identity.model.dto.LoginRequest;
import health.zaed.identity.model.dto.LoginResponse;
import health.zaed.identity.model.dto.RefreshTokenRequest;
import health.zaed.identity.model.dto.TokenResponse;
import health.zaed.identity.model.dto.TwoFactorVerifyRequest;
import health.zaed.identity.security.AuthPrincipal;
import health.zaed.identity.service.AuthService;

/**
 * REST controller for authentication operations.
 *
 * <p>Handles login, token refresh, logout, and 2FA verification for partner/admin users.
 */
@RestController
@RequestMapping("/api/{version}/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/login", version = "1")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        log.debug("Login attempt for email: {}", maskEmail(request.email()));
        LoginResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/2fa/verify", version = "1")
    public ResponseEntity<LoginResponse> verify2FA(@Valid @RequestBody TwoFactorVerifyRequest request, @RequestHeader("Authorization") String authHeader, HttpServletRequest httpRequest) {
        String tempToken = extractToken(authHeader);
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.verify2FA(request, tempToken, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Implements refresh token rotation - old token revoked, new one issued.
     */
    @PostMapping(path = "/refresh", version = "1")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        TokenResponse response = authService.refreshToken(request.refreshToken(), ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/logout", version = "1")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        authService.logout(request.refreshToken(), ipAddress, userAgent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/logout-all", version = "1")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        authService.logoutAll(principal.getUserId(), ipAddress, userAgent);
        return ResponseEntity.noContent().build();
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) return "**@" + email.substring(atIndex + 1);
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
    }
}
