package health.zaed.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import health.zaed.identity.TestcontainersConfig;
import health.zaed.identity.model.dto.LoginRequest;
import health.zaed.identity.model.dto.RefreshTokenRequest;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.entity.UserCredential;
import health.zaed.identity.model.enums.UserRole;
import health.zaed.identity.repository.RefreshTokenRepository;
import health.zaed.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController}.
 *
 * <p>Tests the full authentication flow with real database via Testcontainers.
 *
 * <p>Requires Docker to be running. Skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@DisplayName("AuthController Integration Tests")
@EnabledIf("isDockerAvailable")
class AuthControllerIT {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    private static final String TEST_EMAIL = "partner@pharmacy.com";
    private static final String TEST_PASSWORD = "SecurePassword123!";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clear all test data
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        createTestUser(TEST_EMAIL, TEST_PASSWORD, UserRole.PARTNER_PHARMACY);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should return tokens for valid credentials")
        void shouldReturnTokensForValidCredentials() throws Exception {
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.user.role").value("PARTNER_PHARMACY"))
                .andExpect(jsonPath("$.requires2FA").doesNotExist());
        }

        @Test
        @DisplayName("should return 401 for invalid email")
        void shouldReturn401ForInvalidEmail() throws Exception {
            LoginRequest request = new LoginRequest("nonexistent@example.com", TEST_PASSWORD, null);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("should return 401 for invalid password")
        void shouldReturn401ForInvalidPassword() throws Exception {
            LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongpassword", null);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("should return 400 for missing email")
        void shouldReturn400ForMissingEmail() throws Exception {
            String requestBody = "{\"password\": \"password123\"}";

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmailFormat() throws Exception {
            LoginRequest request = new LoginRequest("not-an-email", TEST_PASSWORD, null);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 for disabled account")
        void shouldReturn401ForDisabledAccount() throws Exception {
            // Disable the test user
            User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
            user.setActive(false);
            userRepository.save(user);

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        }

        @Test
        @DisplayName("should include device ID in refresh token when provided")
        void shouldIncludeDeviceIdWhenProvided() throws Exception {
            String deviceId = "test-device-123";
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, deviceId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

            // Verify device ID was stored
            var tokens = refreshTokenRepository.findAll();
            org.assertj.core.api.Assertions.assertThat(tokens)
                .hasSize(1)
                .first()
                .extracting("deviceId")
                .isEqualTo(deviceId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshToken {

        @Test
        @DisplayName("should return new tokens for valid refresh token")
        void shouldReturnNewTokensForValidRefreshToken() throws Exception {
            // First login to get tokens
            LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

            String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

            // Use refresh token to get new tokens
            RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600));
        }

        @Test
        @DisplayName("should return 401 for invalid refresh token")
        void shouldReturn401ForInvalidRefreshToken() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("invalid.refresh.token");

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should rotate refresh token (old one becomes invalid)")
        void shouldRotateRefreshToken() throws Exception {
            // Login
            LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

            String oldRefreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

            // Refresh once
            RefreshTokenRequest refreshRequest = new RefreshTokenRequest(oldRefreshToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

            // Try to use old refresh token again - should fail
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("should revoke refresh token on logout")
        void shouldRevokeRefreshTokenOnLogout() throws Exception {
            // Login
            LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

            String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

            // Logout
            RefreshTokenRequest logoutRequest = new RefreshTokenRequest(refreshToken);

            mockMvc.perform(post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

            // Try to use the refresh token - should fail
            RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 204 even with invalid token (idempotent)")
        void shouldReturn204EvenWithInvalidToken() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("invalid.token");

            mockMvc.perform(post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
        }
    }

    private void createTestUser(String email, String password, UserRole role) {
        User user = User.builder()
            .email(email)
            .name("Test User")
            .role(role)
            .active(true)
            .verified(true)
            .build();

        UserCredential credential = UserCredential.builder()
            .passwordHash(passwordEncoder.encode(password))
            .build();

        credential.setUser(user);
        user.setCredential(credential);

        userRepository.save(user);
    }
}
