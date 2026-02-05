# ADR-003: Spring Security over Keycloak (Phase 1)

## Status
Accepted

## Date
2026-02-05

## Context

We need to implement authentication and authorization for Zaed Med Connect. The system has diverse auth requirements:

- **Phone OTP** for donors/requesters (primary flow)
- **Email/Password + JWT** for partners
- **Email/Password + 2FA** for admins

We evaluated three main approaches:

1. **Keycloak** (Full IAM solution)
2. **Spring Security + Custom Implementation** (Lightweight)
3. **Managed Services** (Auth0, Firebase Auth)

## Decision

We will implement authentication using **Spring Security 7 with custom implementation** for Phase 1 (MVP), with a planned migration path to **Keycloak** for Phase 2 (Scale).

### Phase 1: Spring Security (MVP)

```
┌─────────────────────────────────────────────────────────────────┐
│                    PHASE 1 ARCHITECTURE                          │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Spring Security 7 Filter Chain              │   │
│  │                                                          │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐        │   │
│  │  │    OTP     │  │    JWT     │  │    2FA     │        │   │
│  │  │  Service   │  │  Service   │  │  Service   │        │   │
│  │  │ (Twilio)   │  │ (jjwt)     │  │  (TOTP)    │        │   │
│  │  └────────────┘  └────────────┘  └────────────┘        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  Storage: PostgreSQL (users) + Redis (OTP, sessions)            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 2: Keycloak Migration (Scale)

```
┌─────────────────────────────────────────────────────────────────┐
│                    PHASE 2 ARCHITECTURE                          │
│                                                                  │
│                     ┌─────────────────┐                         │
│                     │    KEYCLOAK     │                         │
│                     │                 │                         │
│                     │  • Realm: zaed  │                         │
│                     │  • Social Login │                         │
│                     │  • Phone OTP SPI│                         │
│                     │  • Admin Console│                         │
│                     └────────┬────────┘                         │
│                              │                                   │
│     ┌────────────────────────┼────────────────────────┐        │
│     │                        │                        │        │
│     ▼                        ▼                        ▼        │
│  ┌──────┐              ┌──────────┐              ┌──────┐      │
│  │ Web  │              │  Mobile  │              │ API  │      │
│  │ App  │              │   Apps   │              │      │      │
│  └──────┘              └──────────┘              └──────┘      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Consequences

### Positive (Phase 1 - Spring Security)

1. **Faster Time-to-Market**: No Keycloak setup/configuration overhead
2. **Phone OTP Native**: Easier to implement custom OTP flow directly
3. **Fewer Moving Parts**: One less service to deploy and monitor
4. **Team Familiarity**: Spring Security is well-known
5. **Full Control**: Custom auth flows without fighting framework constraints
6. **Lighter Resources**: No separate Keycloak server needed

### Negative (Phase 1 - Spring Security)

1. **Must Build Admin UI**: No built-in user management interface
2. **Must Implement 2FA**: TOTP implementation from scratch
3. **More Code to Maintain**: Custom security code has security risk if done wrong
4. **No Social Login**: Would need to implement OAuth2 client manually
5. **No SSO**: Single Sign-On across apps requires custom implementation

### Migration Triggers (When to Move to Phase 2)

| Trigger | Threshold |
|---------|-----------|
| Mobile apps needed | iOS/Android apps sharing auth |
| Social login requested | Google, Facebook, Apple sign-in |
| Partner SSO requests | Enterprise partners want SAML/OIDC |
| User management complexity | >1000 users, complex role management |
| Multiple applications | Additional Zaed apps (admin portal, mobile) |

### Migration Path

```
PHASE 1 → PHASE 2 MIGRATION STEPS
─────────────────────────────────

1. Deploy Keycloak alongside existing Identity Service
2. Create "zaed" realm with identical roles/permissions
3. Implement custom Phone OTP SPI for Keycloak
4. Export users from PostgreSQL to Keycloak
5. Update Angular to use Keycloak JS adapter
6. Update Spring Boot to validate Keycloak tokens
7. Run both systems in parallel (feature flag)
8. Gradually migrate users (new signups → Keycloak)
9. Deprecate custom Identity Service

TOKEN COMPATIBILITY:
• JWT format remains standard
• Claims structure stays same
• Frontend token handling unchanged
```

## Alternatives Considered

### Alternative 1: Keycloak from Day 1

**Description**: Deploy Keycloak as the identity provider from MVP.

**Pros**:
- Enterprise-grade IAM out of the box
- Built-in admin console
- Social login ready
- 2FA built-in
- Self-hosted (data sovereignty)

**Cons**:
- Additional service to deploy/maintain
- Phone OTP requires custom SPI development
- Steeper learning curve
- Resource intensive (~1GB RAM minimum)
- Configuration complexity for custom flows

**Why Rejected**: Phone OTP is our primary flow, and implementing a Keycloak SPI for custom phone auth adds complexity. Better to start simple and migrate when we need Keycloak's advanced features.

### Alternative 2: Auth0 (Managed Service)

**Description**: Use Auth0's managed authentication service.

**Pros**:
- Zero maintenance
- Phone OTP built-in
- Social login built-in
- Great SDKs and documentation
- Generous free tier (7,000 MAU)

**Cons**:
- Data stored outside Egypt (compliance concern)
- Costs scale with users ($23/1000 MAU after free tier)
- Vendor lock-in
- Less control over auth flows
- Latency for Egypt-based users

**Why Rejected**: Data sovereignty concerns for Egyptian NGO/healthcare-adjacent platform. Also, costs become significant at scale.

### Alternative 3: Firebase Authentication

**Description**: Use Google Firebase Auth.

**Pros**:
- Phone auth built-in
- Free tier generous
- Good mobile SDK
- Google infrastructure reliability

**Cons**:
- Google ecosystem lock-in
- Data outside Egypt
- Less control over token claims
- Not great for complex role systems

**Why Rejected**: Similar concerns to Auth0 regarding data locality and vendor lock-in.

## Implementation Notes

### Dependencies (pom.xml)

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>

<!-- TOTP for 2FA -->
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
</dependency>

<!-- Twilio for SMS -->
<dependency>
    <groupId>com.twilio.sdk</groupId>
    <artifactId>twilio</artifactId>
    <version>10.1.0</version>
</dependency>
```

### Security Checklist

- [ ] BCrypt password hashing (cost factor 12)
- [ ] JWT signed with RS256 (see ADR-008)
- [ ] Refresh token rotation
- [ ] Rate limiting on auth endpoints
- [ ] Account lockout after failed attempts
- [ ] HTTPS enforced (HSTS)
- [ ] CORS whitelist configured
- [ ] Audit logging for auth events

## References

- [Spring Security 7 Documentation](https://docs.spring.io/spring-security/reference/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Auth0 Pricing](https://auth0.com/pricing)
- [JJWT Library](https://github.com/jwtk/jjwt)
