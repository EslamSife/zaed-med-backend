# ADR-009: Identity Service Architecture

## Status
Accepted

## Date
2026-02-05

## Context

During the initial architecture design, there was confusion between "Auth Service" and "User Service" naming and responsibilities:

- **Auth Service** (`services/auth-service/`) contains all authentication and user identity code
- **User Service** (`services/userservice/`) was planned but remains empty
- Documentation inconsistently referenced both services
- ARCHITECTURE.md said "User Service" handles "Auth, profiles, OTP"
- Actual implementation has Auth Service doing all identity work

This naming confusion needed resolution before further development.

### Options Considered

**Option A: Separate Auth + User Services**
- Auth Service: Only authentication (OTP, JWT)
- User Service: User profiles, preferences
- **Rejected**: Over-engineering for MVP, unclear boundaries

**Option B: Auth Service owns everything**
- Keep "Auth Service" name
- Auth owns both authentication AND user identity
- **Rejected**: Name doesn't reflect full responsibility

**Option C: Identity Service (consolidated)** ✓
- Rename "Auth Service" → "Identity Service"
- Identity owns core user data + authentication
- Profile Service (future) for extended preferences
- **Selected**: Clear naming, proper microservices boundaries

## Decision

We will consolidate to a single **Identity Service** that owns:

1. **Core User Identity**
   - User records (id, phone, email, name, role)
   - Phone number as primary identifier (Egypt market)

2. **Authentication**
   - Phone OTP generation and verification
   - Email/password login for partners and admins
   - TOTP 2FA for admin accounts

3. **Authorization Token Issuance**
   - JWT token generation with RS256 signing (PRIVATE KEY)
   - Refresh token management
   - Token revocation

4. **Credential Management**
   - Password hashing (BCrypt)
   - Password reset flows
   - 2FA secret storage

### What Identity Service Does NOT Own

| Data | Owning Service | Reason |
|------|----------------|--------|
| Partner business data | Partner Service | Different bounded context |
| Donation records | Donation Service | Core domain entity |
| Request records | Request Service | Core domain entity |
| Extended profiles | Profile Service (future) | Separation of concerns |

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    IDENTITY SERVICE ARCHITECTURE                             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                      IDENTITY SERVICE                                   │ │
│  │                      Port: 8081                                         │ │
│  │                      Package: health.zaed.identity                      │ │
│  │                                                                         │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │ │
│  │  │    User     │  │     OTP     │  │     JWT     │  │     2FA     │   │ │
│  │  │   Service   │  │   Service   │  │   Service   │  │   Service   │   │ │
│  │  │             │  │             │  │             │  │             │   │ │
│  │  │ • Create    │  │ • Generate  │  │ • Issue     │  │ • Setup     │   │ │
│  │  │ • Lookup    │  │ • Verify    │  │ • Validate  │  │ • Verify    │   │ │
│  │  │ • Update    │  │ • Expire    │  │ • Refresh   │  │ • Backup    │   │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │ │
│  │                                                                         │ │
│  │  Data Owned:                                                            │ │
│  │  • users table (core identity)                                          │ │
│  │  • user_credentials table                                               │ │
│  │  • user_2fa table                                                       │ │
│  │  • refresh_tokens table                                                 │ │
│  │                                                                         │ │
│  │  Keys:                                                                  │ │
│  │  • RSA PRIVATE KEY (for signing JWTs)                                   │ │
│  │  • RSA PUBLIC KEY (shared with other services)                          │ │
│  │                                                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Integration Pattern

Other services validate JWT tokens using the PUBLIC KEY only:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE INTEGRATION                                       │
│                                                                              │
│   ┌──────────────┐      ┌──────────────┐      ┌──────────────┐             │
│   │   IDENTITY   │      │   DONATION   │      │   REQUEST    │             │
│   │   SERVICE    │      │   SERVICE    │      │   SERVICE    │             │
│   │              │      │              │      │              │             │
│   │  PRIVATE KEY │      │  PUBLIC KEY  │      │  PUBLIC KEY  │             │
│   │  (sign)      │      │  (verify)    │      │  (verify)    │             │
│   └──────────────┘      └──────────────┘      └──────────────┘             │
│          │                     │                     │                      │
│          │              ┌──────┴──────┐              │                      │
│          │              │             │              │                      │
│          └──────────────┤   zaed-     ├──────────────┘                      │
│                         │   common-   │                                      │
│                         │   security  │                                      │
│                         │  (library)  │                                      │
│                         └─────────────┘                                      │
│                                                                              │
│   Benefits:                                                                  │
│   • No runtime dependency on Identity Service for token validation          │
│   • Public key can be cached/embedded in services                           │
│   • Only Identity Service can forge tokens                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Package Convention

All packages follow the `health.zaed.*` convention:

| Service | Package |
|---------|---------|
| Identity Service | `health.zaed.identity` |
| Donation Service | `health.zaed.donationservice` |
| Request Service | `health.zaed.requestservice` |
| Partner Service | `health.zaed.partnerservice` |
| Matching Engine | `health.zaed.matching` |
| Notification Service | `health.zaed.notification` |
| Common Security Library | `health.zaed.common.security` |

## Consequences

### Positive

1. **Clear Naming**: "Identity Service" accurately describes its responsibility
2. **Single Source of Truth**: One service owns user identity data
3. **Security Boundaries**: Only Identity Service has signing keys
4. **Decoupled Validation**: Other services validate tokens independently
5. **Future-Ready**: Profile Service can be added later without refactoring

### Negative

1. **Rename Required**: Must rename existing `auth-service` folder and packages
2. **Documentation Updates**: All ADRs and docs need updating
3. **Migration Effort**: Package refactoring across codebase

### Risks

| Risk | Mitigation |
|------|------------|
| Breaking existing code | Automated refactoring tools in IDE |
| Configuration drift | Update all config files in same PR |
| Documentation out of sync | Update all docs before code changes |

## Implementation Notes

### Folder Structure Change

```
services/
├── auth-service/          # DELETE or rename to:
├── identity-service/      # NEW name
│   └── src/main/java/
│       └── health/
│           └── zaed/
│               └── identity/
│                   ├── config/
│                   ├── controller/
│                   ├── service/
│                   ├── model/
│                   └── repository/
├── userservice/           # DELETE (empty boilerplate)
└── ...
```

### Shared Security Library

Create `common/zaed-common-security/` with:
- `JwtValidator` - Public key validation
- `JwtClaims` - Standard claims model
- `SecurityUtils` - Common security utilities

### Migration Steps

1. Update documentation (this ADR, ARCHITECTURE.md, AUTH.md → IDENTITY.md)
2. Rename `services/auth-service/` → `services/identity-service/`
3. Refactor packages `org.zaed.auth` → `health.zaed.identity`
4. Delete empty `services/userservice/`
5. Create `common/zaed-common-security/`
6. Update all service dependencies

## References

- [ADR-003: Spring Security over Keycloak](003-spring-security-over-keycloak.md)
- [ADR-008: JWT RS256 Migration](008-jwt-rs256-migration.md)
- [Microservices Identity Patterns](https://microservices.io/patterns/security/access-token.html)
