# ADR-008: JWT RS256 Signing with Migration Plan

## Status
Accepted

## Date
2026-02-05

## Context

JWT tokens need to be cryptographically signed to prevent tampering. Two main approaches exist:

### Symmetric Signing (HS256)
- Uses a single **shared secret** for signing and verification
- Same key on all services
- Simpler setup

### Asymmetric Signing (RS256)
- Uses **private key** for signing, **public key** for verification
- Only auth service has private key
- Other services only need public key

### Current Situation

The initial AUTH.md documentation shows HS256 examples but recommends RS256 for production without a concrete migration plan.

## Decision

We will implement a **two-phase JWT signing strategy**:

### Phase 1: HS256 (MVP - Weeks 1-8)
- Symmetric signing for rapid development
- Single secret key shared across services
- Acceptable for initial development

### Phase 2: RS256 (Production - Week 9+)
- Asymmetric signing with RSA key pair
- Private key only on Identity Service
- Public key distributed to all other services
- Zero-downtime migration

### Architecture Comparison

```
┌─────────────────────────────────────────────────────────────────┐
│                    HS256 (Phase 1)                               │
│                                                                  │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ Identity │     │ Donation │     │ Matching │               │
│   │ Service  │     │ Service  │     │  Engine  │               │
│   │          │     │          │     │          │               │
│   │ SECRET   │     │ SECRET   │     │ SECRET   │               │
│   │ (sign)   │     │ (verify) │     │ (verify) │               │
│   └──────────┘     └──────────┘     └──────────┘               │
│                                                                  │
│   ⚠️ Risk: Secret leak compromises ALL services                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    RS256 (Phase 2)                               │
│                                                                  │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ Identity │     │ Donation │     │ Matching │               │
│   │ Service  │     │ Service  │     │  Engine  │               │
│   │          │     │          │     │          │               │
│   │ PRIVATE  │     │ PUBLIC   │     │ PUBLIC   │               │
│   │ KEY      │     │ KEY      │     │ KEY      │               │
│   │ (sign)   │     │ (verify) │     │ (verify) │               │
│   └──────────┘     └──────────┘     └──────────┘               │
│                                                                  │
│   ✅ Only Identity Service can create tokens                    │
│   ✅ Public key leak doesn't compromise signing                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Consequences

### Positive

1. **MVP Velocity**: HS256 allows faster initial development
2. **Production Security**: RS256 provides proper key separation
3. **Zero-Downtime Migration**: Plan allows seamless transition
4. **Microservices Ready**: RS256 is the correct pattern for distributed services
5. **Key Rotation**: Asymmetric keys are easier to rotate safely

### Negative

1. **Two-Phase Complexity**: Must implement migration logic
2. **Key Management**: Need secure storage for RSA private key
3. **Configuration Overhead**: Each service needs public key access
4. **Certificate Expiry**: Must monitor and rotate RSA keys

### Risks

| Risk | Mitigation |
|------|------------|
| HS256 secret leaked in MVP | Keep MVP duration short; don't deploy to production with HS256 |
| Migration causes token invalidation | Dual validation period supports both algorithms |
| Private key compromise | Store in secrets manager (AWS Secrets Manager, HashiCorp Vault) |
| Key rotation complexity | Implement JWKS endpoint for automatic key discovery |

## Implementation

### Phase 1: HS256 Configuration

```yaml
# application.yml (Phase 1 - MVP)
zaed:
  auth:
    jwt:
      algorithm: HS256
      secret: ${JWT_SECRET}  # 256-bit minimum
      access-token-expiry: 3600
      refresh-token-expiry: 604800
```

```java
// JwtService.java (Phase 1)
@Service
public class JwtService {

    @Value("${zaed.auth.jwt.secret}")
    private String secret;

    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(getHs256Key())
            .compact();
    }

    private SecretKey getHs256Key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
```

### Phase 2: RS256 Configuration

```yaml
# application.yml (Phase 2 - Production)
zaed:
  auth:
    jwt:
      algorithm: RS256
      private-key-path: ${JWT_PRIVATE_KEY_PATH}  # Auth service only
      public-key-path: ${JWT_PUBLIC_KEY_PATH}    # All services
      jwks-uri: https://auth.zaed.org/.well-known/jwks.json  # Optional
      access-token-expiry: 3600
      refresh-token-expiry: 604800
```

```java
// JwtService.java (Phase 2 - Identity Service)
@Service
@Profile("production")
public class JwtServiceRs256 implements JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtServiceRs256(JwtConfig config) {
        this.privateKey = loadPrivateKey(config.getPrivateKeyPath());
        this.publicKey = loadPublicKey(config.getPublicKeyPath());
    }

    @Override
    public String generateToken(User user) {
        return Jwts.builder()
            .header()
                .keyId(getCurrentKeyId())
                .and()
            .subject(user.getId().toString())
            .claim("type", "access")
            .claim("role", user.getRole().name())
            .claim("permissions", getPermissions(user.getRole()))
            .issuer("zaed.org")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    @Override
    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer("zaed.org")
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private RSAPrivateKey loadPrivateKey(String path) {
        // Load from file or secrets manager
        String keyPem = Files.readString(Path.of(path));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(extractKeyContent(keyPem))
            ));
    }
}
```

### Other Services (Verification Only)

```java
// JwtValidator.java (Donation Service, Matching Engine, etc.)
@Service
public class JwtValidator {

    private final RSAPublicKey publicKey;

    public JwtValidator(JwtConfig config) {
        this.publicKey = loadPublicKey(config.getPublicKeyPath());
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer("zaed.org")
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    // Cannot generate tokens - no private key!
}
```

### Migration Strategy (Zero Downtime)

```java
// JwtServiceMigration.java (Dual support during migration)
@Service
@Profile("migration")
public class JwtServiceMigration implements JwtService {

    private final SecretKey hs256Key;
    private final RSAPrivateKey rs256PrivateKey;
    private final RSAPublicKey rs256PublicKey;

    @Value("${zaed.auth.jwt.migration.sign-with}")
    private String signWith;  // "HS256" or "RS256"

    @Override
    public String generateToken(User user) {
        // New tokens always use target algorithm
        if ("RS256".equals(signWith)) {
            return generateRs256Token(user);
        }
        return generateHs256Token(user);
    }

    @Override
    public Claims validateToken(String token) {
        // Try RS256 first (new tokens)
        try {
            return validateRs256(token);
        } catch (SignatureException e) {
            // Fall back to HS256 (old tokens)
            return validateHs256(token);
        }
    }
}
```

### Migration Timeline

```
┌─────────────────────────────────────────────────────────────────┐
│                    JWT MIGRATION TIMELINE                        │
│                                                                  │
│  WEEK 1-8: Development with HS256                               │
│  ─────────────────────────────────                              │
│  • All services use shared secret                               │
│  • Fast iteration, simple debugging                             │
│                                                                  │
│  WEEK 9: Prepare RS256                                          │
│  ─────────────────────                                          │
│  • Generate RSA key pair (2048-bit minimum, 4096-bit preferred) │
│  • Store private key in secrets manager                         │
│  • Distribute public key to all services                        │
│  • Deploy JwtServiceMigration with dual support                 │
│                                                                  │
│  WEEK 10: Migration Period                                      │
│  ────────────────────────                                       │
│  • Set sign-with=RS256 (new tokens use RS256)                   │
│  • Validation accepts both HS256 and RS256                      │
│  • Monitor for validation errors                                │
│                                                                  │
│  WEEK 11: Complete Migration                                    │
│  ───────────────────────────                                    │
│  • All tokens should be RS256 (access tokens expired)           │
│  • Remove HS256 validation code                                 │
│  • Delete HS256 secret from all services                        │
│                                                                  │
│  ONGOING: Key Rotation                                          │
│  ─────────────────────                                          │
│  • Rotate RSA keys annually                                     │
│  • Use JWKS endpoint for seamless rotation                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### RSA Key Generation

```bash
# Generate 4096-bit RSA key pair
openssl genrsa -out private_key.pem 4096

# Extract public key
openssl rsa -in private_key.pem -pubout -out public_key.pem

# Convert to PKCS8 format (Java-friendly)
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in private_key.pem -out private_key_pkcs8.pem
```

### JWKS Endpoint (Optional but Recommended)

```java
// JwksController.java - Exposes public keys for automatic discovery
@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final JwkSet jwkSet;

    @GetMapping("/jwks.json")
    public Map<String, Object> getJwks() {
        return jwkSet.toJSONObject();
    }
}
```

**JWKS Response Example:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "zaed-2026-02",
      "use": "sig",
      "alg": "RS256",
      "n": "0vx7agoebGc...",
      "e": "AQAB"
    }
  ]
}
```

## Alternatives Considered

### Alternative 1: HS256 Forever

**Description**: Keep symmetric signing permanently.

**Pros**:
- Simpler configuration
- No key distribution needed
- Faster validation (symmetric is faster than asymmetric)

**Cons**:
- Secret must be shared with all services
- Secret rotation requires coordinated deployment
- Any service compromise exposes token signing capability

**Why Rejected**: Unacceptable security risk for production microservices.

### Alternative 2: RS256 from Day 1

**Description**: Start with asymmetric signing immediately.

**Pros**:
- No migration needed
- Production-ready from start

**Cons**:
- Slower initial development
- More configuration overhead for MVP
- Key management complexity before infrastructure is stable

**Why Rejected**: Premature optimization for MVP phase. HS256 is acceptable for development.

### Alternative 3: External Token Service (OAuth2 Server)

**Description**: Use dedicated OAuth2 server (Keycloak, Auth0) for token issuance.

**Pros**:
- Handles all token complexity
- Built-in key rotation
- Industry standard

**Cons**:
- Additional infrastructure
- Adds latency
- Overkill for Phase 1

**Why Rejected**: Planned for Phase 2 (see ADR-003). Not needed for MVP.

## Security Checklist

### HS256 (Phase 1)
- [ ] Secret is at least 256 bits (32 bytes)
- [ ] Secret is generated using cryptographic RNG
- [ ] Secret is stored in environment variable, not code
- [ ] Secret is different per environment (dev/staging/prod)

### RS256 (Phase 2)
- [ ] RSA key is at least 2048 bits (4096 preferred)
- [ ] Private key stored in secrets manager (not filesystem)
- [ ] Private key accessible only to Identity Service
- [ ] Public key available to all services (can be in config)
- [ ] Key ID (kid) included in token header
- [ ] JWKS endpoint available for key discovery
- [ ] Key rotation process documented

## References

- [RFC 7518 - JSON Web Algorithms](https://tools.ietf.org/html/rfc7518)
- [JWT Best Practices](https://auth0.com/blog/jwt-security-best-practices/)
- [JJWT Documentation](https://github.com/jwtk/jjwt)
- [JWKS Specification](https://tools.ietf.org/html/rfc7517)
