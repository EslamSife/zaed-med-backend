# API Versioning Migration

## Overview

**Feature**: Spring Framework 7 Native API Versioning
**Priority**: P1 - Important
**Effort**: Low (2-4 hours)
**Risk**: Low

---

## Current Implementation

### Manual Path Prefixes

```java
// AuthController.java:28
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController { }

// OtpController.java
@RestController
@RequestMapping("/api/v1/auth/otp")
public class OtpController { }

// TwoFactorController.java
@RestController
@RequestMapping("/api/v1/auth/2fa")
public class TwoFactorController { }
```

### Problems with Current Approach
1. Version hardcoded in every controller
2. No central configuration
3. Adding v2 requires duplicating controllers
4. No header/query param versioning option

---

## Target Implementation

### Option A: Path Segment Versioning (Recommended)

**application.yml**
```yaml
spring:
  mvc:
    api-versioning:
      enabled: true
      type: path-segment
      path-segment:
        prefix: v
        position: after-context-path
```

**Controllers**
```java
// AuthController.java
@RestController
@RequestMapping("/api/auth")  // Remove /v1/
@ApiVersion("1")              // Add annotation
public class AuthController { }

// OtpController.java
@RestController
@RequestMapping("/api/auth/otp")
@ApiVersion("1")
public class OtpController { }

// TwoFactorController.java
@RestController
@RequestMapping("/api/auth/2fa")
@ApiVersion("1")
public class TwoFactorController { }
```

**Resulting URLs** (same as before):
```
POST /api/v1/auth/login
POST /api/v1/auth/otp/send
POST /api/v1/auth/2fa/setup
```

---

### Option B: Header Versioning

**application.yml**
```yaml
spring:
  mvc:
    api-versioning:
      enabled: true
      type: header
      header:
        name: X-API-Version
        default-version: "1"
```

**Request Example**:
```http
POST /api/auth/login
X-API-Version: 1
Content-Type: application/json
```

---

### Option C: WebMvcConfigurer (More Control)

**ApiVersionConfig.java**
```java
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .usePathSegment()
            .pathSegmentPrefix("v")
            .defaultVersion("1")
            .supportedVersions("1", "2");  // Future-proof
    }
}
```

---

## Technical Tasks

### Task 1: Add API Version Configuration
- [ ] Create `ApiVersionConfig.java` or add to `application.yml`
- [ ] Choose versioning strategy (path-segment recommended)
- [ ] Set default version to "1"

### Task 2: Update AuthController
- [ ] Remove `/v1/` from `@RequestMapping`
- [ ] Add `@ApiVersion("1")` annotation
- [ ] Import `org.springframework.web.bind.annotation.ApiVersion`
- [ ] Verify endpoints work

### Task 3: Update OtpController
- [ ] Remove `/v1/` from `@RequestMapping`
- [ ] Add `@ApiVersion("1")` annotation
- [ ] Verify endpoints work

### Task 4: Update TwoFactorController
- [ ] Remove `/v1/` from `@RequestMapping`
- [ ] Add `@ApiVersion("1")` annotation
- [ ] Verify endpoints work

### Task 5: Update Security Configuration
- [ ] Update `SecurityConfig.java` path matchers
- [ ] Change `/api/v1/auth/**` patterns (if using path-segment, may stay same)
- [ ] Test public endpoints still accessible

### Task 6: Verify All Endpoints
- [ ] Test login endpoint
- [ ] Test OTP send/verify
- [ ] Test 2FA setup/verify
- [ ] Test refresh token
- [ ] Test logout

---

## Files to Modify

| File | Change |
|------|--------|
| `application.yml` | Add api-versioning config |
| `AuthController.java` | Add @ApiVersion, update path |
| `OtpController.java` | Add @ApiVersion, update path |
| `TwoFactorController.java` | Add @ApiVersion, update path |
| `SecurityConfig.java` | Review path matchers |

---

## Verification Checklist

```bash
# Test all endpoints still work
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test"}'

curl -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"phone":"+201234567890","context":"DONATION"}'

curl -X GET http://localhost:8080/api/v1/auth/2fa/status \
  -H "Authorization: Bearer <token>"
```

---

## Rollback Plan

If issues occur:
1. Revert controller annotations
2. Remove api-versioning config
3. Return to manual `/api/v1/` prefixes

---

## Future: Adding API v2

With native versioning, adding v2 is simple:

```java
// V1 controller (existing)
@RestController
@RequestMapping("/api/auth")
@ApiVersion("1")
public class AuthControllerV1 { }

// V2 controller (new)
@RestController
@RequestMapping("/api/auth")
@ApiVersion("2")
public class AuthControllerV2 {
    // New response format, enhanced features
}
```

Spring automatically routes:
- `/api/v1/auth/login` -> AuthControllerV1
- `/api/v2/auth/login` -> AuthControllerV2
