# Null Safety with JSpecify + NullAway

## Overview

**Feature**: Build-time null safety enforcement
**Priority**: P1 - Important
**Effort**: Medium (4-6 hours)
**Risk**: Low (non-breaking)

---

## Current State

### What's Already Done

**package-info.java** - Package-level null marking
```java
@NullMarked
package health.zaed.identity;

import org.jspecify.annotations.NullMarked;
```

**pom.xml** - JSpecify dependency exists
```xml
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
    <version>1.0.0</version>
</dependency>
```

### What's Missing

1. **NullAway** - Build-time enforcement not configured
2. **@Nullable annotations** - Optional parameters not marked
3. **Error Prone** - Static analysis tooling not integrated

---

## JSpecify Annotations Reference

| Annotation | Usage | Example |
|------------|-------|---------|
| `@NullMarked` | Package/class level - everything non-null by default | `package-info.java` |
| `@Nullable` | Mark parameter/return that CAN be null | `@Nullable String email` |
| `@NonNull` | Explicitly mark as non-null (rarely needed) | `@NonNull User user` |
| `@NullUnmarked` | Opt-out for legacy code | Avoid using |

---

## Target Implementation

### Step 1: Add NullAway to pom.xml

```xml
<properties>
    <errorprone.version>2.24.1</errorprone.version>
    <nullaway.version>0.10.26</nullaway.version>
</properties>

<dependencies>
    <!-- Error Prone Annotations -->
    <dependency>
        <groupId>com.google.errorprone</groupId>
        <artifactId>error_prone_annotations</artifactId>
        <version>${errorprone.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>-XDcompilePolicy=simple</arg>
                    <arg>-Xplugin:ErrorProne -XepOpt:NullAway:AnnotatedPackages=health.zaed</arg>
                </compilerArgs>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.google.errorprone</groupId>
                        <artifactId>error_prone_core</artifactId>
                        <version>${errorprone.version}</version>
                    </path>
                    <path>
                        <groupId>com.uber.nullaway</groupId>
                        <artifactId>nullaway</artifactId>
                        <version>${nullaway.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Step 2: Add @Nullable Where Needed

**JwtService.java** - Example nullable return
```java
// Before
public String extractSubjectUnsafe(String token) {
    // Returns null if token invalid
}

// After
import org.jspecify.annotations.Nullable;

public @Nullable String extractSubjectUnsafe(String token) {
    // Explicitly nullable return
}
```

**AuthService.java** - Example nullable parameter
```java
// Before
public void logout(String refreshToken, String ipAddress, String userAgent) {
    // userAgent can be null
}

// After
public void logout(String refreshToken, String ipAddress, @Nullable String userAgent) {
    // Explicitly nullable parameter
}
```

**AuthPrincipal.java** - Nullable fields
```java
// Before
public record AuthPrincipal(
    String subject,
    String tokenType,
    String role,
    String partnerId,     // Can be null!
    String context,       // Can be null!
    String referenceId,   // Can be null!
    String trackingCode   // Can be null!
) { }

// After
public record AuthPrincipal(
    String subject,
    String tokenType,
    String role,
    @Nullable String partnerId,
    @Nullable String context,
    @Nullable String referenceId,
    @Nullable String trackingCode
) { }
```

---

## Technical Tasks

### Task 1: Add NullAway to Build
- [ ] Add Error Prone dependency to pom.xml
- [ ] Add NullAway dependency to pom.xml
- [ ] Configure maven-compiler-plugin
- [ ] Set annotated packages to `health.zaed`

### Task 2: Run Initial Build
- [ ] Run `mvn compile`
- [ ] Document all NullAway warnings
- [ ] Create list of files needing @Nullable

### Task 3: Fix Service Layer
- [ ] `AuthService.java` - Mark nullable params/returns
- [ ] `JwtService.java` - Mark nullable params/returns
- [ ] `OtpService.java` - Mark nullable params/returns
- [ ] `TwoFactorService.java` - Mark nullable params/returns

### Task 4: Fix DTOs and Records
- [ ] `AuthPrincipal.java` - Mark nullable fields
- [ ] `LoginResponse.java` - Mark nullable fields
- [ ] `OtpVerifyResponse.java` - Mark nullable fields

### Task 5: Fix Configuration Classes
- [ ] Review all @ConfigurationProperties classes
- [ ] Add @Nullable to optional config fields

### Task 6: Verify Build
- [ ] Run `mvn clean compile`
- [ ] Ensure no NullAway errors
- [ ] Run existing tests (when they exist)

---

## Files Likely Needing @Nullable

| File | Nullable Candidates |
|------|---------------------|
| `AuthPrincipal.java` | partnerId, context, referenceId, trackingCode |
| `JwtService.java` | extractSubjectUnsafe return, extractClaims params |
| `AuthService.java` | userAgent param in several methods |
| `User.java` | Optional fields (email, phone) |
| `RefreshToken.java` | revokedAt, revokeReason |
| `LoginResponse.java` | tempToken (only for 2FA flow) |

---

## Verification

```bash
# Build with NullAway enabled
mvn clean compile

# Should see:
# [INFO] BUILD SUCCESS

# If errors, you'll see:
# [ERROR] NullAway: returning @Nullable expression from method with @NonNull return type
```

---

## Benefits After Implementation

1. **Compile-time safety** - NPE prevention before runtime
2. **Self-documenting** - Clear nullability contracts
3. **IDE support** - Warnings in IntelliJ/VS Code
4. **Spring Boot 4 alignment** - Framework uses JSpecify internally

---

## Gradual Adoption Strategy

If too many errors initially:

1. Start with **warning mode** (not errors):
```xml
<arg>-Xplugin:ErrorProne -XepOpt:NullAway:AnnotatedPackages=health.zaed -Xep:NullAway:WARN</arg>
```

2. Fix warnings incrementally
3. Switch to **error mode** when clean:
```xml
<arg>-Xplugin:ErrorProne -XepOpt:NullAway:AnnotatedPackages=health.zaed -Xep:NullAway:ERROR</arg>
```
