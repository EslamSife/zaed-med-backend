# Zaed Med Connect - Identity Service Guide

> **Version:** 1.0
> **Last Updated:** February 2026
> **Tech Stack:** Java 21 + Spring Boot 4 + Spring Security 6
> **Status:** Implementation Ready

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication Strategy](#2-authentication-strategy)
3. [User Roles & Permissions](#3-user-roles--permissions)
4. [Phase 1: Spring Security Implementation](#4-phase-1-spring-security-implementation)
5. [Authentication Flows](#5-authentication-flows)
6. [API Endpoints](#6-api-endpoints)
7. [JWT Token Specification](#7-jwt-token-specification)
8. [Database Schema](#8-database-schema)
9. [Security Configuration](#9-security-configuration)
10. [Security Best Practices](#10-security-best-practices)
11. [Phase 2: Keycloak Migration](#11-phase-2-keycloak-migration)
12. [Testing Guide](#12-testing-guide)

---

## 1. Overview

### 1.1 Authentication Requirements

Zaed has three distinct user groups with different authentication needs:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AUTHENTICATION OVERVIEW                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                      │   │
│  │   DONORS & REQUESTERS          PARTNERS              ADMINS         │   │
│  │   ──────────────────          ─────────              ──────         │   │
│  │                                                                      │   │
│  │   • Phone OTP only            • Email/Password       • Email/Pass   │   │
│  │   • No account needed         • Full account         • 2FA Required │   │
│  │   • Track via code            • Dashboard access     • Full access  │   │
│  │   • 15-min temp token         • 1-hour JWT           • 1-hour JWT   │   │
│  │                                                                      │   │
│  │   LOW FRICTION ◄────────────────────────────────► HIGH SECURITY    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Why Phone-First for Egypt?

| Factor | Reason |
|--------|--------|
| **WhatsApp Penetration** | 90%+ of Egyptians use WhatsApp daily |
| **Trust** | Phone verification feels more legitimate than email |
| **Low Friction** | No password to remember = more donations |
| **Notifications** | Can send updates directly via WhatsApp/SMS |
| **Accessibility** | Works for users with low tech literacy |

### 1.3 Technology Decision

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TECHNOLOGY STACK                                     │
│                                                                              │
│  PHASE 1 (MVP)                          PHASE 2 (Scale)                     │
│  ─────────────                          ───────────────                     │
│                                                                              │
│  • Java 21 (LTS)                        • Add Keycloak 24+                  │
│  • Spring Boot 4.x                      • Social login (Google, FB)         │
│  • Spring Security 6.x                  • Enterprise SSO                    │
│  • Twilio Verify (SMS/WhatsApp OTP)     • Advanced user management         │
│  • java-otp (TOTP for 2FA)                                                  │
│  • Redis (OTP storage, sessions)                                            │
│  • PostgreSQL (users, credentials)                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Authentication Strategy

### 2.1 Strategy Matrix

| User Type | Auth Method | Token Type | Expiry | Stored Where |
|-----------|-------------|------------|--------|--------------|
| Donor/Requester | Phone OTP | Temp JWT | 15 min | Memory only |
| Partner | Email + Password | Access + Refresh | 1h / 7d | DB (refresh) |
| Admin | Email + Password + 2FA | Access + Refresh | 1h / 7d | DB (refresh) |

### 2.2 What Each User Can Access

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ACCESS MATRIX                                        │
│                                                                              │
│                          │ Public │ Track │ Partner │ Admin │              │
│  ────────────────────────┼────────┼───────┼─────────┼───────┤              │
│  Home page               │   ✓    │   ✓   │    ✓    │   ✓   │              │
│  Donate form             │   ✓    │   ✓   │    ✓    │   ✓   │              │
│  Request form            │   ✓    │   ✓   │    ✓    │   ✓   │              │
│  Track page (own code)   │   ✗    │   ✓   │    ✓    │   ✓   │              │
│  Upload donation image   │   ✗    │   ✓   │    ✗    │   ✓   │              │
│  Partner dashboard       │   ✗    │   ✗   │    ✓    │   ✓   │              │
│  Confirm pickup/delivery │   ✗    │   ✗   │    ✓    │   ✓   │              │
│  Admin dashboard         │   ✗    │   ✗   │    ✗    │   ✓   │              │
│  Verify donations        │   ✗    │   ✗   │    ✗    │   ✓   │              │
│  Manage partners         │   ✗    │   ✗   │    ✗    │   ✓   │              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. User Roles & Permissions

### 3.1 Role Definitions

```java
public enum UserRole {
    // Temporary roles (OTP-verified, no account)
    DONOR,              // Can create donations, upload images
    REQUESTER,          // Can create requests

    // Partner roles (full account)
    PARTNER_PHARMACY,   // Pharmacy pickup points
    PARTNER_NGO,        // NGO/charity partners
    PARTNER_VOLUNTEER,  // Individual volunteers

    // Admin roles
    ADMIN               // Full platform access
}
```

### 3.2 Permission Definitions

```java
public enum Permission {
    // Donation permissions
    DONATION_CREATE,
    DONATION_VIEW_OWN,
    DONATION_UPLOAD_IMAGE,
    DONATION_VIEW_ALL,
    DONATION_VERIFY,
    DONATION_REJECT,

    // Request permissions
    REQUEST_CREATE,
    REQUEST_VIEW_OWN,
    REQUEST_VIEW_ALL,

    // Match permissions
    MATCH_VIEW_ASSIGNED,
    MATCH_VIEW_ALL,
    MATCH_UPDATE_STATUS,
    MATCH_CONFIRM_PICKUP,
    MATCH_CONFIRM_DELIVERY,

    // Partner permissions
    PARTNER_DASHBOARD_VIEW,
    PARTNER_MANAGE,
    PARTNER_VERIFY,

    // Admin permissions
    ADMIN_DASHBOARD_VIEW,
    REPORTS_VIEW,
    SETTINGS_MANAGE,
    USERS_MANAGE
}
```

### 3.3 Role-Permission Mapping

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ROLE-PERMISSION MATRIX                                    │
│                                                                              │
│  ┌─────────────────┬─────────────────────────────────────────────────────┐ │
│  │     ROLE        │                    PERMISSIONS                       │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ DONOR           │ • DONATION_CREATE                                   │ │
│  │ (temp token)    │ • DONATION_UPLOAD_IMAGE (own only)                  │ │
│  │                 │ • DONATION_VIEW_OWN                                 │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ REQUESTER       │ • REQUEST_CREATE                                    │ │
│  │ (temp token)    │ • REQUEST_VIEW_OWN                                  │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ PARTNER_*       │ • PARTNER_DASHBOARD_VIEW                            │ │
│  │ (all partner    │ • MATCH_VIEW_ASSIGNED                               │ │
│  │  types)         │ • MATCH_UPDATE_STATUS                               │ │
│  │                 │ • MATCH_CONFIRM_PICKUP                              │ │
│  │                 │ • MATCH_CONFIRM_DELIVERY                            │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ ADMIN           │ • ALL PERMISSIONS                                   │ │
│  │                 │ • DONATION_VERIFY, DONATION_REJECT                  │ │
│  │                 │ • PARTNER_MANAGE, PARTNER_VERIFY                    │ │
│  │                 │ • USERS_MANAGE, SETTINGS_MANAGE                     │ │
│  │                 │ • REPORTS_VIEW, ADMIN_DASHBOARD_VIEW                │ │
│  │                 │                                                      │ │
│  └─────────────────┴─────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Phase 1: Spring Security Implementation

### 4.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION ARCHITECTURE                               │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         ANGULAR FRONTEND                              │  │
│  │                                                                       │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │  │
│  │  │   Public    │  │  Partner    │  │   Admin     │                  │  │
│  │  │   Pages     │  │  Dashboard  │  │   Panel     │                  │  │
│  │  │             │  │             │  │             │                  │  │
│  │  │ • Home      │  │ • Login     │  │ • Login+2FA │                  │  │
│  │  │ • Donate    │  │ • Dashboard │  │ • Dashboard │                  │  │
│  │  │ • Request   │  │ • Pickups   │  │ • Verify    │                  │  │
│  │  │ • Track     │  │ • History   │  │ • Partners  │                  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                  │  │
│  │        │                │                │                           │  │
│  │        │ Phone+OTP      │ JWT            │ JWT+2FA                   │  │
│  │        ▼                ▼                ▼                           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         API GATEWAY                                   │  │
│  │                                                                       │  │
│  │  Route: /api/v1/public/**  → No auth required                        │  │
│  │  Route: /api/v1/auth/**    → No auth required                        │  │
│  │  Route: /api/v1/track/**   → Temp token OR tracking code             │  │
│  │  Route: /api/v1/partner/** → JWT with PARTNER_* role                 │  │
│  │  Route: /api/v1/admin/**   → JWT with ADMIN role                     │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                IDENTITY SERVICE (Spring Boot 4)                       │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐    │  │
│  │  │                    Spring Security 6                         │    │  │
│  │  │                                                              │    │  │
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐            │    │  │
│  │  │  │   OTP      │  │   JWT      │  │   2FA      │            │    │  │
│  │  │  │  Service   │  │  Service   │  │  Service   │            │    │  │
│  │  │  │            │  │            │  │  (TOTP)    │            │    │  │
│  │  │  │ • Generate │  │ • Issue    │  │            │            │    │  │
│  │  │  │ • Verify   │  │ • Validate │  │ • Setup    │            │    │  │
│  │  │  │ • Expire   │  │ • Refresh  │  │ • Verify   │            │    │  │
│  │  │  └────────────┘  └────────────┘  └────────────┘            │    │  │
│  │  │                                                              │    │  │
│  │  └─────────────────────────────────────────────────────────────┘    │  │
│  │                                                                       │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │  │
│  │  │ PostgreSQL  │  │   Redis     │  │   Twilio    │                  │  │
│  │  │             │  │             │  │             │                  │  │
│  │  │ • Users     │  │ • OTP codes │  │ • Send SMS  │                  │  │
│  │  │ • Creds     │  │ • Sessions  │  │ • WhatsApp  │                  │  │
│  │  │ • Tokens    │  │ • Rate limit│  │             │                  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                  │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Project Structure

```
src/main/java/health/zaed/identity/
├── config/
│   ├── SecurityConfig.java           # Main Spring Security config
│   ├── JwtConfig.java                # JWT configuration properties
│   └── CorsConfig.java               # CORS configuration
├── controller/
│   ├── AuthController.java           # Login, logout, refresh
│   ├── OtpController.java            # OTP send/verify
│   └── TwoFactorController.java      # 2FA setup/verify
├── service/
│   ├── AuthService.java              # Authentication logic
│   ├── OtpService.java               # OTP generation/verification
│   ├── JwtService.java               # JWT creation/validation
│   ├── TwoFactorService.java         # TOTP 2FA logic
│   └── UserService.java              # User management
├── security/
│   ├── JwtAuthenticationFilter.java  # JWT filter
│   ├── JwtAuthenticationEntryPoint.java
│   ├── CustomAccessDeniedHandler.java
│   └── RolePermissionEvaluator.java  # Permission checks
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   ├── UserCredential.java
│   │   ├── RefreshToken.java
│   │   └── UserTwoFactor.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── OtpRequest.java
│   │   ├── OtpVerifyRequest.java
│   │   └── TwoFactorSetupResponse.java
│   └── enums/
│       ├── UserRole.java
│       └── Permission.java
├── repository/
│   ├── UserRepository.java
│   ├── UserCredentialRepository.java
│   └── RefreshTokenRepository.java
├── exception/
│   ├── AuthException.java
│   ├── InvalidOtpException.java
│   ├── InvalidTokenException.java
│   └── TooManyAttemptsException.java
└── util/
    ├── PhoneUtils.java               # Phone number formatting
    └── TrackingCodeGenerator.java    # Generate DON-XXX codes
```

### 4.3 Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot 4 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>

    <!-- TOTP for 2FA -->
    <dependency>
        <groupId>dev.samstevens.totp</groupId>
        <artifactId>totp</artifactId>
        <version>1.7.1</version>
    </dependency>

    <!-- Twilio for SMS/WhatsApp -->
    <dependency>
        <groupId>com.twilio.sdk</groupId>
        <artifactId>twilio</artifactId>
        <version>10.1.0</version>
    </dependency>

    <!-- Password hashing -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-crypto</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 4.4 Application Properties

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zaed
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  data:
    redis:
      host: localhost
      port: 6379

  security:
    oauth2:
      resourceserver:
        jwt:
          secret: ${JWT_SECRET}

# Custom auth configuration
zaed:
  auth:
    jwt:
      secret: ${JWT_SECRET}
      access-token-expiry: 3600        # 1 hour in seconds
      refresh-token-expiry: 604800     # 7 days in seconds
      temp-token-expiry: 900           # 15 minutes for OTP-verified users
      issuer: zaed.org

    otp:
      length: 6
      expiry-seconds: 300              # 5 minutes
      max-attempts: 3
      rate-limit-per-hour: 3

    password:
      bcrypt-strength: 12
      min-length: 8
      require-uppercase: true
      require-number: true

    2fa:
      issuer: "Zaed"
      backup-codes-count: 10

  twilio:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    verify-service-sid: ${TWILIO_VERIFY_SID}
    whatsapp-from: ${TWILIO_WHATSAPP_FROM}
```

---

## 5. Authentication Flows

### 5.1 Phone OTP Flow (Donors/Requesters)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PHONE OTP AUTHENTICATION FLOW                            │
│                                                                              │
│  User                    Frontend                 Backend                    │
│   │                         │                        │                       │
│   │  1. Fill donation form  │                        │                       │
│   │     + enter phone       │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  2. POST /api/v1/donations                    │
│   │                         │  {phone, medicine, ...}│                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  3. Create donation   │
│   │                         │                        │     (PENDING_OTP)     │
│   │                         │                        │                       │
│   │                         │                        │  4. Generate OTP      │
│   │                         │                        │     Store in Redis    │
│   │                         │                        │     (TTL: 5 min)      │
│   │                         │                        │                       │
│   │                         │                        │  5. Send via Twilio   │
│   │  ◄─────────────────────────────────────────────────  SMS/WhatsApp       │
│   │     SMS: "Your code                              │                       │
│   │      is 123456"         │                        │                       │
│   │                         │                        │                       │
│   │                         │  6. {                  │                       │
│   │                         │      donationId: "...",│                       │
│   │                         │      requiresOtp: true,│                       │
│   │                         │      expiresIn: 300    │                       │
│   │                         │     }                  │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │  7. Enter OTP code      │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  8. POST /api/v1/auth/otp/verify              │
│   │                         │  {phone, otp, donationId}                     │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  9. Validate OTP      │
│   │                         │                        │     from Redis        │
│   │                         │                        │                       │
│   │                         │                        │ 10. Update donation   │
│   │                         │                        │     status → PENDING  │
│   │                         │                        │     _VERIFICATION     │
│   │                         │                        │                       │
│   │                         │                        │ 11. Generate temp JWT │
│   │                         │                        │     (15 min expiry)   │
│   │                         │                        │                       │
│   │                         │ 12. {                  │                       │
│   │                         │      verified: true,   │                       │
│   │                         │      tempToken: "...", │                       │
│   │                         │      trackingCode:     │                       │
│   │                         │        "DON-ABC123"    │                       │
│   │                         │     }                  │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │ 13. Redirect to         │                        │                       │
│   │     /track/DON-ABC123   │                        │                       │
│   │◄────────────────────────│                        │                       │
│   │                         │                        │                       │
│   │                         │  (Optional: Upload image with tempToken)      │
│   │                         │  POST /api/v1/donations/{id}/images           │
│   │                         │  Authorization: Bearer {tempToken}            │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Partner/Admin JWT Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PARTNER/ADMIN JWT AUTHENTICATION FLOW                     │
│                                                                              │
│  User                    Frontend                 Backend                    │
│   │                         │                        │                       │
│   │  1. Enter email/pass    │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  2. POST /api/v1/auth/login                   │
│   │                         │  {email, password}     │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  3. Validate password │
│   │                         │                        │     (bcrypt verify)   │
│   │                         │                        │                       │
│   │                         │                        │  4. Check if 2FA      │
│   │                         │                        │     enabled           │
│   │                         │                        │                       │
│   │                         │                        │                       │
│   │   ┌─────────────────────┴────────────────────────┴─────────────────────┐│
│   │   │                                                                     ││
│   │   │  PATH A: No 2FA (Partners)       PATH B: 2FA Required (Admins)    ││
│   │   │  ─────────────────────────       ─────────────────────────────     ││
│   │   │                                                                     ││
│   │   │  5a. Generate tokens             5b. Return 2FA challenge          ││
│   │   │      accessToken (1h)                                               ││
│   │   │      refreshToken (7d)           {                                  ││
│   │   │                                    requires2FA: true,               ││
│   │   │  {                                 tempToken: "...",                ││
│   │   │    accessToken: "...",             methods: ["TOTP"]                ││
│   │   │    refreshToken: "...",          }                                  ││
│   │   │    expiresIn: 3600,                                                 ││
│   │   │    user: {...}                                                      ││
│   │   │  }                                                                  ││
│   │   │                                                                     ││
│   │   └─────────────────────┬────────────────────────┬─────────────────────┘│
│   │                         │                        │                       │
│   │                         │                        │                       │
│   │  (If 2FA required)      │                        │                       │
│   │  6. Enter TOTP code     │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  7. POST /api/v1/auth/2fa/verify              │
│   │                         │  {tempToken, totpCode} │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  8. Validate TOTP     │
│   │                         │                        │     (30s window)      │
│   │                         │                        │                       │
│   │                         │                        │  9. Generate tokens   │
│   │                         │                        │                       │
│   │                         │ 10. {                  │                       │
│   │                         │      accessToken,      │                       │
│   │                         │      refreshToken,     │                       │
│   │                         │      expiresIn: 3600   │                       │
│   │                         │     }                  │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │ 11. Store tokens        │                        │                       │
│   │     (localStorage or    │                        │                       │
│   │      httpOnly cookie)   │                        │                       │
│   │                         │                        │                       │
│   │ 12. Redirect to         │                        │                       │
│   │     dashboard           │                        │                       │
│   │◄────────────────────────│                        │                       │
│   │                         │                        │                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Token Refresh Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TOKEN REFRESH FLOW                                   │
│                                                                              │
│  Frontend                                         Backend                    │
│     │                                                │                       │
│     │  1. API call fails with 401                    │                       │
│     │     (access token expired)                     │                       │
│     │◄───────────────────────────────────────────────│                       │
│     │                                                │                       │
│     │  2. POST /api/v1/auth/refresh                  │                       │
│     │     {refreshToken: "..."}                      │                       │
│     │────────────────────────────────────────────────►                       │
│     │                                                │                       │
│     │                                                │  3. Validate refresh  │
│     │                                                │     token in DB       │
│     │                                                │                       │
│     │                                                │  4. Check not revoked │
│     │                                                │                       │
│     │                                                │  5. Generate new      │
│     │                                                │     access token      │
│     │                                                │                       │
│     │                                                │  6. Rotate refresh    │
│     │                                                │     token (optional)  │
│     │                                                │                       │
│     │  7. {                                          │                       │
│     │       accessToken: "new...",                   │                       │
│     │       refreshToken: "new...",                  │                       │
│     │       expiresIn: 3600                          │                       │
│     │     }                                          │                       │
│     │◄───────────────────────────────────────────────│                       │
│     │                                                │                       │
│     │  8. Retry original API call                    │                       │
│     │     with new access token                      │                       │
│     │────────────────────────────────────────────────►                       │
│     │                                                │                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. API Endpoints

### 6.1 OTP Endpoints

```yaml
# ════════════════════════════════════════════════════════════════════════════
#                         OTP ENDPOINTS
# ════════════════════════════════════════════════════════════════════════════

POST /api/v1/auth/otp/send
  Description: Send OTP to phone number
  Auth: None
  Rate Limit: 3 per phone per hour

  Request:
    {
      "phone": "+201234567890",    // E.164 format required
      "channel": "SMS"             // "SMS" or "WHATSAPP"
    }

  Response 200:
    {
      "sent": true,
      "expiresIn": 300,            // seconds
      "retryAfter": 60             // seconds until can resend
    }

  Response 429 (Rate Limited):
    {
      "error": "TOO_MANY_REQUESTS",
      "message": "Maximum OTP requests exceeded. Try again in 45 minutes.",
      "retryAfter": 2700
    }

  Response 400 (Invalid Phone):
    {
      "error": "INVALID_PHONE",
      "message": "Phone number must be in E.164 format"
    }

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/otp/verify
  Description: Verify OTP and get temporary token
  Auth: None
  Rate Limit: 5 attempts per OTP

  Request:
    {
      "phone": "+201234567890",
      "otp": "123456",
      "context": "DONATION",       // "DONATION" or "REQUEST"
      "referenceId": "uuid"        // The donation/request ID
    }

  Response 200:
    {
      "verified": true,
      "tempToken": "eyJhbG...",    // JWT, 15 min expiry
      "trackingCode": "DON-ABC123",
      "trackingUrl": "https://zaed.org/track/DON-ABC123"
    }

  Response 400 (Invalid OTP):
    {
      "error": "INVALID_OTP",
      "message": "Invalid or expired OTP",
      "attemptsRemaining": 2
    }

  Response 400 (Expired OTP):
    {
      "error": "OTP_EXPIRED",
      "message": "OTP has expired. Please request a new one."
    }

  Response 429 (Too Many Attempts):
    {
      "error": "TOO_MANY_ATTEMPTS",
      "message": "Maximum attempts exceeded. Request a new OTP.",
      "retryAfter": 300
    }
```

### 6.2 Login Endpoints

```yaml
# ════════════════════════════════════════════════════════════════════════════
#                         LOGIN ENDPOINTS
# ════════════════════════════════════════════════════════════════════════════

POST /api/v1/auth/login
  Description: Login with email and password
  Auth: None
  Rate Limit: 5 per IP per minute, 10 per account per hour

  Request:
    {
      "email": "partner@example.com",
      "password": "********"
    }

  Response 200 (No 2FA):
    {
      "accessToken": "eyJhbG...",
      "refreshToken": "eyJhbG...",
      "expiresIn": 3600,
      "tokenType": "Bearer",
      "user": {
        "id": "uuid",
        "email": "partner@example.com",
        "name": "Cairo Pharmacy",
        "role": "PARTNER_PHARMACY",
        "partnerId": "uuid"
      }
    }

  Response 200 (2FA Required):
    {
      "requires2FA": true,
      "tempToken": "eyJhbG...",     // Short-lived token for 2FA
      "methods": ["TOTP"],
      "expiresIn": 300              // 5 min to complete 2FA
    }

  Response 401 (Invalid Credentials):
    {
      "error": "INVALID_CREDENTIALS",
      "message": "Invalid email or password"
    }

  Response 403 (Account Locked):
    {
      "error": "ACCOUNT_LOCKED",
      "message": "Account temporarily locked. Try again in 30 minutes.",
      "lockedUntil": "2026-02-05T14:30:00Z"
    }

  Response 403 (Account Disabled):
    {
      "error": "ACCOUNT_DISABLED",
      "message": "Account has been disabled. Contact support."
    }

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/refresh
  Description: Refresh access token
  Auth: None (uses refresh token)

  Request:
    {
      "refreshToken": "eyJhbG..."
    }

  Response 200:
    {
      "accessToken": "eyJhbG...",
      "refreshToken": "eyJhbG...",  // Rotated
      "expiresIn": 3600,
      "tokenType": "Bearer"
    }

  Response 401:
    {
      "error": "INVALID_TOKEN",
      "message": "Refresh token is invalid or expired"
    }

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/logout
  Description: Invalidate refresh token
  Auth: Bearer token

  Request:
    {
      "refreshToken": "eyJhbG..."   // Optional, logout from specific device
    }

  Response 204: No Content

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/logout-all
  Description: Invalidate all refresh tokens (logout from all devices)
  Auth: Bearer token

  Response 204: No Content
```

### 6.3 2FA Endpoints

```yaml
# ════════════════════════════════════════════════════════════════════════════
#                         2FA ENDPOINTS
# ════════════════════════════════════════════════════════════════════════════

POST /api/v1/auth/2fa/verify
  Description: Complete 2FA verification during login
  Auth: Temp token from login

  Request:
    {
      "tempToken": "eyJhbG...",
      "totpCode": "123456"
    }

  Response 200:
    {
      "accessToken": "eyJhbG...",
      "refreshToken": "eyJhbG...",
      "expiresIn": 3600,
      "tokenType": "Bearer"
    }

  Response 400:
    {
      "error": "INVALID_TOTP",
      "message": "Invalid verification code"
    }

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/2fa/setup
  Description: Initialize 2FA setup (get QR code)
  Auth: Bearer token

  Response 200:
    {
      "secret": "JBSWY3DPEHPK3PXP",        // Base32 encoded
      "qrCodeUri": "otpauth://totp/Zaed:admin@zaed.org?secret=...",
      "qrCodeImage": "data:image/png;base64,...",  // QR code as base64
      "backupCodes": [                      // Store these securely!
        "a1b2-c3d4",
        "e5f6-g7h8",
        // ... 10 codes
      ]
    }

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/2fa/enable
  Description: Enable 2FA after verifying setup
  Auth: Bearer token

  Request:
    {
      "totpCode": "123456"    // Verify user set up authenticator correctly
    }

  Response 200:
    {
      "enabled": true,
      "enabledAt": "2026-02-05T12:00:00Z"
    }

  Response 400:
    {
      "error": "INVALID_TOTP",
      "message": "Code doesn't match. Make sure your authenticator is set up correctly."
    }

# ─────────────────────────────────────────────────────────────────────────────

DELETE /api/v1/auth/2fa
  Description: Disable 2FA
  Auth: Bearer token

  Request:
    {
      "password": "********",    // Require password confirmation
      "totpCode": "123456"       // Or backup code
    }

  Response 204: No Content

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/2fa/backup/verify
  Description: Verify using backup code (one-time use)
  Auth: Temp token from login

  Request:
    {
      "tempToken": "eyJhbG...",
      "backupCode": "a1b2-c3d4"
    }

  Response 200:
    {
      "accessToken": "eyJhbG...",
      "refreshToken": "eyJhbG...",
      "remainingBackupCodes": 9    // Warn user if low
    }
```

### 6.4 Password Endpoints

```yaml
# ════════════════════════════════════════════════════════════════════════════
#                         PASSWORD ENDPOINTS
# ════════════════════════════════════════════════════════════════════════════

POST /api/v1/auth/password/forgot
  Description: Request password reset email
  Auth: None
  Rate Limit: 3 per email per hour

  Request:
    {
      "email": "partner@example.com"
    }

  Response 200:
    {
      "message": "If an account exists, a reset email has been sent."
    }
    // Always return 200 to prevent email enumeration

# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/password/reset
  Description: Reset password with token from email
  Auth: None

  Request:
    {
      "token": "reset-token-from-email",
      "newPassword": "NewSecurePassword123!"
    }

  Response 200:
    {
      "message": "Password reset successfully. Please login."
    }

  Response 400:
    {
      "error": "INVALID_TOKEN",
      "message": "Reset link is invalid or expired"
    }

  Response 400:
    {
      "error": "WEAK_PASSWORD",
      "message": "Password must be at least 8 characters with uppercase and number"
    }

# ─────────────────────────────────────────────────────────────────────────────

PUT /api/v1/auth/password
  Description: Change password (authenticated user)
  Auth: Bearer token

  Request:
    {
      "currentPassword": "OldPassword123!",
      "newPassword": "NewPassword456!"
    }

  Response 200:
    {
      "message": "Password changed successfully"
    }

  Response 400:
    {
      "error": "INCORRECT_PASSWORD",
      "message": "Current password is incorrect"
    }
```

---

## 7. JWT Token Specification

### 7.1 Token Types

| Token Type | Purpose | Expiry | Storage |
|------------|---------|--------|---------|
| Access Token | API authorization | 1 hour | Memory/localStorage |
| Refresh Token | Get new access token | 7 days | HttpOnly cookie or secure storage |
| Temp Token (OTP) | Limited actions after OTP | 15 min | Memory |
| Temp Token (2FA) | Complete 2FA | 5 min | Memory |

### 7.2 Token Payload Structures

#### Access Token (Partner/Admin)

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "type": "access",
  "email": "partner@example.com",
  "role": "PARTNER_PHARMACY",
  "partnerId": "660e8400-e29b-41d4-a716-446655440000",
  "permissions": [
    "PARTNER_DASHBOARD_VIEW",
    "MATCH_VIEW_ASSIGNED",
    "MATCH_UPDATE_STATUS",
    "MATCH_CONFIRM_PICKUP",
    "MATCH_CONFIRM_DELIVERY"
  ],
  "iss": "zaed.org",
  "iat": 1707134400,
  "exp": 1707138000
}
```

#### Access Token (Admin)

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "type": "access",
  "email": "admin@zaed.org",
  "role": "ADMIN",
  "permissions": [
    "DONATION_VERIFY",
    "DONATION_REJECT",
    "PARTNER_MANAGE",
    "PARTNER_VERIFY",
    "ADMIN_DASHBOARD_VIEW",
    "REPORTS_VIEW",
    "SETTINGS_MANAGE",
    "USERS_MANAGE"
  ],
  "iss": "zaed.org",
  "iat": 1707134400,
  "exp": 1707138000
}
```

#### Temp Token (OTP-verified Donor)

```json
{
  "sub": "phone:+201234567890",
  "type": "temp",
  "context": "DONATION",
  "referenceId": "770e8400-e29b-41d4-a716-446655440000",
  "trackingCode": "DON-ABC123",
  "permissions": [
    "DONATION_UPLOAD_IMAGE",
    "DONATION_VIEW_OWN"
  ],
  "iss": "zaed.org",
  "iat": 1707134400,
  "exp": 1707135300
}
```

#### Refresh Token

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "type": "refresh",
  "jti": "unique-token-id-for-revocation",
  "deviceId": "browser-fingerprint-hash",
  "iss": "zaed.org",
  "iat": 1707134400,
  "exp": 1707739200
}
```

### 7.3 JWT Service Implementation

```java
@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtConfig jwtConfig;

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("type", "access")
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .claim("permissions", getPermissions(user.getRole()))
            .claim("partnerId", user.getPartnerId())
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now()
                .plusSeconds(jwtConfig.getAccessTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public String generateRefreshToken(User user, String deviceId) {
        String jti = UUID.randomUUID().toString();

        // Store in database for revocation
        refreshTokenRepository.save(RefreshToken.builder()
            .id(jti)
            .userId(user.getId())
            .deviceId(deviceId)
            .expiresAt(Instant.now().plusSeconds(jwtConfig.getRefreshTokenExpiry()))
            .build());

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("type", "refresh")
            .id(jti)
            .claim("deviceId", deviceId)
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now()
                .plusSeconds(jwtConfig.getRefreshTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public String generateTempToken(String phone, String context,
                                     UUID referenceId, String trackingCode) {
        return Jwts.builder()
            .subject("phone:" + phone)
            .claim("type", "temp")
            .claim("context", context)
            .claim("referenceId", referenceId.toString())
            .claim("trackingCode", trackingCode)
            .claim("permissions", getTempPermissions(context))
            .issuer(jwtConfig.getIssuer())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now()
                .plusSeconds(jwtConfig.getTempTokenExpiry())))
            .signWith(getSigningKey())
            .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtConfig.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired");
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

---

## 8. Database Schema

### 8.1 ER Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AUTH DATABASE SCHEMA                                 │
│                                                                              │
│  ┌──────────────────┐                                                       │
│  │      users       │                                                       │
│  ├──────────────────┤                                                       │
│  │ id (PK)          │◄─────────────────────────────────────────────────────┐│
│  │ phone            │                                                      ││
│  │ email            │                                                      ││
│  │ name             │                                                      ││
│  │ role             │      ┌──────────────────┐                           ││
│  │ is_verified      │      │ user_credentials │                           ││
│  │ is_active        │      ├──────────────────┤                           ││
│  │ created_at       │◄─────│ user_id (PK,FK)  │                           ││
│  │ updated_at       │      │ password_hash    │                           ││
│  └──────────────────┘      │ failed_attempts  │                           ││
│           │                │ locked_until     │                           ││
│           │                │ password_changed │                           ││
│           │                └──────────────────┘                           ││
│           │                                                                ││
│           │                ┌──────────────────┐                           ││
│           │                │   user_2fa       │                           ││
│           │                ├──────────────────┤                           ││
│           └───────────────►│ user_id (PK,FK)  │                           ││
│                            │ totp_secret_enc  │                           ││
│                            │ is_enabled       │                           ││
│                            │ enabled_at       │                           ││
│                            │ backup_codes_hash│                           ││
│                            └──────────────────┘                           ││
│                                                                            ││
│  ┌──────────────────┐      ┌──────────────────┐                           ││
│  │  refresh_tokens  │      │   otp_codes      │                           ││
│  ├──────────────────┤      ├──────────────────┤                           ││
│  │ id (PK)          │      │ id (PK)          │                           ││
│  │ user_id (FK)     │──────│ phone            │                           ││
│  │ token_hash       │      │ otp_hash         │                           ││
│  │ device_info      │      │ context          │                           ││
│  │ ip_address       │      │ reference_id     │                           ││
│  │ expires_at       │      │ attempts         │                           ││
│  │ revoked_at       │      │ expires_at       │                           ││
│  │ created_at       │      │ verified_at      │                           ││
│  └──────────────────┘      │ created_at       │                           ││
│                            └──────────────────┘                           ││
│                                                                            ││
│  ┌──────────────────┐                                                      ││
│  │  auth_audit_log  │                                                      ││
│  ├──────────────────┤                                                      ││
│  │ id (PK)          │                                                      ││
│  │ event_type       │                                                      ││
│  │ user_id (FK)     │──────────────────────────────────────────────────────┘│
│  │ phone            │                                                       │
│  │ ip_address       │                                                       │
│  │ user_agent       │                                                       │
│  │ success          │                                                       │
│  │ failure_reason   │                                                       │
│  │ metadata         │                                                       │
│  │ created_at       │                                                       │
│  └──────────────────┘                                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 SQL Migration Scripts

```sql
-- ════════════════════════════════════════════════════════════════════════════
-- V1__create_auth_tables.sql
-- ════════════════════════════════════════════════════════════════════════════

-- Users table (if not exists from main schema)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) UNIQUE,
    email VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'DONOR',
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    preferred_city VARCHAR(100),
    preferred_area VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- ─────────────────────────────────────────────────────────────────────────────

-- User credentials (for partners and admins with passwords)
CREATE TABLE user_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    must_change_password BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────

-- 2FA settings
CREATE TABLE user_2fa (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    totp_secret_encrypted VARCHAR(255),          -- AES-256 encrypted
    is_enabled BOOLEAN DEFAULT FALSE,
    enabled_at TIMESTAMP WITH TIME ZONE,
    backup_codes_hash TEXT[],                    -- Bcrypt hashed backup codes
    backup_codes_used INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────

-- Refresh tokens (for session management and revocation)
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,                  -- UUID as string (jti claim)
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,            -- SHA-256 hash for lookup
    device_id VARCHAR(255),                      -- Browser fingerprint
    device_info VARCHAR(500),                    -- User agent string
    ip_address INET,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(50),                   -- 'LOGOUT', 'PASSWORD_CHANGE', 'SUSPICIOUS'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at)
    WHERE revoked_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

-- OTP codes (primarily in Redis, this is fallback/audit)
CREATE TABLE otp_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,              -- Bcrypt hash
    context VARCHAR(20) NOT NULL,                -- 'DONATION', 'REQUEST'
    reference_id UUID,                           -- donation/request ID
    channel VARCHAR(20) DEFAULT 'SMS',           -- 'SMS', 'WHATSAPP'
    attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_expires ON otp_codes(phone, expires_at)
    WHERE verified_at IS NULL;
CREATE INDEX idx_otp_reference ON otp_codes(reference_id);

-- Auto-cleanup old OTPs (run via scheduled job)
-- DELETE FROM otp_codes WHERE expires_at < NOW() - INTERVAL '1 day';

-- ─────────────────────────────────────────────────────────────────────────────

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,            -- SHA-256 hash
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_token ON password_reset_tokens(token_hash);

-- ─────────────────────────────────────────────────────────────────────────────

-- Auth audit log
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    user_id UUID REFERENCES users(id),
    phone VARCHAR(20),
    email VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    risk_score INTEGER,                          -- For fraud detection
    metadata JSONB,                              -- Additional event data
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Event types:
-- LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT
-- OTP_SENT, OTP_VERIFIED, OTP_FAILED
-- 2FA_ENABLED, 2FA_DISABLED, 2FA_VERIFIED, 2FA_FAILED
-- PASSWORD_CHANGED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED
-- ACCOUNT_LOCKED, ACCOUNT_UNLOCKED
-- TOKEN_REFRESHED, TOKEN_REVOKED

CREATE INDEX idx_auth_audit_user ON auth_audit_log(user_id, created_at DESC);
CREATE INDEX idx_auth_audit_ip ON auth_audit_log(ip_address, created_at DESC);
CREATE INDEX idx_auth_audit_type ON auth_audit_log(event_type, created_at DESC);
CREATE INDEX idx_auth_audit_created ON auth_audit_log(created_at DESC);

-- Partition by month for large scale (optional)
-- CREATE TABLE auth_audit_log_2026_02 PARTITION OF auth_audit_log
--     FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- ─────────────────────────────────────────────────────────────────────────────

-- Rate limiting table (alternative to Redis)
CREATE TABLE rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(255) NOT NULL,                   -- e.g., 'otp:+201234567890' or 'login:192.168.1.1'
    action VARCHAR(50) NOT NULL,                 -- 'OTP_SEND', 'LOGIN', etc.
    count INTEGER DEFAULT 1,
    window_start TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(key, action, window_start)
);

CREATE INDEX idx_rate_limits_key ON rate_limits(key, action);
CREATE INDEX idx_rate_limits_window ON rate_limits(window_end);
```

---

## 9. Security Configuration

### 9.1 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF (stateless API)
            .csrf(csrf -> csrf.disable())

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/v1/public/**",
                    "/api/v1/auth/otp/**",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/password/forgot",
                    "/api/v1/auth/password/reset",
                    "/api/v1/track/**",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()

                // Partner endpoints
                .requestMatchers("/api/v1/partner/**")
                    .hasAnyRole("PARTNER_PHARMACY", "PARTNER_NGO", "PARTNER_VOLUNTEER")

                // Admin endpoints
                .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                // Authenticated endpoints (temp token holders)
                .requestMatchers("/api/v1/donations/*/images")
                    .hasAnyAuthority("DONATION_UPLOAD_IMAGE")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // JWT filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Exception handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "https://zaed.org",
            "https://www.zaed.org",
            "http://localhost:4200"  // Dev only
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

### 9.2 JWT Authentication Filter

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final Claims claims = jwtService.validateToken(jwt);
            final String tokenType = claims.get("type", String.class);

            Authentication authentication;

            if ("temp".equals(tokenType)) {
                // Temp token (OTP-verified donor/requester)
                authentication = createTempAuthentication(claims);
            } else if ("access".equals(tokenType)) {
                // Regular access token (partner/admin)
                authentication = createUserAuthentication(claims);
            } else {
                throw new InvalidTokenException("Invalid token type");
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (InvalidTokenException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            // Don't set authentication - will result in 401
        }

        filterChain.doFilter(request, response);
    }

    private Authentication createUserAuthentication(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);
        List<String> permissions = claims.get("permissions", List.class);

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        permissions.forEach(p ->
            authorities.add(new SimpleGrantedAuthority(p))
        );

        UserPrincipal principal = UserPrincipal.builder()
            .userId(userId)
            .email(claims.get("email", String.class))
            .role(role)
            .partnerId(claims.get("partnerId", String.class))
            .build();

        return new UsernamePasswordAuthenticationToken(
            principal, null, authorities
        );
    }

    private Authentication createTempAuthentication(Claims claims) {
        String phone = claims.getSubject().replace("phone:", "");
        String context = claims.get("context", String.class);
        List<String> permissions = claims.get("permissions", List.class);

        List<GrantedAuthority> authorities = permissions.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        TempPrincipal principal = TempPrincipal.builder()
            .phone(phone)
            .context(context)
            .referenceId(UUID.fromString(claims.get("referenceId", String.class)))
            .trackingCode(claims.get("trackingCode", String.class))
            .build();

        return new UsernamePasswordAuthenticationToken(
            principal, null, authorities
        );
    }
}
```

---

## 10. Security Best Practices

### 10.1 Implementation Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECURITY IMPLEMENTATION CHECKLIST                         │
│                                                                              │
│  PASSWORD SECURITY                                                           │
│  ─────────────────                                                          │
│  ☐ BCrypt with cost factor 12                                               │
│  ☐ Minimum 8 characters                                                     │
│  ☐ Require 1 uppercase + 1 number                                           │
│  ☐ Check against HaveIBeenPwned API (optional)                              │
│  ☐ Rate limit: 5 attempts per minute per IP                                 │
│  ☐ Account lockout: 10 failed attempts → 30 min lock                        │
│                                                                              │
│  OTP SECURITY                                                                │
│  ────────────                                                               │
│  ☐ 6-digit numeric codes                                                    │
│  ☐ 5-minute expiration                                                      │
│  ☐ Max 3 verification attempts per OTP                                      │
│  ☐ Rate limit: 3 OTPs per phone per hour                                   │
│  ☐ Store hashed OTP (not plain text)                                        │
│  ☐ Use Twilio Verify (handles rate limiting)                                │
│                                                                              │
│  JWT SECURITY                                                                │
│  ────────────                                                               │
│  ☐ HS256 minimum (HS512 or RS256 preferred)                                 │
│  ☐ Short access token expiry (1 hour)                                       │
│  ☐ Refresh token rotation on use                                            │
│  ☐ Store refresh tokens in DB (for revocation)                              │
│  ☐ Include jti claim for token blacklisting                                 │
│  ☐ Validate issuer and expiry on every request                              │
│                                                                              │
│  2FA SECURITY                                                                │
│  ───────────                                                                │
│  ☐ TOTP with 30-second window                                               │
│  ☐ Allow 1 code before/after for clock drift                               │
│  ☐ Encrypt TOTP secrets at rest (AES-256)                                   │
│  ☐ Hash backup codes (BCrypt)                                               │
│  ☐ Require 2FA for all admin accounts                                       │
│  ☐ Provide 10 backup codes                                                  │
│                                                                              │
│  API SECURITY                                                                │
│  ────────────                                                               │
│  ☐ HTTPS only (enforce HSTS)                                                │
│  ☐ CORS whitelist specific domains                                          │
│  ☐ Rate limiting per user and per IP                                        │
│  ☐ Request body size limits (1MB default)                                   │
│  ☐ Input validation on all endpoints                                        │
│  ☐ Parameterized queries (prevent SQL injection)                            │
│  ☐ Output encoding (prevent XSS)                                            │
│                                                                              │
│  AUDIT & MONITORING                                                          │
│  ──────────────────                                                         │
│  ☐ Log all auth events (success and failure)                                │
│  ☐ Log failed attempts with IP                                              │
│  ☐ Alert on brute force patterns                                            │
│  ☐ Alert on impossible travel (login from different countries)             │
│  ☐ Mask PII in logs (show only last 4 digits of phone)                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.2 Rate Limiting Configuration

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter otpSendLimiter() {
        return RateLimiter.of("otp-send",
            RateLimiterConfig.custom()
                .limitForPeriod(3)                    // 3 requests
                .limitRefreshPeriod(Duration.ofHours(1))  // per hour
                .timeoutDuration(Duration.ZERO)      // fail immediately
                .build()
        );
    }

    @Bean
    public RateLimiter loginLimiter() {
        return RateLimiter.of("login",
            RateLimiterConfig.custom()
                .limitForPeriod(5)                    // 5 attempts
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build()
        );
    }

    @Bean
    public RateLimiter otpVerifyLimiter() {
        return RateLimiter.of("otp-verify",
            RateLimiterConfig.custom()
                .limitForPeriod(5)                    // 5 attempts
                .limitRefreshPeriod(Duration.ofMinutes(5))
                .timeoutDuration(Duration.ZERO)
                .build()
        );
    }
}
```

---

## 11. Phase 2: Keycloak Migration

### 11.1 When to Migrate

Migrate to Keycloak when you need:

| Feature | Phase 1 (Spring Security) | Phase 2 (Keycloak) |
|---------|--------------------------|-------------------|
| Basic auth | ✓ | ✓ |
| Phone OTP | ✓ (Twilio) | ✓ (Custom SPI) |
| 2FA/TOTP | ✓ (java-otp) | ✓ (Built-in) |
| Social login | ✗ | ✓ |
| Multiple apps | Manual | ✓ (SSO) |
| User management UI | Build yourself | ✓ (Admin Console) |
| Enterprise SSO (SAML) | ✗ | ✓ |

### 11.2 Keycloak Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PHASE 2: KEYCLOAK ARCHITECTURE                            │
│                                                                              │
│                     ┌─────────────────────┐                                 │
│                     │     KEYCLOAK        │                                 │
│                     │                     │                                 │
│                     │  ┌───────────────┐  │                                 │
│                     │  │    Realm:     │  │                                 │
│                     │  │    "zaed"     │  │                                 │
│                     │  └───────────────┘  │                                 │
│                     │                     │                                 │
│                     │  Clients:           │                                 │
│                     │  • zaed-web         │                                 │
│                     │  • zaed-mobile-ios  │                                 │
│                     │  • zaed-mobile-android                                │
│                     │  • zaed-partner-api │                                 │
│                     │                     │                                 │
│                     │  Identity Providers:│                                 │
│                     │  • Google           │                                 │
│                     │  • Facebook         │                                 │
│                     │  • Apple            │                                 │
│                     │  • Phone OTP (SPI)  │                                 │
│                     │                     │                                 │
│                     │  Custom SPIs:       │                                 │
│                     │  • TwilioOtpAuth    │                                 │
│                     │  • EgyptPhoneFormat │                                 │
│                     │                     │                                 │
│                     └──────────┬──────────┘                                 │
│                                │                                             │
│     ┌──────────────────────────┼──────────────────────────┐                 │
│     │                          │                          │                 │
│     ▼                          ▼                          ▼                 │
│  ┌──────────┐           ┌──────────┐           ┌──────────┐                │
│  │  Web     │           │  Mobile  │           │ Partner  │                │
│  │  App     │           │  Apps    │           │   API    │                │
│  │ (Angular)│           │(iOS/And) │           │          │                │
│  └──────────┘           └──────────┘           └──────────┘                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.3 Migration Steps

```
1. Deploy Keycloak alongside existing auth
2. Create "zaed" realm with same roles/permissions
3. Import existing users (script)
4. Implement Phone OTP SPI for Keycloak
5. Update Angular to use Keycloak JS adapter
6. Update Spring Boot to validate Keycloak tokens
7. Run both systems in parallel (feature flag)
8. Gradually migrate users
9. Deprecate custom Identity Service
```

---

## 12. Testing Guide

### 12.1 Test Cases

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSendOtp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "phone": "+201234567890",
                        "channel": "SMS"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sent").value(true))
            .andExpect(jsonPath("$.expiresIn").value(300));
    }

    @Test
    void shouldRejectInvalidPhoneFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "phone": "01234567890",
                        "channel": "SMS"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_PHONE"));
    }

    @Test
    void shouldRateLimitOtpRequests() throws Exception {
        String phone = "+201234567890";

        // Send 3 OTPs (allowed)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/otp/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\": \"" + phone + "\"}"))
                .andExpect(status().isOk());
        }

        // 4th request should be rate limited
        mockMvc.perform(post("/api/v1/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\"}"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "partner@example.com",
                        "password": "ValidPassword123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void shouldRequire2FAForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "admin@zaed.org",
                        "password": "AdminPassword123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requires2FA").value(true))
            .andExpect(jsonPath("$.tempToken").exists());
    }
}
```

### 12.2 Manual Testing Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MANUAL TESTING CHECKLIST                             │
│                                                                              │
│  OTP FLOW                                                                    │
│  ────────                                                                   │
│  ☐ Send OTP to valid Egyptian phone number                                  │
│  ☐ Verify OTP arrives within 30 seconds                                     │
│  ☐ Verify OTP with correct code → success                                   │
│  ☐ Verify OTP with wrong code → error + attempts remaining                  │
│  ☐ Verify OTP after expiry → error                                          │
│  ☐ Request 4th OTP within hour → rate limited                               │
│  ☐ Temp token allows image upload                                           │
│  ☐ Temp token expires after 15 minutes                                      │
│                                                                              │
│  LOGIN FLOW                                                                  │
│  ──────────                                                                 │
│  ☐ Partner login with valid credentials → tokens                            │
│  ☐ Partner login with wrong password → error                                │
│  ☐ 5 wrong passwords → account locked message                               │
│  ☐ Admin login → 2FA challenge                                              │
│  ☐ Admin 2FA with valid TOTP → tokens                                       │
│  ☐ Admin 2FA with wrong TOTP → error                                        │
│  ☐ Admin login with backup code → success + warning                         │
│                                                                              │
│  TOKEN FLOW                                                                  │
│  ──────────                                                                 │
│  ☐ Access protected endpoint with valid token → success                     │
│  ☐ Access protected endpoint with expired token → 401                       │
│  ☐ Refresh token → new access token                                         │
│  ☐ Refresh with revoked token → 401                                         │
│  ☐ Logout → refresh token revoked                                           │
│  ☐ Logout all → all refresh tokens revoked                                  │
│                                                                              │
│  AUTHORIZATION                                                               │
│  ─────────────                                                              │
│  ☐ Partner can access /partner/** endpoints                                 │
│  ☐ Partner cannot access /admin/** endpoints → 403                          │
│  ☐ Admin can access all endpoints                                           │
│  ☐ Temp token can only upload images for own donation                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Appendix: Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `INVALID_PHONE` | 400 | Phone number format invalid |
| `INVALID_OTP` | 400 | OTP code incorrect |
| `OTP_EXPIRED` | 400 | OTP has expired |
| `INVALID_CREDENTIALS` | 401 | Email or password incorrect |
| `INVALID_TOKEN` | 401 | JWT token invalid or expired |
| `INVALID_TOTP` | 400 | 2FA code incorrect |
| `ACCOUNT_LOCKED` | 403 | Too many failed attempts |
| `ACCOUNT_DISABLED` | 403 | Account deactivated |
| `TOO_MANY_REQUESTS` | 429 | Rate limit exceeded |
| `TOO_MANY_ATTEMPTS` | 429 | Max verification attempts |
| `WEAK_PASSWORD` | 400 | Password doesn't meet requirements |
| `2FA_REQUIRED` | - | Not an error, indicates 2FA needed |

---

*This document provides complete implementation details for Zaed's Identity Service. For general architecture, see ARCHITECTURE.md. For the ADR on Identity Service architecture, see [ADR-009](adr/009-identity-service-architecture.md).*
