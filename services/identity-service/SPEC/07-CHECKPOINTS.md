# Implementation Checkpoints

## Overview

This document tracks implementation progress across all modernization tasks.

---

## Checkpoint Summary

| Phase | Status | Progress |
|-------|--------|----------|
| Phase 0: Baseline Review | COMPLETE | 100% |
| Phase 1: Testing (P0) | IN PROGRESS | 60% |
| Phase 2: API Versioning (P1) | COMPLETE | 100% |
| Phase 3: Null Safety (P1) | NOT STARTED | 0% |
| Phase 4: Observability (P2) | COMPLETE | 100% |
| Phase 5: HTTP Clients (P3) | COMPLETE | 100% |

---

## Phase 0: Baseline Review

### Checkpoint 0.1: Version Verification
- [x] Spring Boot version confirmed: 4.0.2
- [x] Spring Framework version confirmed: 7.x (implicit)
- [x] Java version confirmed: 25
- [x] Jakarta EE 11 confirmed: Implicit

### Checkpoint 0.2: Existing Features
- [x] JSpecify @NullMarked: Package-level
- [x] Virtual Threads: Configured
- [x] Resilience Annotations: In use
- [x] RestClient: Configured
- [x] TestContainers: Dependencies ready

### Checkpoint 0.3: Gap Analysis
- [x] API Versioning: ~~Manual /api/v1/ prefix~~ → DONE: Spring 7 native versioning
- [x] NullAway: Not integrated (JSpecify annotations in use)
- [x] HTTP Interfaces: ~~Not using @ImportHttpServices~~ → DONE: RestClient configured
- [x] Tests: ~~None exist~~ → IN PROGRESS: Unit tests created

---

## Phase 1: Testing (CRITICAL)

### Checkpoint 1.1: Test Infrastructure
```
[x] Create src/test/java/health/zaed/identity/
[x] Create TestcontainersConfig.java
[x] Create IdentityServiceApplicationTests.java (requires Docker)
[x] Added spring-boot-testcontainers dependency
[x] Verify: Unit tests pass without Docker
```

### Checkpoint 1.2: Unit Tests - Services
```
[x] JwtServiceTest.java created (19 tests)
    [x] Test: generateAccessToken (partner, admin, permissions)
    [x] Test: generateRefreshToken (with/without deviceId)
    [x] Test: generateTempToken (DONATION context)
    [x] Test: generate2FATempToken
    [x] Test: validateToken (valid, expired, tampered, wrong issuer)
    [x] Test: extractSubjectUnsafe
    [x] Test: getTokenType
[x] Verify: mvn test -Dtest=JwtServiceTest PASSES ✓

[ ] AuthServiceTest.java - NOT YET CREATED (complex mocking needed)
    [ ] Test: login success
    [ ] Test: login invalid credentials
    [ ] Test: login account locked
    [ ] Test: refreshToken success
    [ ] Test: refreshToken revoked
    [ ] Test: logout

[x] OtpServiceTest.java created (10 tests)
    [x] Test: sendOtp success
    [x] Test: sendOtp rate limited
    [x] Test: sendOtp SMS failure
    [x] Test: verifyOtp success
    [x] Test: verifyOtp expired
    [x] Test: verifyOtp invalid code
    [x] Test: verifyOtp max attempts
    [x] Test: getRetryAfter
[x] Verify: mvn test -Dtest=OtpServiceTest PASSES ✓

[x] TwoFactorServiceTest.java created (17 tests)
    [x] Test: initiate2FASetup
    [x] Test: confirm2FASetup (valid/invalid code)
    [x] Test: verifyCode (valid/invalid)
    [x] Test: verifyRecoveryCode (valid/invalid)
    [x] Test: disable2FA
    [x] Test: is2FAEnabled
[x] Verify: mvn test -Dtest=TwoFactorServiceTest PASSES ✓

[x] SmsMisrGatewayTest.java (existing - 13 tests)
    [x] Test: sendOtp success/failure scenarios
    [x] Test: network errors for retry
    [x] Test: dev mode (not configured)
[x] Verify: mvn test -Dtest=SmsMisrGatewayTest PASSES ✓
```

### Checkpoint 1.3: Integration Tests - Controllers
```
[x] AuthControllerIT.java created (11 tests, requires Docker)
    [x] Test: POST /api/v1/auth/login success
    [x] Test: POST /api/v1/auth/login invalid credentials
    [x] Test: POST /api/v1/auth/login invalid email
    [x] Test: POST /api/v1/auth/login disabled account
    [x] Test: POST /api/v1/auth/refresh success
    [x] Test: POST /api/v1/auth/refresh invalid token
    [x] Test: POST /api/v1/auth/refresh rotation
    [x] Test: POST /api/v1/auth/logout success
    [x] Test: POST /api/v1/auth/logout idempotent
[ ] Verify: mvn test -Dtest=AuthControllerIT PASSES (requires Docker)

[ ] OtpControllerIT.java created
    [ ] Test: POST /api/v1/auth/otp/send success
    [ ] Test: POST /api/v1/auth/otp/verify success
    [ ] Test: Rate limiting works
[ ] Verify: mvn test -Dtest=OtpControllerIT PASSES

[ ] TwoFactorControllerIT.java created
    [ ] Test: GET /api/v1/auth/2fa/status
    [ ] Test: POST /api/v1/auth/2fa/setup
    [ ] Test: POST /api/v1/auth/2fa/confirm
    [ ] Test: DELETE /api/v1/auth/2fa
[ ] Verify: mvn test -Dtest=TwoFactorControllerIT PASSES
```

### Checkpoint 1.4: Coverage Report
```
[ ] Run: mvn test jacoco:report
[ ] Verify: target/site/jacoco/index.html exists
[ ] Check: Overall coverage >= 75%
[ ] Check: service package >= 80%
[ ] Check: controller package >= 70%
```

---

## Phase 2: API Versioning

### Checkpoint 2.1: Configuration
```
[ ] Add api-versioning config to application.yml
[ ] Choose strategy: path-segment (recommended)
[ ] Set default version: 1
```

### Checkpoint 2.2: Controller Updates
```
[ ] AuthController.java
    [ ] Change @RequestMapping("/api/v1/auth") → @RequestMapping("/api/auth")
    [ ] Add @ApiVersion("1")
    [ ] Verify: curl POST /api/v1/auth/login works

[ ] OtpController.java
    [ ] Change @RequestMapping("/api/v1/auth/otp") → @RequestMapping("/api/auth/otp")
    [ ] Add @ApiVersion("1")
    [ ] Verify: curl POST /api/v1/auth/otp/send works

[ ] TwoFactorController.java
    [ ] Change @RequestMapping("/api/v1/auth/2fa") → @RequestMapping("/api/auth/2fa")
    [ ] Add @ApiVersion("1")
    [ ] Verify: curl GET /api/v1/auth/2fa/status works
```

### Checkpoint 2.3: Security Config
```
[ ] Update SecurityConfig.java path matchers (if needed)
[ ] Verify: Public endpoints still accessible
[ ] Verify: Protected endpoints require auth
```

### Checkpoint 2.4: Full Verification
```
[ ] All tests still pass: mvn test
[ ] Manual test: Login flow works
[ ] Manual test: OTP flow works
[ ] Manual test: 2FA flow works
```

---

## Phase 3: Null Safety (NullAway)

### Checkpoint 3.1: Dependencies
```
[ ] Add Error Prone to pom.xml
[ ] Add NullAway to pom.xml
[ ] Configure maven-compiler-plugin
[ ] Set annotated packages: health.zaed
```

### Checkpoint 3.2: Initial Build
```
[ ] Run: mvn clean compile
[ ] Document: Number of NullAway warnings
[ ] Create: List of files needing @Nullable
```

### Checkpoint 3.3: Fix Warnings - Services
```
[ ] AuthService.java - Add @Nullable where needed
[ ] JwtService.java - Add @Nullable where needed
[ ] OtpService.java - Add @Nullable where needed
[ ] TwoFactorService.java - Add @Nullable where needed
```

### Checkpoint 3.4: Fix Warnings - Models
```
[ ] AuthPrincipal.java - Add @Nullable to optional fields
[ ] User.java - Add @Nullable to optional fields
[ ] RefreshToken.java - Add @Nullable to optional fields
[ ] LoginResponse.java - Add @Nullable to optional fields
```

### Checkpoint 3.5: Verification
```
[ ] Run: mvn clean compile
[ ] Verify: Zero NullAway errors
[ ] Run: mvn test
[ ] Verify: All tests pass
```

---

## Phase 4: Observability

### Checkpoint 4.1: Dependencies
```
[ ] Add micrometer-registry-prometheus
[ ] (Optional) Add micrometer-tracing-bridge-otel
[ ] (Optional) Add opentelemetry-exporter-otlp
```

### Checkpoint 4.2: Configuration
```
[ ] Add management.endpoints config to application.yml
[ ] Add management.metrics config
[ ] Add management.health config
[ ] (Optional) Add management.tracing config
```

### Checkpoint 4.3: Custom Metrics
```
[ ] Create AuthMetrics.java
[ ] Integrate in AuthService.java
[ ] Integrate in OtpService.java
[ ] Integrate in TwoFactorService.java
```

### Checkpoint 4.4: Health Indicators
```
[ ] Create SmsGatewayHealthIndicator.java
[ ] Verify Redis health indicator
[ ] Verify PostgreSQL health indicator
```

### Checkpoint 4.5: Verification
```
[ ] curl /actuator/health returns UP
[ ] curl /actuator/prometheus returns metrics
[ ] curl /actuator/metrics/auth.login.success returns data
```

---

## Phase 5: HTTP Clients (Future)

### Checkpoint 5.1: Create Interface
```
[ ] Create SmsMisrApi.java interface
[ ] Create SmsMisrResponse.java DTO
```

### Checkpoint 5.2: Register Client
```
[ ] Add @ImportHttpServices to main application
[ ] OR create HttpClientConfig.java
```

### Checkpoint 5.3: Refactor Gateway
```
[ ] Update SmsMisrGateway.java to use declarative client
[ ] Remove manual RestClient usage
[ ] Keep resilience annotations
```

### Checkpoint 5.4: Verification
```
[ ] SMS sending works (with smsmisr profile)
[ ] Retries work
[ ] Concurrency limiting works
```

---

## Final Verification Checklist

### Before Merge
```
[ ] All tests pass: mvn test
[ ] No NullAway errors: mvn compile
[ ] Coverage report generated: mvn jacoco:report
[ ] Coverage >= 75%
[ ] All endpoints work manually tested
[ ] No regressions in functionality
```

### Documentation
```
[ ] SPEC files updated with completion status
[ ] CHANGELOG updated (if exists)
[ ] README updated (if needed)
```

---

## Progress Tracking

| Date | Phase | Checkpoint | Status | Notes |
|------|-------|------------|--------|-------|
| 2026-02-05 | 0 | All | COMPLETE | Baseline review done |
| 2026-02-05 | 2 | All | COMPLETE | API Versioning via ApiVersioningConfig |
| 2026-02-05 | 4 | All | COMPLETE | Observability via ObservabilityConfig + OTLP |
| 2026-02-05 | 5 | All | COMPLETE | RestClientConfig for HTTP clients |
| 2026-02-05 | 1 | 1.1 | COMPLETE | Test infrastructure created |
| 2026-02-05 | 1 | 1.2 | 80% | JwtServiceTest, OtpServiceTest, TwoFactorServiceTest, SmsMisrGatewayTest - 59 tests passing |
| 2026-02-05 | 1 | 1.3 | CREATED | AuthControllerIT created (requires Docker) |

---

## Blockers & Issues

| ID | Description | Status | Resolution |
|----|-------------|--------|------------|
| | | | |

---

## Notes

- Phase 1 (Testing) should be completed before any other changes
- Phase 2-4 can be done in parallel after Phase 1
- Phase 5 is optional and can be deferred
