# Identity Service - Spring Boot 4 / Spring Framework 7 Modernization Spec

## Document Index

| File | Description |
|------|-------------|
| [00-OVERVIEW.md](./00-OVERVIEW.md) | This file - Executive summary and navigation |
| [01-CURRENT-STATE.md](./01-CURRENT-STATE.md) | Current implementation analysis |
| [02-API-VERSIONING.md](./02-API-VERSIONING.md) | Native API versioning migration |
| [03-NULL-SAFETY.md](./03-NULL-SAFETY.md) | JSpecify + NullAway integration |
| [04-HTTP-CLIENTS.md](./04-HTTP-CLIENTS.md) | @ImportHttpServices migration |
| [05-OBSERVABILITY.md](./05-OBSERVABILITY.md) | Micrometer 2 / OpenTelemetry setup |
| [06-TESTING.md](./06-TESTING.md) | Test suite implementation |
| [07-CHECKPOINTS.md](./07-CHECKPOINTS.md) | Implementation checkpoints |

---

## Executive Summary

**Service**: identity-service
**Current Version**: Spring Boot 4.0.2 / Spring Framework 7.x
**Java Version**: 25 (Latest LTS)
**Status**: Production-ready, with enhancement opportunities

### What's Already Modern

```
[x] Spring Boot 4.0.2
[x] Java 25 with Virtual Threads
[x] JSpecify @NullMarked (package-level)
[x] Resilience annotations (@Retryable, @ConcurrencyLimit)
[x] RestClient (Spring Boot 4 native)
[x] Jakarta EE 11 (implicit)
[x] Flyway Starter (Spring Boot 4 pattern)
[x] TestContainers dependencies ready
```

### Gaps Identified

```
[ ] Native API Versioning (using manual /api/v1/ prefixes)
[ ] NullAway build-time enforcement
[ ] @ImportHttpServices for declarative HTTP clients
[ ] Observability patterns (Micrometer 2)
[ ] Test suite (0 tests exist!)
```

---

## Priority Matrix

| Task | Impact | Effort | Priority |
|------|--------|--------|----------|
| Add Test Suite | High | High | P0 - Critical |
| Native API Versioning | Medium | Low | P1 - Important |
| NullAway Integration | Medium | Medium | P1 - Important |
| Observability Config | Medium | Medium | P2 - Nice to Have |
| @ImportHttpServices | Low | Low | P3 - Future |
| @ConfigurationPropertiesSource | Low | Very Low | P3 - Future |

---

## Technology References

| Technology | Version | Documentation |
|------------|---------|---------------|
| Spring Boot | 4.0.2 | https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now |
| Spring Framework | 7.0.x | https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability |
| JSpecify | 1.0.0 | https://jspecify.dev/ |
| NullAway | 0.10.x | https://github.com/uber/NullAway |
| Micrometer | 2.x | https://micrometer.io/ |

---

## Key Files Reference

```
services/identity-service/
├── pom.xml                              # Dependencies (Spring Boot 4.0.2)
├── src/main/java/health/zaed/identity/
│   ├── package-info.java               # @NullMarked declaration
│   ├── IdentityServiceApplication.java # Main application
│   ├── controller/
│   │   ├── AuthController.java         # /api/v1/auth endpoints
│   │   ├── OtpController.java          # /api/v1/auth/otp endpoints
│   │   └── TwoFactorController.java    # /api/v1/auth/2fa endpoints
│   ├── service/
│   │   ├── AuthService.java            # Authentication logic
│   │   ├── JwtService.java             # JWT handling
│   │   ├── OtpService.java             # OTP management
│   │   ├── TwoFactorService.java       # 2FA TOTP handling
│   │   ├── SmsGateway.java             # SMS interface
│   │   ├── TwilioSmsGateway.java       # Twilio implementation
│   │   └── SmsMisrGateway.java         # SMS Misr implementation
│   ├── config/
│   │   ├── SecurityConfig.java         # Spring Security 7
│   │   ├── JwtConfig.java              # JWT properties
│   │   ├── OtpConfig.java              # OTP properties
│   │   ├── TwoFactorConfig.java        # 2FA properties
│   │   ├── TwilioConfig.java           # Twilio properties
│   │   ├── SmsMisrConfig.java          # SMS Misr properties
│   │   ├── ResilienceConfig.java       # @EnableResilientMethods
│   │   ├── VirtualThreadConfig.java    # Virtual threads setup
│   │   └── RestClientConfig.java       # HTTP client bean
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   └── AuthPrincipal.java          # Security context
│   ├── exception/
│   │   └── GlobalExceptionHandler.java # Error handling
│   └── model/
│       ├── entity/                     # JPA entities
│       ├── dto/                        # Request/Response DTOs
│       └── enums/                      # UserRole, Permission, etc.
└── src/test/java/                      # EMPTY - needs tests!
```
