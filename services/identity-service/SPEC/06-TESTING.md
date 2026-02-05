# Test Suite Implementation

## Overview

**Feature**: Comprehensive test coverage
**Priority**: P0 - CRITICAL
**Effort**: High (16-24 hours)
**Risk**: None (tests only)

---

## Current State

### Test Infrastructure Ready

**pom.xml dependencies**
```xml
<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Security Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- TestContainers - PostgreSQL -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>

<!-- TestContainers - Redis -->
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <version>2.2.0</version>
    <scope>test</scope>
</dependency>
```

### Current Test Classes
```
src/test/java/
└── (EMPTY!)
```

---

## Target Test Structure

```
src/test/java/health/zaed/identity/
├── IdentityServiceApplicationTests.java       # Context loads
├── TestcontainersConfig.java                  # Shared containers
│
├── unit/
│   ├── service/
│   │   ├── AuthServiceTest.java              # Auth logic
│   │   ├── JwtServiceTest.java               # JWT generation/validation
│   │   ├── OtpServiceTest.java               # OTP logic
│   │   └── TwoFactorServiceTest.java         # 2FA TOTP logic
│   ├── security/
│   │   ├── JwtAuthenticationFilterTest.java  # Filter logic
│   │   └── AuthPrincipalTest.java            # Principal record
│   └── model/
│       └── PermissionTest.java               # Permission mapping
│
├── integration/
│   ├── controller/
│   │   ├── AuthControllerIT.java             # Login flow
│   │   ├── OtpControllerIT.java              # OTP flow
│   │   └── TwoFactorControllerIT.java        # 2FA flow
│   ├── repository/
│   │   ├── UserRepositoryIT.java             # User CRUD
│   │   └── RefreshTokenRepositoryIT.java     # Token CRUD
│   └── security/
│       └── SecurityConfigIT.java             # Auth rules
│
└── e2e/
    ├── LoginFlowE2ETest.java                 # Full login scenarios
    ├── OtpFlowE2ETest.java                   # Full OTP scenarios
    └── TwoFactorFlowE2ETest.java             # Full 2FA scenarios
```

---

## Test Implementation Details

### 1. TestContainers Configuration

**TestcontainersConfig.java**
```java
package health.zaed.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import com.redis.testcontainers.RedisContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("identity_test")
            .withUsername("test")
            .withPassword("test");
    }

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return new RedisContainer("redis:7-alpine");
    }
}
```

### 2. Application Context Test

**IdentityServiceApplicationTests.java**
```java
package health.zaed.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class IdentityServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies application starts without errors
    }
}
```

### 3. Unit Tests - JwtService

**JwtServiceTest.java**
```java
package health.zaed.identity.unit.service;

import health.zaed.identity.config.JwtConfig;
import health.zaed.identity.service.JwtService;
import health.zaed.identity.model.enums.Permission;
import health.zaed.identity.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-key-that-is-at-least-256-bits-long-for-hs256");
        jwtConfig.setIssuer("test-issuer");
        jwtConfig.setAccessTokenExpiry(3600);
        jwtConfig.setRefreshTokenExpiry(604800);
        jwtConfig.setTempTokenExpiry(900);

        jwtService = new JwtService(jwtConfig);
    }

    @Test
    @DisplayName("Should generate valid access token")
    void shouldGenerateValidAccessToken() {
        // Given
        UUID userId = UUID.randomUUID();
        UserRole role = UserRole.PARTNER_PHARMACY;
        Set<Permission> permissions = Permission.getPermissionsForRole(role);

        // When
        String token = jwtService.generateAccessToken(
            userId.toString(), role, permissions, null
        );

        // Then
        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractSubject(token)).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Given - set expiry to 0
        jwtConfig.setAccessTokenExpiry(0);
        JwtService expiredService = new JwtService(jwtConfig);

        UUID userId = UUID.randomUUID();
        String token = expiredService.generateAccessToken(
            userId.toString(), UserRole.ADMIN, Set.of(), null
        );

        // When/Then
        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should extract claims from token")
    void shouldExtractClaimsFromToken() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UserRole role = UserRole.PARTNER_NGO;

        String token = jwtService.generateAccessToken(
            userId.toString(), role, Set.of(Permission.DONATION_VIEW), partnerId.toString()
        );

        // When
        var claims = jwtService.extractClaims(token);

        // Then
        assertThat(claims.get("role")).isEqualTo(role.name());
        assertThat(claims.get("partnerId")).isEqualTo(partnerId.toString());
    }
}
```

### 4. Unit Tests - AuthService

**AuthServiceTest.java**
```java
package health.zaed.identity.unit.service;

import health.zaed.identity.service.AuthService;
import health.zaed.identity.service.JwtService;
import health.zaed.identity.repository.UserRepository;
import health.zaed.identity.repository.RefreshTokenRepository;
import health.zaed.identity.model.dto.LoginRequest;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.entity.UserCredential;
import health.zaed.identity.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void shouldRejectInvalidCredentials() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestAgent"))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("Should lock account after 5 failed attempts")
    void shouldLockAccountAfterFailedAttempts() {
        // Given
        User user = createTestUser();
        user.getCredential().setFailedAttempts(5);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("test@example.com", "password");

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestAgent"))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("locked");
    }

    private User createTestUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");

        UserCredential credential = new UserCredential();
        credential.setPasswordHash("$2a$12$hashedpassword");
        credential.setFailedAttempts(0);
        user.setCredential(credential);

        return user;
    }
}
```

### 5. Integration Tests - AuthController

**AuthControllerIT.java**
```java
package health.zaed.identity.integration.controller;

import health.zaed.identity.TestcontainersConfig;
import health.zaed.identity.model.dto.LoginRequest;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.entity.UserCredential;
import health.zaed.identity.model.enums.UserRole;
import health.zaed.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        createTestUser("partner@example.com", "password123", UserRole.PARTNER_PHARMACY);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - success")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("partner@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.requires2FA").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - invalid credentials")
    void loginInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("partner@example.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - success")
    void refreshTokenSuccess() throws Exception {
        // First login to get tokens
        LoginRequest loginRequest = new LoginRequest("partner@example.com", "password123");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();

        // Then refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists());
    }

    private void createTestUser(String email, String password, UserRole role) {
        User user = User.builder()
            .email(email)
            .role(role)
            .build();

        UserCredential credential = new UserCredential();
        credential.setPasswordHash(passwordEncoder.encode(password));
        user.setCredential(credential);

        userRepository.save(user);
    }
}
```

### 6. E2E Tests - Full Login Flow

**LoginFlowE2ETest.java**
```java
package health.zaed.identity.e2e;

import health.zaed.identity.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class LoginFlowE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Full partner login flow: login -> access resource -> refresh -> logout")
    void fullPartnerLoginFlow() {
        // 1. Login
        String loginUrl = "http://localhost:" + port + "/api/v1/auth/login";
        // ... complete test implementation
    }
}
```

---

## Technical Tasks

### Task 1: Create Test Infrastructure
- [ ] Create `TestcontainersConfig.java`
- [ ] Create `IdentityServiceApplicationTests.java`
- [ ] Verify containers start correctly

### Task 2: Unit Tests - Services
- [ ] `JwtServiceTest.java` - Token generation, validation, extraction
- [ ] `AuthServiceTest.java` - Login logic, lockout, token refresh
- [ ] `OtpServiceTest.java` - OTP generation, verification, rate limiting
- [ ] `TwoFactorServiceTest.java` - TOTP setup, verification, recovery codes

### Task 3: Unit Tests - Security
- [ ] `JwtAuthenticationFilterTest.java` - Filter chain logic
- [ ] `AuthPrincipalTest.java` - Record methods

### Task 4: Unit Tests - Models
- [ ] `PermissionTest.java` - Role-permission mapping

### Task 5: Integration Tests - Controllers
- [ ] `AuthControllerIT.java` - Login, refresh, logout endpoints
- [ ] `OtpControllerIT.java` - OTP send/verify endpoints
- [ ] `TwoFactorControllerIT.java` - 2FA setup/verify endpoints

### Task 6: Integration Tests - Repositories
- [ ] `UserRepositoryIT.java` - User CRUD operations
- [ ] `RefreshTokenRepositoryIT.java` - Token CRUD operations

### Task 7: E2E Tests
- [ ] `LoginFlowE2ETest.java` - Full login scenarios
- [ ] `OtpFlowE2ETest.java` - Full OTP scenarios
- [ ] `TwoFactorFlowE2ETest.java` - Full 2FA scenarios

---

## Coverage Targets

| Package | Target Coverage |
|---------|----------------|
| `service` | 80% |
| `controller` | 70% |
| `security` | 80% |
| `repository` | 60% |
| **Overall** | 75% |

---

## Running Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=JwtServiceTest

# Run integration tests only
mvn test -Dtest="*IT"

# Run with verbose output
mvn test -X
```

---

## CI/CD Integration

**GitHub Actions snippet**
```yaml
- name: Run tests
  run: mvn test

- name: Upload coverage report
  uses: codecov/codecov-action@v3
  with:
    files: target/site/jacoco/jacoco.xml
```
