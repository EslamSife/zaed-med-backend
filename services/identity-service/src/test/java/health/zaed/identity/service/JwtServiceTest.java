package health.zaed.identity.service;

import health.zaed.identity.config.JwtConfig;
import health.zaed.identity.exception.InvalidTokenException;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.enums.OtpContext;
import health.zaed.identity.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>Tests JWT token generation and validation without Spring context.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    // A valid 256-bit (32 byte) secret encoded in Base64 for HS256
    // The secret must be at least 32 bytes when decoded from Base64
    private static final String TEST_SECRET = Base64.getEncoder()
        .encodeToString("this-is-a-test-secret-key-256bit".getBytes());
    private static final String TEST_ISSUER = "test-issuer";

    private JwtService jwtService;
    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret(TEST_SECRET);
        jwtConfig.setIssuer(TEST_ISSUER);
        jwtConfig.setAccessTokenExpiry(3600);
        jwtConfig.setRefreshTokenExpiry(604800);
        jwtConfig.setTempTokenExpiry(900);

        jwtService = new JwtService(jwtConfig);
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("should generate valid access token for partner user")
        void shouldGenerateValidAccessTokenForPartner() {
            User user = createPartnerUser();

            String token = jwtService.generateAccessToken(user);

            assertThat(token).isNotBlank();
            Claims claims = jwtService.validateToken(token);
            assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
            assertThat(claims.get("type")).isEqualTo("access");
            assertThat(claims.get("email")).isEqualTo(user.getEmail());
            assertThat(claims.get("role")).isEqualTo("PARTNER_PHARMACY");
            assertThat(claims.getIssuer()).isEqualTo(TEST_ISSUER);
        }

        @Test
        @DisplayName("should include permissions in token claims")
        void shouldIncludePermissionsInClaims() {
            User user = createPartnerUser();

            String token = jwtService.generateAccessToken(user);

            Claims claims = jwtService.validateToken(token);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            assertThat(permissions)
                .contains("PARTNER_DASHBOARD_VIEW", "MATCH_VIEW_ASSIGNED");
        }

        @Test
        @DisplayName("should include partnerId when present")
        void shouldIncludePartnerIdWhenPresent() {
            UUID partnerId = UUID.randomUUID();
            User user = createPartnerUser();
            user.setPartnerId(partnerId);

            String token = jwtService.generateAccessToken(user);

            Claims claims = jwtService.validateToken(token);
            assertThat(claims.get("partnerId")).isEqualTo(partnerId.toString());
        }

        @Test
        @DisplayName("should set null partnerId when not present")
        void shouldSetNullPartnerIdWhenNotPresent() {
            User user = createPartnerUser();
            user.setPartnerId(null);

            String token = jwtService.generateAccessToken(user);

            Claims claims = jwtService.validateToken(token);
            assertThat(claims.get("partnerId")).isNull();
        }

        @Test
        @DisplayName("should generate token for admin with all permissions")
        void shouldGenerateTokenForAdminWithAllPermissions() {
            User admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@zaed.org")
                .role(UserRole.ADMIN)
                .build();

            String token = jwtService.generateAccessToken(admin);

            Claims claims = jwtService.validateToken(token);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            // Admin has all permissions
            assertThat(permissions).contains(
                "DONATION_VIEW_ALL",
                "ADMIN_DASHBOARD_VIEW",
                "USERS_MANAGE"
            );
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("should generate valid refresh token with jti")
        void shouldGenerateValidRefreshTokenWithJti() {
            User user = createPartnerUser();
            String tokenId = UUID.randomUUID().toString();
            String deviceId = "device-123";

            String token = jwtService.generateRefreshToken(user, tokenId, deviceId);

            assertThat(token).isNotBlank();
            Claims claims = jwtService.validateToken(token);
            assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
            assertThat(claims.get("type")).isEqualTo("refresh");
            assertThat(claims.getId()).isEqualTo(tokenId);
            assertThat(claims.get("deviceId")).isEqualTo(deviceId);
        }

        @Test
        @DisplayName("should allow null deviceId")
        void shouldAllowNullDeviceId() {
            User user = createPartnerUser();
            String tokenId = UUID.randomUUID().toString();

            String token = jwtService.generateRefreshToken(user, tokenId, null);

            Claims claims = jwtService.validateToken(token);
            assertThat(claims.get("deviceId")).isNull();
        }
    }

    @Nested
    @DisplayName("generateTempToken")
    class GenerateTempToken {

        @Test
        @DisplayName("should generate temp token for donor OTP flow")
        void shouldGenerateTempTokenForDonorFlow() {
            String phone = "+201234567890";
            UUID referenceId = UUID.randomUUID();
            String trackingCode = "ZAED-ABC123";

            String token = jwtService.generateTempToken(
                phone, OtpContext.DONATION, referenceId, trackingCode
            );

            assertThat(token).isNotBlank();
            Claims claims = jwtService.validateToken(token);
            assertThat(claims.getSubject()).isEqualTo("phone:" + phone);
            assertThat(claims.get("type")).isEqualTo("temp");
            assertThat(claims.get("context")).isEqualTo("DONATION");
            assertThat(claims.get("referenceId")).isEqualTo(referenceId.toString());
            assertThat(claims.get("trackingCode")).isEqualTo(trackingCode);
        }

        @Test
        @DisplayName("should include donor permissions for donation context")
        void shouldIncludeDonorPermissions() {
            String token = jwtService.generateTempToken(
                "+201234567890", OtpContext.DONATION, UUID.randomUUID(), "TRACK-123"
            );

            Claims claims = jwtService.validateToken(token);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            assertThat(permissions).contains("DONATION_UPLOAD_IMAGE", "DONATION_VIEW_OWN");
        }
    }

    @Nested
    @DisplayName("generate2FATempToken")
    class Generate2FATempToken {

        @Test
        @DisplayName("should generate 2FA pending token")
        void shouldGenerate2FAPendingToken() {
            UUID userId = UUID.randomUUID();

            String token = jwtService.generate2FATempToken(userId);

            Claims claims = jwtService.validateToken(token);
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.get("type")).isEqualTo("2fa_pending");
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate and return claims for valid token")
        void shouldValidateAndReturnClaims() {
            User user = createPartnerUser();
            String token = jwtService.generateAccessToken(user);

            Claims claims = jwtService.validateToken(token);

            assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
            assertThat(claims.getIssuer()).isEqualTo(TEST_ISSUER);
        }

        @Test
        @DisplayName("should throw InvalidTokenException for expired token")
        void shouldThrowForExpiredToken() {
            // Create a config with 0 expiry
            JwtConfig expiredConfig = new JwtConfig();
            expiredConfig.setSecret(TEST_SECRET);
            expiredConfig.setIssuer(TEST_ISSUER);
            expiredConfig.setAccessTokenExpiry(0);
            JwtService expiredService = new JwtService(expiredConfig);

            User user = createPartnerUser();
            String token = expiredService.generateAccessToken(user);

            // Token expires immediately
            assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("should throw InvalidTokenException for wrong issuer")
        void shouldThrowForWrongIssuer() {
            // Create token with different issuer
            JwtConfig otherConfig = new JwtConfig();
            otherConfig.setSecret(TEST_SECRET);
            otherConfig.setIssuer("other-issuer");
            otherConfig.setAccessTokenExpiry(3600);
            JwtService otherService = new JwtService(otherConfig);

            User user = createPartnerUser();
            String token = otherService.generateAccessToken(user);

            assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid token");
        }

        @Test
        @DisplayName("should throw InvalidTokenException for tampered token")
        void shouldThrowForTamperedToken() {
            User user = createPartnerUser();
            String token = jwtService.generateAccessToken(user);
            String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

            assertThatThrownBy(() -> jwtService.validateToken(tamperedToken))
                .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidTokenException for malformed token")
        void shouldThrowForMalformedToken() {
            assertThatThrownBy(() -> jwtService.validateToken("not.a.valid.token"))
                .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("extractSubjectUnsafe")
    class ExtractSubjectUnsafe {

        @Test
        @DisplayName("should extract subject without validation")
        void shouldExtractSubjectWithoutValidation() {
            User user = createPartnerUser();
            String token = jwtService.generateAccessToken(user);

            String subject = jwtService.extractSubjectUnsafe(token);

            assertThat(subject).isEqualTo(user.getId().toString());
        }

        @Test
        @DisplayName("should return null for malformed token")
        void shouldReturnNullForMalformedToken() {
            String subject = jwtService.extractSubjectUnsafe("invalid-token");

            assertThat(subject).isNull();
        }
    }

    @Nested
    @DisplayName("getTokenType")
    class GetTokenType {

        @Test
        @DisplayName("should return access for access token")
        void shouldReturnAccessForAccessToken() {
            User user = createPartnerUser();
            String token = jwtService.generateAccessToken(user);
            Claims claims = jwtService.validateToken(token);

            String type = jwtService.getTokenType(claims);

            assertThat(type).isEqualTo("access");
        }

        @Test
        @DisplayName("should return refresh for refresh token")
        void shouldReturnRefreshForRefreshToken() {
            User user = createPartnerUser();
            String token = jwtService.generateRefreshToken(user, "token-id", null);
            Claims claims = jwtService.validateToken(token);

            String type = jwtService.getTokenType(claims);

            assertThat(type).isEqualTo("refresh");
        }
    }

    private User createPartnerUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("partner@pharmacy.com")
            .name("Test Pharmacy")
            .role(UserRole.PARTNER_PHARMACY)
            .active(true)
            .build();
    }
}
