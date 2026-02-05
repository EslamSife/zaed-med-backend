# Zaed Med Connect - System Architecture Document

> **Version:** 1.0
> **Last Updated:** February 2026
> **Status:** Design Phase

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Overview](#2-system-overview)
3. [User Roles & Authentication Strategy](#3-user-roles--authentication-strategy)
4. [Authentication & Authorization Architecture](#4-authentication--authorization-architecture) ⭐ **NEW**
5. [Domain Model](#5-domain-model)
6. [Microservices Architecture](#6-microservices-architecture)
7. [API Specifications](#7-api-specifications)
8. [Event-Driven Architecture](#8-event-driven-architecture)
9. [Matching Algorithm](#9-matching-algorithm)
10. [Database Schema](#10-database-schema)
11. [Technology Stack](#11-technology-stack)
12. [Deployment Architecture](#12-deployment-architecture)
13. [Security](#13-security)
14. [Frontend Pages & Routes](#14-frontend-pages--routes)
15. [Development Roadmap](#15-development-roadmap)

---

## 1. Executive Summary

### What is Zaed?

Zaed (زائد) is a **medicine donation coordination platform** that connects people with unused medication to those who need it. Unlike traditional charity warehouses, Zaed is a **speed-first coordination engine** that matches donations with requests in real-time.

### Core Value Proposition

```
┌─────────────────────────────────────────────────────────────────┐
│                     ZAED'S MISSION                               │
│                                                                  │
│   "Medicine moves fast. Lives depend on it."                    │
│                                                                  │
│   • NOT a warehouse - a coordination engine                     │
│   • Speed is the primary metric (< 24h match time)              │
│   • Partner network handles physical logistics                  │
│   • Full transparency and tracking                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Metrics (Target)

| Metric | Target | Current |
|--------|--------|---------|
| Average Match Time | < 24 hours | - |
| Partner Network | 100+ pharmacies/NGOs | - |
| Cities Coverage | 15+ Egyptian cities | - |
| Monthly Matches | 500+ | - |

---

## 2. System Overview

### 2.1 System Context Diagram

```
                              ┌─────────────────┐
                              │   CDN / Static  │
                              │    (Angular)    │
                              └────────┬────────┘
                                       │
┌──────────┐  ┌──────────┐  ┌──────────▼──────────┐  ┌──────────┐
│  Donors  │  │Requesters│  │    API Gateway      │  │ Partners │
│          │──│          │──│  (Auth, Routing)    │──│(Pharmacy,│
│          │  │          │  │                     │  │ NGO,Vol.)│
└──────────┘  └──────────┘  └──────────┬──────────┘  └──────────┘
                                       │
         ┌─────────────┬───────────────┼───────────────┬─────────────┐
         │             │               │               │             │
         ▼             ▼               ▼               ▼             ▼
   ┌──────────┐ ┌──────────┐    ┌──────────┐   ┌──────────┐  ┌──────────┐
   │ Identity │ │ Donation │    │ Request  │   │ Matching │  │ Partner  │
   │ Service  │ │ Service  │    │ Service  │   │  Engine  │  │ Service  │
   └────┬─────┘ └────┬─────┘    └────┬─────┘   └────┬─────┘  └────┬─────┘
        │            │               │              │             │
        └────────────┴───────────────┴──────────────┴─────────────┘
                                     │
                          ┌──────────▼──────────┐
                          │   Message Broker    │
                          │   (Kafka/RabbitMQ)  │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                          │  Notification Svc   │
                          │  (SMS, WhatsApp,    │
                          │   Email, Push)      │
                          └─────────────────────┘
```

### 2.2 High-Level Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           HAPPY PATH FLOW                                    │
│                                                                              │
│  DONOR                    SYSTEM                      REQUESTER              │
│    │                        │                            │                   │
│    │  1. List medicine      │                            │                   │
│    │  ──────────────────►   │                            │                   │
│    │                        │                            │                   │
│    │  2. Upload photo       │                            │                   │
│    │  ──────────────────►   │                            │                   │
│    │                        │                            │                   │
│    │                        │   3. Admin verifies        │                   │
│    │                        │   ◄──────────────────      │                   │
│    │                        │                            │                   │
│    │                        │                            │  4. Submit request│
│    │                        │   ◄────────────────────────│                   │
│    │                        │                            │                   │
│    │                        │   5. MATCHING ENGINE       │                   │
│    │                        │   finds best match         │                   │
│    │                        │                            │                   │
│    │  6. "Match found!"     │   7. "Match found!"        │                   │
│    │  ◄──────────────────   │   ─────────────────────►   │                   │
│    │                        │                            │                   │
│    │  8. Drop at pharmacy   │                            │                   │
│    │  ──────────────────►   │                            │                   │
│    │                        │                            │                   │
│    │                        │   9. Partner confirms      │                   │
│    │                        │                            │                   │
│    │                        │   10. "Ready for pickup"   │                   │
│    │                        │   ─────────────────────►   │                   │
│    │                        │                            │                   │
│    │                        │                            │  11. Pick up      │
│    │                        │   ◄────────────────────────│                   │
│    │                        │                            │                   │
│    │  12. "Delivered!"      │   13. "Delivered!"         │                   │
│    │  ◄──────────────────   │   ─────────────────────►   │                   │
│    │                        │                            │                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. User Roles & Authentication Strategy

### 3.1 User Types

| Role | Description | Auth Required | Dashboard |
|------|-------------|---------------|-----------|
| **Donor** | Individual donating unused medicine | Phone OTP only | Track page |
| **Requester** | Individual needing medicine | Phone OTP only | Track page |
| **Partner - Pharmacy** | Verified pickup/distribution point | Full login | Partner dashboard |
| **Partner - NGO** | Organization connecting beneficiaries | Full login | Partner dashboard |
| **Partner - Volunteer** | Individual helping with logistics | Full login | Partner dashboard |
| **Admin** | Platform administrator | Full login + 2FA | Admin panel |

### 3.2 Authentication Strategy: Phone-First Hybrid

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION ARCHITECTURE                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ DONORS & REQUESTERS (Low Friction)                                   │   │
│  │                                                                      │   │
│  │   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │   │
│  │   │ Submit   │───►│  Enter   │───►│ Receive  │───►│  Track   │     │   │
│  │   │ Form     │    │  Phone   │    │   OTP    │    │  Code    │     │   │
│  │   └──────────┘    └──────────┘    └──────────┘    └──────────┘     │   │
│  │                                                                      │   │
│  │   • No password to remember                                          │   │
│  │   • Track via: zaed.org/track/ABC123                                │   │
│  │   • Updates via WhatsApp/SMS                                         │   │
│  │   • Optional: Link to full account later                            │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ PARTNERS (Verified Access)                                           │   │
│  │                                                                      │   │
│  │   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │   │
│  │   │ Apply    │───►│  Admin   │───►│ Receive  │───►│  Login   │     │   │
│  │   │ Online   │    │ Verifies │    │ Creds    │    │ Dashboard│     │   │
│  │   └──────────┘    └──────────┘    └──────────┘    └──────────┘     │   │
│  │                                                                      │   │
│  │   • Email + Password                                                 │   │
│  │   • Organization verification required                               │   │
│  │   • Access to partner dashboard                                      │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ ADMINS (High Security)                                               │   │
│  │                                                                      │   │
│  │   • Email + Password + 2FA (TOTP)                                    │   │
│  │   • IP whitelist (optional)                                          │   │
│  │   • Audit logging for all actions                                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Why Phone-First for Egypt?

1. **WhatsApp penetration**: 90%+ of Egyptians use WhatsApp daily
2. **Trust**: Phone verification feels more legitimate
3. **Low friction**: No password to remember
4. **Notifications**: Can send updates directly via WhatsApp
5. **Accessibility**: Works for users with low tech literacy

### 3.4 Track Page vs Dashboard

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  TRACK PAGE (Public, with code)          DASHBOARD (Authenticated)          │
│  /track/:trackingCode                    /dashboard, /partner, /admin       │
│                                                                              │
│  ┌─────────────────────────────┐        ┌─────────────────────────────┐    │
│  │                             │        │                             │    │
│  │  Track Your Donation        │        │  Partner Dashboard          │    │
│  │  ─────────────────────      │        │  ─────────────────────      │    │
│  │                             │        │                             │    │
│  │  Code: DON-ABC123           │        │  Welcome, Cairo Pharmacy    │    │
│  │                             │        │                             │    │
│  │  Status: ✓ Matched          │        │  Pending Pickups: 5         │    │
│  │                             │        │  Today's Deliveries: 3      │    │
│  │  ┌─────────────────────┐   │        │                             │    │
│  │  │ ● Submitted         │   │        │  ┌─────────────────────┐   │    │
│  │  │ ● Verified          │   │        │  │ Metformin 500mg     │   │    │
│  │  │ ● Matched ←         │   │        │  │ Status: Awaiting    │   │    │
│  │  │ ○ Picked up         │   │        │  │ [Confirm Pickup]    │   │    │
│  │  │ ○ Delivered         │   │        │  └─────────────────────┘   │    │
│  │  └─────────────────────┘   │        │                             │    │
│  │                             │        │  [View All] [Export]       │    │
│  │  Matched with: Partner #23  │        │                             │    │
│  │  Pickup: Cairo Pharmacy     │        │                             │    │
│  │                             │        │                             │    │
│  └─────────────────────────────┘        └─────────────────────────────┘    │
│                                                                              │
│  NO LOGIN REQUIRED                       LOGIN REQUIRED                      │
│  Just need tracking code                 Full authentication                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Authentication & Authorization Architecture

> **Technology Decision**: Java 21 + Spring Boot 4 + Spring Security 6

### 4.1 Authentication Options Analysis

We evaluated three main approaches for Zaed's authentication needs:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION OPTIONS COMPARISON                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ OPTION 1: KEYCLOAK (Full IAM)                                        │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  PROS:                                  CONS:                        │   │
│  │  ✓ Complete IAM solution                ✗ Heavy (separate service)   │   │
│  │  ✓ Built-in admin UI                    ✗ Complex deployment        │   │
│  │  ✓ OAuth2/OIDC/SAML support            ✗ Phone OTP needs custom SPI │   │
│  │  ✓ 2FA/MFA built-in (TOTP)             ✗ Steeper learning curve     │   │
│  │  ✓ Social login ready                   ✗ Resource intensive        │   │
│  │  ✓ Self-hosted (data sovereignty)                                   │   │
│  │  ✓ Enterprise SSO support                                           │   │
│  │  ✓ Excellent Spring Boot integration                                │   │
│  │                                                                      │   │
│  │  BEST FOR: Multiple apps, enterprise features, social login         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ OPTION 2: SPRING SECURITY + CUSTOM (Lightweight)                    │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  PROS:                                  CONS:                        │   │
│  │  ✓ Built into Spring Boot 4            ✗ Must build admin UI       │   │
│  │  ✓ No extra service to deploy          ✗ Must implement 2FA        │   │
│  │  ✓ Full control over auth flows        ✗ More code to maintain     │   │
│  │  ✓ Easy phone OTP implementation       ✗ Security risk if done wrong│   │
│  │  ✓ Lighter resource usage              ✗ No built-in user mgmt     │   │
│  │  ✓ Faster MVP development                                           │   │
│  │                                                                      │   │
│  │  BEST FOR: MVP, simple auth needs, phone-first auth                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ OPTION 3: AUTH0 / FIREBASE (Managed Service)                        │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  PROS:                                  CONS:                        │   │
│  │  ✓ Zero maintenance                     ✗ Data outside Egypt        │   │
│  │  ✓ Phone OTP built-in                   ✗ Costs scale with users    │   │
│  │  ✓ Social login built-in               ✗ Vendor lock-in            │   │
│  │  ✓ Great SDKs & docs                   ✗ Less control              │   │
│  │  ✓ Generous free tier                   ✗ Compliance concerns       │   │
│  │                                                                      │   │
│  │  BEST FOR: Startups, rapid prototyping, global apps                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Recommended Approach: Phased Implementation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              DECISION: PHASED HYBRID APPROACH                                │
│                                                                              │
│  ╔═══════════════════════════════════════════════════════════════════════╗ │
│  ║  PHASE 1 (MVP): Spring Security + Custom Implementation               ║ │
│  ╚═══════════════════════════════════════════════════════════════════════╝ │
│                                                                              │
│  WHY THIS FOR MVP:                                                           │
│  • Faster time-to-market (no Keycloak setup/config)                        │
│  • Phone OTP is core requirement - easier to implement directly            │
│  • Fewer moving parts = easier debugging                                    │
│  • Team can focus on business logic, not IAM configuration                 │
│  • Spring Security 6 is production-ready and secure                        │
│                                                                              │
│  ╔═══════════════════════════════════════════════════════════════════════╗ │
│  ║  PHASE 2+ (Scale): Migrate to Keycloak                                ║ │
│  ╚═══════════════════════════════════════════════════════════════════════╝ │
│                                                                              │
│  WHEN TO MIGRATE:                                                            │
│  • When you need mobile apps (iOS, Android) sharing auth                   │
│  • When partners want SSO with their systems                               │
│  • When you add social login (Google, Facebook, Apple)                     │
│  • When admin user management becomes complex                              │
│  • When you expand to multiple applications                                │
│                                                                              │
│  MIGRATION PATH:                                                             │
│  • OAuth2/JWT is standard - tokens remain compatible                       │
│  • Export users from PostgreSQL to Keycloak                                │
│  • Update Spring Security config to use Keycloak as provider               │
│  • Frontend token handling stays the same                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Phase 1: Spring Security Implementation

#### 4.3.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 PHASE 1: AUTH ARCHITECTURE                                   │
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
│  │  • Route: /api/v1/public/**  → No auth required                      │  │
│  │  • Route: /api/v1/partner/** → JWT validation (PARTNER roles)        │  │
│  │  • Route: /api/v1/admin/**   → JWT validation (ADMIN role)           │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│                                    ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                   IDENTITY SERVICE (Spring Boot 4)                    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐    │  │
│  │  │                    Spring Security 6                         │    │  │
│  │  │                                                              │    │  │
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐            │    │  │
│  │  │  │   OTP      │  │   JWT      │  │   2FA      │            │    │  │
│  │  │  │  Provider  │  │  Provider  │  │  Provider  │            │    │  │
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
│  │  │ • Partners  │  │ • Sessions  │  │ • WhatsApp  │                  │  │
│  │  │ • Admins    │  │ • Rate limit│  │   API       │                  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                  │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4.3.2 Authentication Flows

**Flow 1: Donor/Requester Phone OTP Authentication**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PHONE OTP AUTHENTICATION FLOW                            │
│                                                                              │
│  User                    Frontend                 Backend                    │
│   │                         │                        │                       │
│   │  1. Enter phone         │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  2. POST /auth/otp/send                       │
│   │                         │  {phone: "+20..."}     │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  3. Generate OTP      │
│   │                         │                        │     Store in Redis    │
│   │                         │                        │     (TTL: 5 min)      │
│   │                         │                        │                       │
│   │                         │                        │  4. Send via Twilio   │
│   │                         │                        │     SMS/WhatsApp      │
│   │                         │                        │                       │
│   │                         │  5. {sent: true,       │                       │
│   │                         │      expiresIn: 300}   │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │  6. Enter OTP code      │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  7. POST /auth/otp/verify                     │
│   │                         │  {phone, otp, context} │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  8. Validate OTP      │
│   │                         │                        │     Create/get user   │
│   │                         │                        │     Generate temp JWT │
│   │                         │                        │                       │
│   │                         │  9. {                  │                       │
│   │                         │      tempToken: "...", │                       │
│   │                         │      expiresIn: 900,   │                       │
│   │                         │      trackingCode: "DON-..."                  │
│   │                         │     }                  │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │  10. Redirect to        │                        │                       │
│   │      /track/{code}      │                        │                       │
│   │◄────────────────────────│                        │                       │
│   │                         │                        │                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Flow 2: Partner/Admin JWT Authentication**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PARTNER/ADMIN JWT AUTHENTICATION FLOW                     │
│                                                                              │
│  User                    Frontend                 Backend                    │
│   │                         │                        │                       │
│   │  1. Enter email/pass    │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  2. POST /auth/login   │                       │
│   │                         │  {email, password}     │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  3. Validate creds    │
│   │                         │                        │     Check if 2FA req  │
│   │                         │                        │                       │
│   │                         │  4a. If NO 2FA:        │                       │
│   │                         │  {accessToken, refresh}│                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │                         │  4b. If 2FA required:  │                       │
│   │                         │  {requires2FA: true,   │                       │
│   │                         │   tempToken: "..."}    │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │  5. Enter TOTP code     │                        │                       │
│   │  ──────────────────────►│                        │                       │
│   │                         │                        │                       │
│   │                         │  6. POST /auth/2fa/verify                     │
│   │                         │  {tempToken, totpCode} │                       │
│   │                         │───────────────────────►│                       │
│   │                         │                        │                       │
│   │                         │                        │  7. Validate TOTP     │
│   │                         │                        │     Issue full JWT    │
│   │                         │                        │                       │
│   │                         │  8. {accessToken,      │                       │
│   │                         │      refreshToken}     │                       │
│   │                         │◄───────────────────────│                       │
│   │                         │                        │                       │
│   │  9. Store tokens        │                        │                       │
│   │     Redirect to dashboard                        │                       │
│   │◄────────────────────────│                        │                       │
│   │                         │                        │                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4.3.3 JWT Token Structure

```java
// Access Token Claims
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // User ID
  "type": "access",
  "role": "PARTNER_PHARMACY",                     // User role
  "partnerId": "...",                             // For partners only
  "permissions": [                                // Fine-grained permissions
    "matches:view",
    "matches:update",
    "pickups:confirm"
  ],
  "iat": 1707134400,                              // Issued at
  "exp": 1707138000                               // Expires (1 hour)
}

// Refresh Token Claims
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "type": "refresh",
  "jti": "unique-token-id",                       // For revocation
  "iat": 1707134400,
  "exp": 1707739200                               // Expires (7 days)
}

// Temporary Token (for OTP-verified donors/requesters)
{
  "sub": "phone:+201234567890",
  "type": "temp",
  "context": "DONATION",                          // or "REQUEST"
  "referenceId": "donation-uuid",
  "permissions": ["donation:upload-image"],       // Limited permissions
  "iat": 1707134400,
  "exp": 1707135300                               // Expires (15 min)
}
```

#### 4.3.4 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())  // API-only, CSRF not needed
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no auth required
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/track/**").permitAll()

                // Partner endpoints - require PARTNER role
                .requestMatchers("/api/v1/partner/**").hasAnyRole(
                    "PARTNER_PHARMACY", "PARTNER_NGO", "PARTNER_VOLUNTEER"
                )

                // Admin endpoints - require ADMIN role
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                .accessDeniedHandler(new CustomAccessDeniedHandler())
            )
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
}
```

#### 4.3.5 Role & Permission Matrix

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ROLE & PERMISSION MATRIX                                  │
│                                                                              │
│  ┌─────────────────┬─────────────────────────────────────────────────────┐ │
│  │     ROLE        │                    PERMISSIONS                       │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ DONOR (temp)    │ • donation:create                                   │ │
│  │                 │ • donation:upload-image (own only)                  │ │
│  │                 │ • track:view (own only)                             │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ REQUESTER (temp)│ • request:create                                    │ │
│  │                 │ • track:view (own only)                             │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ PARTNER_PHARMACY│ • partner:dashboard:view                            │ │
│  │ PARTNER_NGO     │ • matches:view (assigned only)                      │ │
│  │ PARTNER_VOLUNTEER│• matches:update-status (assigned only)             │ │
│  │                 │ • pickups:confirm                                   │ │
│  │                 │ • deliveries:confirm                                │ │
│  │                 │                                                      │ │
│  ├─────────────────┼─────────────────────────────────────────────────────┤ │
│  │                 │                                                      │ │
│  │ ADMIN           │ • ALL permissions                                   │ │
│  │                 │ • donations:verify                                  │ │
│  │                 │ • donations:reject                                  │ │
│  │                 │ • partners:manage                                   │ │
│  │                 │ • partners:verify                                   │ │
│  │                 │ • users:manage                                      │ │
│  │                 │ • reports:view                                      │ │
│  │                 │ • settings:manage                                   │ │
│  │                 │                                                      │ │
│  └─────────────────┴─────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Phase 2: Keycloak Migration (Future)

When Zaed scales and needs enterprise features, migrate to Keycloak:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PHASE 2: KEYCLOAK ARCHITECTURE                            │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │                     ┌─────────────────────┐                          │  │
│  │                     │     KEYCLOAK        │                          │  │
│  │                     │                     │                          │  │
│  │                     │  ┌───────────────┐  │                          │  │
│  │                     │  │    Realm:     │  │                          │  │
│  │                     │  │    "zaed"     │  │                          │  │
│  │                     │  └───────────────┘  │                          │  │
│  │                     │                     │                          │  │
│  │                     │  Clients:           │                          │  │
│  │                     │  • zaed-web         │                          │  │
│  │                     │  • zaed-mobile      │                          │  │
│  │                     │  • zaed-partner-api │                          │  │
│  │                     │                     │                          │  │
│  │                     │  Identity Providers:│                          │  │
│  │                     │  • Google           │                          │  │
│  │                     │  • Facebook         │                          │  │
│  │                     │  • Phone OTP (SPI)  │                          │  │
│  │                     │                     │                          │  │
│  │                     └──────────┬──────────┘                          │  │
│  │                                │                                      │  │
│  │     ┌──────────────────────────┼──────────────────────────┐          │  │
│  │     │                          │                          │          │  │
│  │     ▼                          ▼                          ▼          │  │
│  │  ┌──────────┐           ┌──────────┐           ┌──────────┐         │  │
│  │  │  Web     │           │  Mobile  │           │ Partner  │         │  │
│  │  │  App     │           │  Apps    │           │   API    │         │  │
│  │  │ (Angular)│           │(iOS/And) │           │          │         │  │
│  │  └──────────┘           └──────────┘           └──────────┘         │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  KEYCLOAK FEATURES TO USE:                                                   │
│  ─────────────────────────                                                  │
│  • Realm for multi-tenancy (if expanding to other countries)               │
│  • Custom Phone OTP Authenticator (Keycloak SPI)                           │
│  • User Federation (if partners have LDAP)                                  │
│  • Social Login (Google, Facebook, Apple)                                   │
│  • Admin Console for user management                                        │
│  • Built-in brute force protection                                          │
│  • Audit logging                                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Auth API Endpoints

```yaml
# ════════════════════════════════════════════════════════════════════════════
#                         AUTHENTICATION API
# ════════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# OTP ENDPOINTS (For Donors/Requesters)
# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/otp/send
  Description: Send OTP to phone number
  Request:
    phone: string        # E.164 format: +201234567890
    channel: string      # "SMS" or "WHATSAPP" (default: SMS)
  Response: 200 OK
    sent: boolean
    expiresIn: number    # seconds (300 = 5 min)
    retryAfter: number   # seconds until can resend
  Errors:
    429: Rate limited (max 3 OTPs per phone per hour)
    400: Invalid phone format

POST /api/v1/auth/otp/verify
  Description: Verify OTP and get temporary token
  Request:
    phone: string
    otp: string          # 6-digit code
    context: string      # "DONATION" or "REQUEST"
    referenceId: string  # The donation/request ID
  Response: 200 OK
    verified: boolean
    tempToken: string    # JWT, 15 min expiry
    trackingCode: string # e.g., "DON-ABC123"
  Errors:
    400: Invalid/expired OTP
    429: Too many failed attempts

# ─────────────────────────────────────────────────────────────────────────────
# JWT ENDPOINTS (For Partners/Admins)
# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/login
  Description: Login with email and password
  Request:
    email: string
    password: string
  Response: 200 OK
    # If 2FA not required:
    accessToken: string
    refreshToken: string
    expiresIn: number
    user: {
      id: string
      email: string
      role: string
      partnerId?: string
    }
    # If 2FA required:
    requires2FA: boolean
    tempToken: string
    methods: ["TOTP"]
  Errors:
    401: Invalid credentials
    403: Account disabled
    429: Too many failed attempts

POST /api/v1/auth/2fa/verify
  Description: Complete 2FA verification
  Request:
    tempToken: string
    totpCode: string     # 6-digit TOTP code
  Response: 200 OK
    accessToken: string
    refreshToken: string
    expiresIn: number
  Errors:
    400: Invalid TOTP code
    401: Invalid/expired temp token

POST /api/v1/auth/refresh
  Description: Refresh access token
  Request:
    refreshToken: string
  Response: 200 OK
    accessToken: string
    refreshToken: string  # Rotated
    expiresIn: number
  Errors:
    401: Invalid/expired refresh token

POST /api/v1/auth/logout
  Description: Invalidate refresh token
  Request:
    refreshToken: string
  Response: 204 No Content

# ─────────────────────────────────────────────────────────────────────────────
# 2FA SETUP ENDPOINTS (For Partners/Admins)
# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/2fa/setup
  Description: Initialize 2FA setup
  Headers:
    Authorization: Bearer {accessToken}
  Response: 200 OK
    secret: string       # Base32 encoded
    qrCodeUrl: string    # otpauth:// URL for QR code
    backupCodes: string[] # 10 one-time backup codes

POST /api/v1/auth/2fa/enable
  Description: Enable 2FA after verifying setup
  Headers:
    Authorization: Bearer {accessToken}
  Request:
    totpCode: string     # Code from authenticator app
  Response: 200 OK
    enabled: boolean
  Errors:
    400: Invalid TOTP code

DELETE /api/v1/auth/2fa
  Description: Disable 2FA
  Headers:
    Authorization: Bearer {accessToken}
  Request:
    password: string     # Require password confirmation
  Response: 204 No Content

# ─────────────────────────────────────────────────────────────────────────────
# PASSWORD MANAGEMENT
# ─────────────────────────────────────────────────────────────────────────────

POST /api/v1/auth/password/forgot
  Description: Request password reset
  Request:
    email: string
  Response: 200 OK
    message: "If account exists, reset email sent"
  # Always return 200 to prevent email enumeration

POST /api/v1/auth/password/reset
  Description: Reset password with token
  Request:
    token: string        # From email link
    newPassword: string
  Response: 200 OK
  Errors:
    400: Invalid/expired token
    400: Password too weak

PUT /api/v1/auth/password
  Description: Change password (authenticated)
  Headers:
    Authorization: Bearer {accessToken}
  Request:
    currentPassword: string
    newPassword: string
  Response: 200 OK
  Errors:
    400: Current password incorrect
    400: New password too weak
```

### 4.6 Security Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECURITY IMPLEMENTATION CHECKLIST                         │
│                                                                              │
│  PASSWORD SECURITY                                                           │
│  ─────────────────                                                          │
│  ☐ Bcrypt with cost factor 12+ for password hashing                        │
│  ☐ Minimum 8 characters, require complexity                                 │
│  ☐ Check against breached password database (HaveIBeenPwned API)           │
│  ☐ Rate limit login attempts (5 per minute per IP)                         │
│  ☐ Account lockout after 10 failed attempts (30 min)                       │
│                                                                              │
│  OTP SECURITY                                                                │
│  ────────────                                                               │
│  ☐ 6-digit numeric codes                                                    │
│  ☐ 5-minute expiration                                                      │
│  ☐ Max 3 verification attempts per OTP                                      │
│  ☐ Rate limit: 3 OTPs per phone per hour                                   │
│  ☐ Store hashed OTP in Redis (not plain text)                              │
│                                                                              │
│  JWT SECURITY                                                                │
│  ────────────                                                               │
│  ☐ RS256 algorithm (asymmetric) for production                             │
│  ☐ Short access token expiry (1 hour)                                       │
│  ☐ Refresh token rotation on use                                            │
│  ☐ Store refresh tokens in database (for revocation)                        │
│  ☐ Include jti (JWT ID) for token blacklisting                             │
│                                                                              │
│  2FA SECURITY                                                                │
│  ───────────                                                                │
│  ☐ TOTP with 30-second window                                               │
│  ☐ Allow 1 code before/after for clock drift                               │
│  ☐ Encrypt TOTP secrets at rest                                             │
│  ☐ Provide backup codes (hashed, one-time use)                             │
│  ☐ Require 2FA for all admin accounts                                       │
│                                                                              │
│  API SECURITY                                                                │
│  ────────────                                                               │
│  ☐ HTTPS only (HSTS header)                                                 │
│  ☐ CORS whitelist for frontend domains only                                │
│  ☐ Rate limiting per user and per IP                                        │
│  ☐ Request size limits                                                      │
│  ☐ Input validation on all endpoints                                        │
│  ☐ SQL injection prevention (parameterized queries)                        │
│  ☐ XSS prevention (output encoding)                                         │
│                                                                              │
│  AUDIT & MONITORING                                                          │
│  ──────────────────                                                         │
│  ☐ Log all authentication events                                            │
│  ☐ Log failed login attempts with IP                                        │
│  ☐ Alert on suspicious patterns (brute force, credential stuffing)         │
│  ☐ Log admin actions with before/after state                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.7 Database Schema for Auth

```sql
-- ════════════════════════════════════════════════════════════════════════════
--                         AUTH-RELATED TABLES
-- ════════════════════════════════════════════════════════════════════════════

-- User credentials (for partners and admins)
CREATE TABLE user_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2FA settings
CREATE TABLE user_2fa (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    totp_secret_encrypted VARCHAR(255),  -- AES-256 encrypted
    is_enabled BOOLEAN DEFAULT FALSE,
    enabled_at TIMESTAMP WITH TIME ZONE,
    backup_codes_hash TEXT[],            -- Bcrypt hashed, 10 codes
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Refresh tokens (for revocation)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,    -- SHA-256 hash of token
    device_info VARCHAR(255),            -- User agent
    ip_address INET,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_hash (token_hash)
);

-- OTP codes (in Redis, but fallback table)
CREATE TABLE otp_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,      -- Bcrypt hash
    context VARCHAR(20) NOT NULL,        -- 'DONATION', 'REQUEST'
    reference_id UUID,
    attempts INTEGER DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    INDEX idx_otp_phone (phone, expires_at)
);

-- Audit log for auth events
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,     -- 'LOGIN', 'LOGOUT', 'OTP_SENT', etc.
    user_id UUID REFERENCES users(id),
    phone VARCHAR(20),                   -- For OTP events
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_auth_audit_user ON auth_audit_log(user_id, created_at DESC);
CREATE INDEX idx_auth_audit_ip ON auth_audit_log(ip_address, created_at DESC);
CREATE INDEX idx_auth_audit_type ON auth_audit_log(event_type, created_at DESC);
```

---

## 5. Domain Model

### 5.1 Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BOUNDED CONTEXTS                                     │
│                                                                              │
│   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   │
│   │    USER     │   │  DONATION   │   │   REQUEST   │   │  MATCHING   │   │
│   │   CONTEXT   │   │   CONTEXT   │   │   CONTEXT   │   │   CONTEXT   │   │
│   │             │   │             │   │             │   │   (CORE)    │   │
│   │ • User      │   │ • Donation  │   │ • Request   │   │ • Match     │   │
│   │ • Auth      │   │ • Image     │   │ • Urgency   │   │ • Score     │   │
│   │ • Profile   │   │ • Verify    │   │ • SLA       │   │ • Algorithm │   │
│   └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘   │
│                                                                              │
│   ┌─────────────┐   ┌─────────────┐                                         │
│   │   PARTNER   │   │NOTIFICATION │                                         │
│   │   CONTEXT   │   │   CONTEXT   │                                         │
│   │             │   │             │                                         │
│   │ • Partner   │   │ • SMS       │                                         │
│   │ • Location  │   │ • WhatsApp  │                                         │
│   │ • Capacity  │   │ • Email     │                                         │
│   └─────────────┘   └─────────────┘                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Entity Definitions

#### User Entity

```typescript
interface User {
  id: UUID;
  phone: string;              // Primary identifier for donors/requesters
  email?: string;             // Optional, required for partners/admins
  name?: string;
  role: UserRole;
  preferredLocation?: Location;
  isVerified: boolean;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

enum UserRole {
  DONOR = 'DONOR',
  REQUESTER = 'REQUESTER',
  PARTNER_PHARMACY = 'PARTNER_PHARMACY',
  PARTNER_NGO = 'PARTNER_NGO',
  PARTNER_VOLUNTEER = 'PARTNER_VOLUNTEER',
  ADMIN = 'ADMIN'
}
```

#### Donation Entity

```typescript
interface Donation {
  id: UUID;
  trackingCode: string;       // e.g., "DON-ABC123"

  // Donor info (may not have full user account)
  donorPhone: string;
  donorName?: string;
  donorUserId?: UUID;         // Linked if user creates account

  // Medicine details
  medicineName: string;
  quantity: string;
  expiryDate: Date;
  notes?: string;

  // Location
  location: Location;

  // Status tracking
  status: DonationStatus;
  verifiedAt?: Timestamp;
  verifiedBy?: UUID;

  // Images
  images: DonationImage[];

  // Timestamps
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

enum DonationStatus {
  PENDING_VERIFICATION = 'PENDING_VERIFICATION',
  AVAILABLE = 'AVAILABLE',           // Verified and ready for matching
  MATCHED = 'MATCHED',               // Matched with a request
  AWAITING_PICKUP = 'AWAITING_PICKUP',
  FULFILLED = 'FULFILLED',
  EXPIRED = 'EXPIRED',
  REJECTED = 'REJECTED'              // Failed verification
}

interface DonationImage {
  id: UUID;
  donationId: UUID;
  url: string;
  uploadedAt: Timestamp;
}
```

#### Request Entity

```typescript
interface MedicineRequest {
  id: UUID;
  trackingCode: string;       // e.g., "REQ-XYZ789"

  // Requester info
  requesterPhone: string;
  requesterName?: string;
  requesterUserId?: UUID;

  // Medicine details
  medicineName: string;
  quantity: string;
  notes?: string;

  // Urgency & SLA
  urgency: UrgencyLevel;
  maxWaitDays: number;
  slaDeadline: Timestamp;

  // Location & Contact
  location: Location;
  contactMethod: string;      // "WhatsApp", "SMS", "Call"

  // Status
  status: RequestStatus;

  // Timestamps
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

enum UrgencyLevel {
  LOW = 'LOW',           // Within a week
  MEDIUM = 'MEDIUM',     // Within 2-3 days
  HIGH = 'HIGH'          // Within 24 hours
}

enum RequestStatus {
  PENDING = 'PENDING',
  MATCHED = 'MATCHED',
  AWAITING_PICKUP = 'AWAITING_PICKUP',
  FULFILLED = 'FULFILLED',
  EXPIRED = 'EXPIRED',
  CANCELLED = 'CANCELLED'
}
```

#### Match Entity

```typescript
interface Match {
  id: UUID;

  // References
  donationId: UUID;
  requestId: UUID;
  partnerId: UUID;            // Pickup/distribution point

  // Match details
  matchScore: number;         // 0-100, algorithm output
  status: MatchStatus;

  // Tracking
  matchedAt: Timestamp;
  pickedUpAt?: Timestamp;
  deliveredAt?: Timestamp;
  confirmedAt?: Timestamp;

  // Feedback
  recipientRating?: number;   // 1-5
  recipientFeedback?: string;
}

enum MatchStatus {
  PENDING_DONOR_DROP = 'PENDING_DONOR_DROP',
  AWAITING_PICKUP = 'AWAITING_PICKUP',
  READY_FOR_RECIPIENT = 'READY_FOR_RECIPIENT',
  DELIVERED = 'DELIVERED',
  CONFIRMED = 'CONFIRMED',
  CANCELLED = 'CANCELLED'
}
```

#### Partner Entity

```typescript
interface Partner {
  id: UUID;
  userId: UUID;               // Linked user account

  // Organization details
  name: string;
  organization: string;
  type: PartnerType;

  // Contact
  email: string;
  phone: string;

  // Location
  location: Location;
  serviceRadius: number;      // km

  // Status
  isActive: boolean;
  isVerified: boolean;
  verifiedAt?: Timestamp;

  // Capabilities
  capabilities: string[];     // e.g., ["cold_storage", "controlled_substances"]
  maxCapacity: number;        // Max items they can hold

  // Stats
  totalHandled: number;
  averageRating: number;

  createdAt: Timestamp;
}

enum PartnerType {
  PHARMACY = 'PHARMACY',
  NGO = 'NGO',
  VOLUNTEER = 'VOLUNTEER'
}
```

#### Shared Value Objects

```typescript
interface Location {
  city: string;               // e.g., "Cairo"
  area: string;               // e.g., "Maadi"
  address?: string;           // Full address (optional)
  coordinates?: {
    lat: number;
    lng: number;
  };
}

interface TrackingCode {
  prefix: 'DON' | 'REQ';
  code: string;               // 6 alphanumeric characters

  // Format: DON-ABC123, REQ-XYZ789
  toString(): string;
}
```

---

## 6. Microservices Architecture

### 6.1 Service Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MICROSERVICES OVERVIEW                                │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                         API GATEWAY                                    │ │
│  │              (Kong / Traefik / AWS API Gateway)                       │ │
│  │                                                                        │ │
│  │  Responsibilities:                                                     │ │
│  │  • Request routing                                                     │ │
│  │  • Authentication (JWT validation)                                     │ │
│  │  • Rate limiting                                                       │ │
│  │  • Request/Response logging                                            │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                      │                                       │
│      ┌───────────────────────────────┼───────────────────────────────┐      │
│      │                               │                               │      │
│      ▼                               ▼                               ▼      │
│  ┌─────────┐  ┌─────────┐  ┌─────────────┐  ┌─────────┐  ┌─────────────┐  │
│  │IDENTITY │  │DONATION │  │   REQUEST   │  │ PARTNER │  │  MATCHING   │  │
│  │ SERVICE │  │ SERVICE │  │   SERVICE   │  │ SERVICE │  │   ENGINE    │  │
│  │         │  │         │  │             │  │         │  │   (CORE)    │  │
│  │ Port:   │  │ Port:   │  │   Port:     │  │ Port:   │  │   Port:     │  │
│  │  8081   │  │  8082   │  │    8083     │  │  8084   │  │    8085     │  │
│  └────┬────┘  └────┬────┘  └──────┬──────┘  └────┬────┘  └──────┬──────┘  │
│       │            │              │              │              │          │
│       │            │              │              │              │          │
│       └────────────┴──────────────┴──────────────┴──────────────┘          │
│                                   │                                         │
│                    ┌──────────────▼──────────────┐                         │
│                    │       MESSAGE BROKER        │                         │
│                    │     (Kafka / RabbitMQ)      │                         │
│                    └──────────────┬──────────────┘                         │
│                                   │                                         │
│                    ┌──────────────▼──────────────┐                         │
│                    │    NOTIFICATION SERVICE     │                         │
│                    │         Port: 8086          │                         │
│                    │   (SMS, WhatsApp, Email)    │                         │
│                    └─────────────────────────────┘                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Service Responsibilities

| Service | Responsibility | Database | Events Published | Events Consumed |
|---------|---------------|----------|------------------|-----------------|
| **Identity Service** | Auth, user identity, OTP | PostgreSQL | UserCreated, OtpSent | - |
| **Donation Service** | Donation CRUD, images | PostgreSQL + S3 | DonationCreated, DonationVerified | - |
| **Request Service** | Request CRUD, SLA | PostgreSQL | RequestCreated, RequestExpired | - |
| **Partner Service** | Partner management | PostgreSQL | PartnerVerified | - |
| **Matching Engine** | Smart matching | PostgreSQL + Redis | MatchFound, MatchFulfilled | DonationVerified, RequestCreated |
| **Notification Service** | SMS, WhatsApp, Email | Redis | - | All events |

---

## 7. API Specifications

### 7.1 Public API (No Auth Required)

#### Submit Donation

```yaml
POST /api/v1/donations
Content-Type: application/json

Request:
{
  "phone": "+201234567890",      # Required - for OTP verification
  "name": "Ahmed Hassan",         # Optional
  "medicineName": "Metformin 500mg",
  "quantity": "30 tablets",
  "expiryDate": "2026-06-15",
  "location": {
    "city": "Cairo",
    "area": "Maadi"
  },
  "notes": "Unopened box"
}

Response: 201 Created
{
  "id": "uuid",
  "trackingCode": "DON-ABC123",
  "status": "PENDING_VERIFICATION",
  "message": "OTP sent to your phone. Please verify to complete submission.",
  "otpRequired": true
}
```

#### Verify OTP

```yaml
POST /api/v1/auth/verify-otp
Content-Type: application/json

Request:
{
  "phone": "+201234567890",
  "otp": "123456",
  "referenceId": "uuid"           # The donation/request ID
}

Response: 200 OK
{
  "verified": true,
  "trackingCode": "DON-ABC123",
  "trackingUrl": "https://zaed.org/track/DON-ABC123"
}
```

#### Upload Donation Image

```yaml
POST /api/v1/donations/{id}/images
Content-Type: multipart/form-data
Authorization: Bearer {temp-token}    # Short-lived token from OTP verification

Request:
  - image: File (max 5MB, jpg/png)

Response: 200 OK
{
  "imageId": "uuid",
  "imageUrl": "https://cdn.zaed.org/donations/..."
}
```

#### Submit Medicine Request

```yaml
POST /api/v1/requests
Content-Type: application/json

Request:
{
  "phone": "+201234567890",
  "name": "Fatma Ali",
  "medicineName": "Metformin 500mg",
  "quantity": "30 tablets",
  "urgency": "MEDIUM",            # LOW, MEDIUM, HIGH
  "location": {
    "city": "Cairo",
    "area": "Heliopolis"
  },
  "contactMethod": "WhatsApp",
  "notes": "For elderly parent"
}

Response: 201 Created
{
  "id": "uuid",
  "trackingCode": "REQ-XYZ789",
  "status": "PENDING",
  "slaDeadline": "2026-02-08T12:00:00Z",
  "message": "OTP sent to your phone.",
  "otpRequired": true
}
```

#### Track Donation/Request (Public)

```yaml
GET /api/v1/track/{trackingCode}

Response: 200 OK
{
  "trackingCode": "DON-ABC123",
  "type": "DONATION",
  "status": "MATCHED",
  "medicineName": "Metformin 500mg",
  "quantity": "30 tablets",
  "location": {
    "city": "Cairo",
    "area": "Maadi"
  },
  "timeline": [
    {
      "status": "PENDING_VERIFICATION",
      "timestamp": "2026-02-05T10:00:00Z",
      "message": "Donation submitted"
    },
    {
      "status": "AVAILABLE",
      "timestamp": "2026-02-05T11:30:00Z",
      "message": "Verified by admin"
    },
    {
      "status": "MATCHED",
      "timestamp": "2026-02-05T14:00:00Z",
      "message": "Matched with a request"
    }
  ],
  "match": {
    "partnerId": "uuid",
    "partnerName": "Cairo Pharmacy - Maadi",
    "partnerAddress": "123 Road 9, Maadi",
    "partnerPhone": "+20xxxxxxxxx",
    "instructions": "Please drop off during business hours (9am-9pm)"
  }
}
```

### 7.2 Partner API (Auth Required)

#### Partner Login

```yaml
POST /api/v1/auth/partner/login
Content-Type: application/json

Request:
{
  "email": "pharmacy@example.com",
  "password": "********"
}

Response: 200 OK
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "expiresIn": 3600,
  "partner": {
    "id": "uuid",
    "name": "Cairo Pharmacy - Maadi",
    "type": "PHARMACY"
  }
}
```

#### Get Partner Dashboard

```yaml
GET /api/v1/partners/me/dashboard
Authorization: Bearer {accessToken}

Response: 200 OK
{
  "partner": {
    "id": "uuid",
    "name": "Cairo Pharmacy - Maadi",
    "type": "PHARMACY"
  },
  "stats": {
    "pendingPickups": 5,
    "awaitingRecipients": 3,
    "completedToday": 8,
    "completedThisMonth": 127,
    "averageRating": 4.8
  },
  "pendingItems": [
    {
      "matchId": "uuid",
      "donationTrackingCode": "DON-ABC123",
      "medicineName": "Metformin 500mg",
      "quantity": "30 tablets",
      "donorName": "Ahmed H.",
      "status": "PENDING_DONOR_DROP",
      "createdAt": "2026-02-05T14:00:00Z"
    }
  ]
}
```

#### Confirm Pickup (Partner)

```yaml
POST /api/v1/matches/{matchId}/confirm-pickup
Authorization: Bearer {accessToken}

Request:
{
  "notes": "Received in good condition",
  "verifiedExpiry": true
}

Response: 200 OK
{
  "matchId": "uuid",
  "status": "READY_FOR_RECIPIENT",
  "message": "Recipient has been notified"
}
```

#### Confirm Delivery (Partner)

```yaml
POST /api/v1/matches/{matchId}/confirm-delivery
Authorization: Bearer {accessToken}

Request:
{
  "recipientVerified": true,      # Checked ID or phone
  "notes": "Delivered successfully"
}

Response: 200 OK
{
  "matchId": "uuid",
  "status": "DELIVERED",
  "message": "Both donor and recipient have been notified"
}
```

### 7.3 Admin API (Auth + Role Required)

#### Admin Login

```yaml
POST /api/v1/auth/admin/login
Content-Type: application/json

Request:
{
  "email": "admin@zaed.org",
  "password": "********",
  "totpCode": "123456"            # 2FA required
}

Response: 200 OK
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "admin": {
    "id": "uuid",
    "name": "Admin User",
    "permissions": ["donations:verify", "partners:manage", "reports:view"]
  }
}
```

#### Get Pending Verifications

```yaml
GET /api/v1/admin/donations/pending
Authorization: Bearer {accessToken}

Response: 200 OK
{
  "total": 12,
  "items": [
    {
      "id": "uuid",
      "trackingCode": "DON-ABC123",
      "medicineName": "Metformin 500mg",
      "quantity": "30 tablets",
      "expiryDate": "2026-06-15",
      "images": [
        "https://cdn.zaed.org/donations/..."
      ],
      "donorPhone": "+20123****890",
      "location": {
        "city": "Cairo",
        "area": "Maadi"
      },
      "submittedAt": "2026-02-05T10:00:00Z"
    }
  ]
}
```

#### Verify Donation (Admin)

```yaml
POST /api/v1/admin/donations/{id}/verify
Authorization: Bearer {accessToken}

Request:
{
  "approved": true,
  "notes": "Medicine verified, expiry date clear"
}

Response: 200 OK
{
  "id": "uuid",
  "status": "AVAILABLE",
  "message": "Donation verified and now available for matching"
}
```

#### Reject Donation (Admin)

```yaml
POST /api/v1/admin/donations/{id}/reject
Authorization: Bearer {accessToken}

Request:
{
  "reason": "EXPIRED",            # EXPIRED, DAMAGED, UNREADABLE, CONTROLLED_SUBSTANCE, OTHER
  "notes": "Medicine already expired"
}

Response: 200 OK
{
  "id": "uuid",
  "status": "REJECTED",
  "message": "Donor has been notified"
}
```

---

## 8. Event-Driven Architecture

### 8.1 Event Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EVENT FLOW                                         │
│                                                                              │
│  DONATION SERVICE                    MATCHING ENGINE                         │
│  ─────────────────                   ──────────────────                      │
│        │                                    │                                │
│        │ DonationVerified                   │                                │
│        │ ─────────────────────────────────► │                                │
│        │                                    │                                │
│        │                                    │ (runs matching algorithm)      │
│        │                                    │                                │
│        │                              MatchFound                             │
│        │ ◄───────────────────────────────── │                                │
│        │                                    │                                │
│        │                                    │                                │
│  REQUEST SERVICE                            │                                │
│  ───────────────                            │                                │
│        │                                    │                                │
│        │ RequestCreated                     │                                │
│        │ ─────────────────────────────────► │                                │
│        │                                    │                                │
│        │                              MatchFound                             │
│        │ ◄───────────────────────────────── │                                │
│        │                                    │                                │
│                                                                              │
│  NOTIFICATION SERVICE (Subscribes to all events)                            │
│  ────────────────────────────────────────────────                           │
│                                                                              │
│  • DonationVerified  → SMS to donor: "Your donation is now live"            │
│  • RequestCreated    → SMS to requester: "We're searching for your medicine"│
│  • MatchFound        → SMS to both: "Match found!"                          │
│  • MatchFulfilled    → SMS to both: "Delivery confirmed. Thank you!"        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Event Schemas

```typescript
// Base event structure
interface BaseEvent {
  eventId: string;          // UUID
  eventType: string;
  timestamp: string;        // ISO 8601
  version: string;          // "1.0"
  source: string;           // Service name
}

// Donation Events
interface DonationCreatedEvent extends BaseEvent {
  eventType: 'DONATION_CREATED';
  payload: {
    donationId: string;
    trackingCode: string;
    donorPhone: string;
    medicineName: string;
    quantity: string;
    expiryDate: string;
    location: Location;
  };
}

interface DonationVerifiedEvent extends BaseEvent {
  eventType: 'DONATION_VERIFIED';
  payload: {
    donationId: string;
    trackingCode: string;
    verifiedBy: string;
    medicineName: string;
    location: Location;
  };
}

// Request Events
interface RequestCreatedEvent extends BaseEvent {
  eventType: 'REQUEST_CREATED';
  payload: {
    requestId: string;
    trackingCode: string;
    requesterPhone: string;
    medicineName: string;
    quantity: string;
    urgency: UrgencyLevel;
    location: Location;
    slaDeadline: string;
  };
}

// Match Events
interface MatchFoundEvent extends BaseEvent {
  eventType: 'MATCH_FOUND';
  payload: {
    matchId: string;
    donationId: string;
    donationTrackingCode: string;
    requestId: string;
    requestTrackingCode: string;
    partnerId: string;
    partnerName: string;
    matchScore: number;
    donorPhone: string;
    requesterPhone: string;
  };
}

interface MatchFulfilledEvent extends BaseEvent {
  eventType: 'MATCH_FULFILLED';
  payload: {
    matchId: string;
    donorPhone: string;
    requesterPhone: string;
    medicineName: string;
  };
}
```

### 8.3 Kafka Topics

| Topic | Producers | Consumers | Retention |
|-------|-----------|-----------|-----------|
| `donations` | Donation Service | Matching Engine, Notification Service | 7 days |
| `requests` | Request Service | Matching Engine, Notification Service | 7 days |
| `matches` | Matching Engine | Donation Service, Request Service, Notification Service | 30 days |
| `notifications` | All services | Notification Service | 3 days |

---

## 9. Matching Algorithm

### 9.1 Overview

The Matching Engine is the **core domain** of Zaed. It's responsible for finding the best donation-request pairs based on multiple factors.

### 9.2 Matching Criteria

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MATCHING SCORE FORMULA                                │
│                                                                              │
│   MatchScore = (NameSimilarity × 0.40)                                      │
│              + (ProximityScore × 0.30)                                       │
│              + (UrgencyBonus   × 0.20)                                       │
│              + (FreshnessScore × 0.10)                                       │
│                                                                              │
│   Score Range: 0 - 100                                                       │
│   Minimum Threshold: 60 (below this, no match is made)                      │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   1. NAME SIMILARITY (40% weight)                                           │
│   ────────────────────────────────                                          │
│   • Uses Levenshtein distance + tokenization                                │
│   • Handles brand names vs generic names                                    │
│   • Score: 0-100                                                            │
│                                                                              │
│   Examples:                                                                  │
│   • "Metformin 500mg" vs "Metformin 500mg" → 100                            │
│   • "Metformin 500mg" vs "Glucophage 500mg" → 85 (same drug)               │
│   • "Metformin 500mg" vs "Metformin 1000mg" → 70 (different dose)          │
│   • "Metformin" vs "Insulin" → 0 (completely different)                    │
│                                                                              │
│   2. PROXIMITY SCORE (30% weight)                                           │
│   ─────────────────────────────────                                         │
│   • Based on distance between donation and request locations                │
│   • Formula: 100 × (1 / (1 + distance_km / 10))                            │
│                                                                              │
│   Examples:                                                                  │
│   • Same area (0-2 km) → 90-100                                             │
│   • Same city (5 km) → 67                                                   │
│   • Nearby city (20 km) → 33                                                │
│   • Far (50+ km) → < 20                                                     │
│                                                                              │
│   3. URGENCY BONUS (20% weight)                                             │
│   ───────────────────────────────                                           │
│   • HIGH urgency requests get priority                                      │
│   • Score:                                                                   │
│     - HIGH: 100                                                             │
│     - MEDIUM: 50                                                            │
│     - LOW: 20                                                               │
│                                                                              │
│   4. FRESHNESS SCORE (10% weight)                                           │
│   ─────────────────────────────────                                         │
│   • Prefers donations with longer shelf life                                │
│   • Formula: 100 × (days_until_expiry / 180)                               │
│   • Capped at 100                                                           │
│                                                                              │
│   Examples:                                                                  │
│   • 6+ months until expiry → 100                                            │
│   • 3 months until expiry → 50                                              │
│   • 1 month until expiry → 17                                               │
│   • < 1 week until expiry → Donation excluded                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Matching Process

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MATCHING PROCESS                                     │
│                                                                              │
│   TRIGGER: New DonationVerified or RequestCreated event                     │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ STEP 1: FILTER CANDIDATES                                            │  │
│   │                                                                      │  │
│   │ If triggered by RequestCreated:                                      │  │
│   │   candidates = donations.where(                                      │  │
│   │     status == AVAILABLE &&                                           │  │
│   │     expiryDate > today + 7 days &&                                   │  │
│   │     distance(donation.location, request.location) < 50km             │  │
│   │   )                                                                  │  │
│   │                                                                      │  │
│   │ If triggered by DonationVerified:                                    │  │
│   │   candidates = requests.where(                                       │  │
│   │     status == PENDING &&                                             │  │
│   │     slaDeadline > now &&                                             │  │
│   │     distance(request.location, donation.location) < 50km             │  │
│   │   )                                                                  │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ STEP 2: SCORE EACH CANDIDATE                                         │  │
│   │                                                                      │  │
│   │ for each candidate:                                                  │  │
│   │   score = calculateMatchScore(donation, request)                     │  │
│   │   if score >= 60:                                                    │  │
│   │     potentialMatches.add({candidate, score})                         │  │
│   │                                                                      │  │
│   │ sort potentialMatches by score DESC                                  │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ STEP 3: SELECT OPTIMAL PARTNER                                       │  │
│   │                                                                      │  │
│   │ bestMatch = potentialMatches[0]                                      │  │
│   │                                                                      │  │
│   │ partner = findNearestPartner(                                        │  │
│   │   midpoint(donation.location, request.location),                     │  │
│   │   type = [PHARMACY, NGO],                                            │  │
│   │   isActive = true,                                                   │  │
│   │   hasCapacity = true                                                 │  │
│   │ )                                                                    │  │
│   │                                                                      │  │
│   │ if no partner found:                                                 │  │
│   │   schedule retry in 1 hour                                           │  │
│   │   return                                                             │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ STEP 4: CREATE MATCH                                                 │  │
│   │                                                                      │  │
│   │ transaction:                                                         │  │
│   │   donation.status = MATCHED                                          │  │
│   │   request.status = MATCHED                                           │  │
│   │   match = createMatch(donation, request, partner, score)             │  │
│   │                                                                      │  │
│   │ publish MatchFoundEvent                                              │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.4 Medicine Name Matching (Future: ML)

For MVP, use simple text matching. Future enhancement with ML:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MEDICINE NAME MATCHING                                    │
│                                                                              │
│   MVP (Phase 1):                                                            │
│   • Levenshtein distance                                                    │
│   • Lowercase + trim                                                        │
│   • Remove common words ("tablet", "capsule", "mg")                        │
│                                                                              │
│   Enhanced (Phase 2):                                                        │
│   • Medicine synonym dictionary (brand → generic mapping)                   │
│   • Egyptian medicine database integration                                  │
│                                                                              │
│   ML-Based (Phase 3):                                                        │
│   • Word embeddings for medicine names                                      │
│   • Train on historical matches                                             │
│   • Handle Arabic medicine names                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Database Schema

### 10.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DATABASE SCHEMA (PostgreSQL)                         │
│                                                                              │
│  ┌──────────────┐         ┌──────────────┐         ┌──────────────┐        │
│  │    users     │         │  donations   │         │   requests   │        │
│  ├──────────────┤         ├──────────────┤         ├──────────────┤        │
│  │ id (PK)      │◄───────┐│ id (PK)      │         │ id (PK)      │        │
│  │ phone        │        ││ tracking_code│         │ tracking_code│        │
│  │ email        │        ││ donor_phone  │         │ req_phone    │        │
│  │ name         │        ││ donor_user_id│─────────│ req_user_id  │───┐    │
│  │ role         │        ││ (FK → users) │         │ (FK → users) │   │    │
│  │ is_verified  │        │├──────────────┤         ├──────────────┤   │    │
│  │ created_at   │        ││ medicine_name│         │ medicine_name│   │    │
│  └──────────────┘        ││ quantity     │         │ quantity     │   │    │
│         │                ││ expiry_date  │         │ urgency      │   │    │
│         │                ││ notes        │         │ max_wait_days│   │    │
│         │                ││ city         │         │ sla_deadline │   │    │
│         │                ││ area         │         │ notes        │   │    │
│         │                ││ lat, lng     │         │ city, area   │   │    │
│         │                ││ status       │         │ contact_meth │   │    │
│         │                ││ verified_at  │         │ status       │   │    │
│         │                ││ verified_by  │─────┐   │ created_at   │   │    │
│         │                ││ created_at   │     │   └──────┬───────┘   │    │
│         │                │└──────────────┘     │          │           │    │
│         │                │        │            │          │           │    │
│         │                │        │            │          │           │    │
│  ┌──────▼───────┐        │  ┌─────▼──────┐     │    ┌─────▼─────┐    │    │
│  │   partners   │        │  │  donation_ │     │    │  matches  │    │    │
│  ├──────────────┤        │  │   images   │     │    ├───────────┤    │    │
│  │ id (PK)      │◄───────┼──├────────────┤     │    │ id (PK)   │    │    │
│  │ user_id (FK) │────────┘  │ id (PK)    │     │    │ donation_ │────┘    │
│  │ name         │           │ donation_id│─────┼────│ id (FK)   │         │
│  │ organization │           │ url        │     │    │ request_  │─────────┘
│  │ type         │           │ uploaded_at│     │    │ id (FK)   │
│  │ email, phone │           └────────────┘     │    │ partner_  │
│  │ city, area   │                              │    │ id (FK)   │─────────┐
│  │ lat, lng     │                              │    │ match_    │         │
│  │ service_rad  │                              │    │ score     │         │
│  │ is_active    │                              │    │ status    │         │
│  │ is_verified  │◄─────────────────────────────┘    │ matched_at│         │
│  │ capabilities │                                   │ fulfilled_│         │
│  │ max_capacity │                                   │ at        │         │
│  │ total_handled│                                   │ rating    │         │
│  │ avg_rating   │◄──────────────────────────────────│ feedback  │         │
│  └──────────────┘                                   └───────────┘         │
│         ▲                                                                  │
│         └──────────────────────────────────────────────────────────────────┘
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.2 SQL Schema

```sql
-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'DONOR',
    preferred_city VARCHAR(100),
    preferred_area VARCHAR(100),
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_email ON users(email);

-- Donations table
CREATE TABLE donations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_code VARCHAR(20) UNIQUE NOT NULL,

    -- Donor info
    donor_phone VARCHAR(20) NOT NULL,
    donor_name VARCHAR(255),
    donor_user_id UUID REFERENCES users(id),

    -- Medicine details
    medicine_name VARCHAR(255) NOT NULL,
    quantity VARCHAR(100) NOT NULL,
    expiry_date DATE NOT NULL,
    notes TEXT,

    -- Location
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by UUID REFERENCES users(id),
    rejection_reason VARCHAR(50),
    rejection_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_donations_tracking ON donations(tracking_code);
CREATE INDEX idx_donations_status ON donations(status);
CREATE INDEX idx_donations_location ON donations(city, area);
CREATE INDEX idx_donations_created ON donations(created_at DESC);

-- Donation images table
CREATE TABLE donation_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    donation_id UUID NOT NULL REFERENCES donations(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_donation_images_donation ON donation_images(donation_id);

-- Requests table
CREATE TABLE requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_code VARCHAR(20) UNIQUE NOT NULL,

    -- Requester info
    requester_phone VARCHAR(20) NOT NULL,
    requester_name VARCHAR(255),
    requester_user_id UUID REFERENCES users(id),

    -- Medicine details
    medicine_name VARCHAR(255) NOT NULL,
    quantity VARCHAR(100) NOT NULL,
    notes TEXT,

    -- Urgency
    urgency VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    max_wait_days INTEGER NOT NULL DEFAULT 3,
    sla_deadline TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Location & Contact
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    contact_method VARCHAR(50) NOT NULL DEFAULT 'WhatsApp',

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_requests_tracking ON requests(tracking_code);
CREATE INDEX idx_requests_status ON requests(status);
CREATE INDEX idx_requests_urgency ON requests(urgency);
CREATE INDEX idx_requests_sla ON requests(sla_deadline);
CREATE INDEX idx_requests_location ON requests(city, area);

-- Partners table
CREATE TABLE partners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),

    -- Organization details
    name VARCHAR(255) NOT NULL,
    organization VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,

    -- Contact
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,

    -- Location
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    service_radius INTEGER DEFAULT 10,

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,

    -- Capabilities
    capabilities TEXT[], -- e.g., ['cold_storage', 'controlled_substances']
    max_capacity INTEGER DEFAULT 50,

    -- Stats
    total_handled INTEGER DEFAULT 0,
    average_rating DECIMAL(3, 2) DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_partners_type ON partners(type);
CREATE INDEX idx_partners_location ON partners(city, area);
CREATE INDEX idx_partners_active ON partners(is_active, is_verified);

-- Matches table
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- References
    donation_id UUID NOT NULL REFERENCES donations(id),
    request_id UUID NOT NULL REFERENCES requests(id),
    partner_id UUID NOT NULL REFERENCES partners(id),

    -- Match details
    match_score INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_DONOR_DROP',

    -- Tracking timestamps
    matched_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    donor_dropped_at TIMESTAMP WITH TIME ZONE,
    picked_up_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,

    -- Feedback
    recipient_rating INTEGER CHECK (recipient_rating >= 1 AND recipient_rating <= 5),
    recipient_feedback TEXT,

    -- Notes
    notes TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Constraints
    UNIQUE(donation_id),  -- One donation can only be matched once
    UNIQUE(request_id)    -- One request can only be matched once
);

CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_matches_partner ON matches(partner_id);
CREATE INDEX idx_matches_created ON matches(created_at DESC);

-- OTP table (for phone verification)
CREATE TABLE otp_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(6) NOT NULL,
    reference_type VARCHAR(20) NOT NULL, -- 'DONATION', 'REQUEST'
    reference_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_otp_phone ON otp_codes(phone, expires_at);

-- Notifications log table
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_phone VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL, -- 'SMS', 'WHATSAPP', 'EMAIL'
    template VARCHAR(50) NOT NULL,
    variables JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
```

---

## 11. Technology Stack

> **Decision: Java 21 + Spring Boot 4 + Spring Security 6**

### 11.1 Recommended Stack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       TECHNOLOGY STACK                                       │
│                                                                              │
│  FRONTEND (Current - Keep)                                                   │
│  ├── Angular 20                                                             │
│  ├── TailwindCSS 4.x                                                        │
│  ├── RxJS                                                                   │
│  └── TypeScript 5.8                                                         │
│                                                                              │
│  BACKEND ⭐ DECIDED                                                          │
│  ├── Language: Java 21 (LTS)                                                │
│  │   └─ WHY: Strong typing, mature ecosystem, virtual threads support      │
│  │                                                                          │
│  ├── Framework: Spring Boot 4.x + Spring Framework 7                       │
│  │   ├── Spring Web (REST APIs with virtual threads)                       │
│  │   ├── Spring Data JPA 4.x (PostgreSQL)                                  │
│  │   ├── Spring Security 6.x (JWT, OAuth2 Resource Server)                 │
│  │   ├── Spring Cloud 2024.x (service discovery, config)                   │
│  │   └── Spring Kafka 3.x (event streaming)                                │
│  │                                                                          │
│  ├── Key Spring Boot 4 Features:                                            │
│  │   ├── Virtual threads by default (Project Loom)                         │
│  │   ├── GraalVM native image support                                      │
│  │   ├── Improved observability (Micrometer 2.0)                           │
│  │   └── Jakarta EE 11 compatibility                                       │
│  │                                                                          │
│  DATABASE                                                                    │
│  ├── PostgreSQL 16 (primary data store)                                     │
│  ├── Redis 7 (caching, sessions, rate limiting, OTP storage)               │
│  └── S3 / MinIO (image storage)                                             │
│                                                                              │
│  MESSAGING                                                                   │
│  ├── Apache Kafka 3.x (event streaming)                                    │
│  └── Alternative: RabbitMQ 3.x (simpler setup)                             │
│                                                                              │
│  NOTIFICATIONS                                                               │
│  ├── Twilio (SMS + WhatsApp Business API)                                  │
│  └── SendGrid (Email)                                                       │
│                                                                              │
│  INFRASTRUCTURE                                                              │
│  ├── Docker + Kubernetes (EKS)                                             │
│  ├── Kong / AWS API Gateway                                                 │
│  ├── Prometheus + Grafana (monitoring)                                      │
│  └── Jaeger / AWS X-Ray (distributed tracing)                              │
│                                                                              │
│  CI/CD                                                                       │
│  ├── GitHub Actions                                                         │
│  └── ArgoCD (GitOps for K8s)                                                │
│                                                                              │
│  AUTH (Phase 1)                                                              │
│  ├── Spring Security 6 (custom implementation)                             │
│  ├── Twilio Verify (Phone OTP)                                             │
│  └── java-otp library (TOTP for 2FA)                                       │
│                                                                              │
│  AUTH (Phase 2 - Future)                                                     │
│  └── Keycloak 24+ (when enterprise features needed)                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.2 Why Java 21 + Spring Boot 4?

| Criteria | Java/Spring | Node.js/NestJS |
|----------|-------------|----------------|
| Type Safety | Excellent | Good (TypeScript) |
| Ecosystem | Mature, extensive | Growing |
| Performance | Very good | Good |
| Hiring (Egypt) | Larger talent pool | Smaller pool |
| Enterprise Ready | Yes | Yes |
| Learning Curve | Steeper | Gentler |

**Recommendation**: Java/Spring Boot for backend services, but NestJS is a valid alternative if the team is more comfortable with TypeScript.

---

## 12. Deployment Architecture

### 12.1 Production Architecture (AWS)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION DEPLOYMENT (AWS)                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       Route 53 (DNS)                                 │   │
│  │                    zaed.org → CloudFront                             │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                         │
│  ┌────────────────────────────────▼────────────────────────────────────┐   │
│  │                     CloudFront (CDN)                                 │   │
│  │              Angular SPA + Static Assets from S3                     │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                         │
│  ┌────────────────────────────────▼────────────────────────────────────┐   │
│  │                Application Load Balancer                             │   │
│  │                    /api/* → EKS Cluster                             │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                         │
│  ┌────────────────────────────────▼────────────────────────────────────┐   │
│  │                      EKS Cluster (Kubernetes)                        │   │
│  │                                                                      │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │   Kong      │ │  Identity   │ │  Donation   │ │  Request    │   │   │
│  │  │   Gateway   │ │   Service   │ │  Service    │ │  Service    │   │   │
│  │  │   (2 pods)  │ │   (2 pods)  │ │  (3 pods)   │ │  (3 pods)   │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  │                                                                      │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                   │   │
│  │  │  Matching   │ │  Partner    │ │Notification │                   │   │
│  │  │  Engine     │ │  Service    │ │  Service    │                   │   │
│  │  │  (3 pods)   │ │  (2 pods)   │ │  (2 pods)   │                   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                   │   │
│  │                                                                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                   │                                         │
│         ┌─────────────────────────┼─────────────────────────┐              │
│         │                         │                         │              │
│         ▼                         ▼                         ▼              │
│  ┌─────────────┐          ┌─────────────┐          ┌─────────────┐        │
│  │ Amazon RDS  │          │ Amazon MSK  │          │ ElastiCache │        │
│  │ PostgreSQL  │          │   (Kafka)   │          │   (Redis)   │        │
│  │ (Multi-AZ)  │          │             │          │   Cluster   │        │
│  └─────────────┘          └─────────────┘          └─────────────┘        │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                              S3 Bucket                               │  │
│  │                    Medicine Images + Static Assets                   │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 12.2 Development Environment

```yaml
# docker-compose.yml for local development
version: '3.8'

services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: zaed
      POSTGRES_USER: zaed
      POSTGRES_PASSWORD: zaed_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  minio:
    image: minio/minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"

volumes:
  postgres_data:
```

---

## 13. Security

### 13.1 Security Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SECURITY ARCHITECTURE                                 │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 1: NETWORK SECURITY                                            │   │
│  │ • WAF (Web Application Firewall) - AWS WAF                           │   │
│  │ • DDoS protection - AWS Shield                                       │   │
│  │ • VPC isolation for internal services                                │   │
│  │ • Private subnets for databases                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 2: API GATEWAY SECURITY                                        │   │
│  │ • Rate limiting (100 req/min for anonymous, 1000 for authenticated) │   │
│  │ • Request validation                                                 │   │
│  │ • IP blocking for abuse                                              │   │
│  │ • CORS configuration                                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 3: AUTHENTICATION                                              │   │
│  │ • Phone OTP for donors/requesters (expires in 5 min)                │   │
│  │ • JWT tokens for partners/admins (1 hour expiry)                    │   │
│  │ • Refresh token rotation                                             │   │
│  │ • 2FA for admin accounts (TOTP)                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 4: AUTHORIZATION                                               │   │
│  │ • Role-based access control (RBAC)                                   │   │
│  │ • Resource-level permissions (own data only)                         │   │
│  │ • Admin audit logging                                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 5: DATA SECURITY                                               │   │
│  │ • Encryption at rest (AES-256)                                       │   │
│  │ • Encryption in transit (TLS 1.3)                                    │   │
│  │ • PII masking in logs                                                │   │
│  │ • Database connection encryption                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ LAYER 6: MEDICINE SAFETY                                             │   │
│  │ • Photo verification before listing                                  │   │
│  │ • Expiry date validation (>7 days remaining)                        │   │
│  │ • Partner verification (pharmacies licensed)                         │   │
│  │ • Full audit trail for all medicine movements                        │   │
│  │ • Controlled substance restrictions                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 13.2 JWT Token Structure

```json
// Access Token Payload (Partner/Admin)
{
  "sub": "uuid",                    // User ID
  "type": "access",
  "role": "PARTNER_PHARMACY",
  "partnerId": "uuid",              // For partners
  "permissions": ["matches:view", "matches:update"],
  "iat": 1707134400,
  "exp": 1707138000                 // 1 hour
}

// Temporary Token Payload (Donor/Requester after OTP)
{
  "sub": "phone:+201234567890",
  "type": "temp",
  "referenceType": "DONATION",
  "referenceId": "uuid",
  "iat": 1707134400,
  "exp": 1707135300                 // 15 minutes (for image upload)
}
```

---

## 14. Frontend Pages & Routes

### 14.1 Route Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FRONTEND ROUTES                                      │
│                                                                              │
│  PUBLIC ROUTES (No Auth)                                                     │
│  ────────────────────────                                                    │
│  /                        → Home page                                        │
│  /donate                  → Donation form (multi-step)                       │
│  /request                 → Request form (multi-step)                        │
│  /how-it-works            → How the platform works                          │
│  /partners                → Partner information & registration              │
│  /track/:code             → Track donation/request status    [NEW]          │
│                                                                              │
│  PARTNER ROUTES (Partner Auth Required)                                      │
│  ──────────────────────────────────────                                      │
│  /partner/login           → Partner login page               [NEW]          │
│  /partner/register        → Partner application form         [NEW]          │
│  /partner/dashboard       → Partner dashboard                [NEW]          │
│  /partner/pickups         → Pending pickups list             [NEW]          │
│  /partner/history         → Completed handoffs               [NEW]          │
│                                                                              │
│  ADMIN ROUTES (Admin Auth + 2FA Required)                                   │
│  ────────────────────────────────────────                                   │
│  /admin/login             → Admin login with 2FA             [NEW]          │
│  /admin/dashboard         → Admin overview                   [NEW]          │
│  /admin/donations         → Pending verifications            [NEW]          │
│  /admin/partners          → Partner management               [NEW]          │
│  /admin/matches           → All matches                      [NEW]          │
│  /admin/reports           → Analytics & reports              [NEW]          │
│                                                                              │
│  UTILITY ROUTES                                                              │
│  ──────────────────                                                          │
│  /404                     → Not found page                                   │
│  /privacy                 → Privacy policy                                   │
│  /terms                   → Terms of service                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 14.2 New Pages to Build

| Priority | Page | Description | Auth |
|----------|------|-------------|------|
| **P0** | `/track/:code` | Public tracking page for donations/requests | None (just code) |
| **P0** | `/partner/login` | Partner email/password login | None |
| **P0** | `/partner/dashboard` | Partner home with pending items | Partner JWT |
| **P1** | `/admin/login` | Admin login with 2FA | None |
| **P1** | `/admin/dashboard` | Admin overview | Admin JWT |
| **P1** | `/admin/donations` | Verify/reject donations | Admin JWT |
| **P2** | `/partner/register` | Partner application form | None |
| **P2** | `/admin/partners` | Manage partners | Admin JWT |
| **P2** | `/admin/reports` | Analytics | Admin JWT |

---

## 15. Development Roadmap

### 15.1 Phase Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DEVELOPMENT PHASES                                    │
│                                                                              │
│  PHASE 1: MVP                                                               │
│  ─────────────────                                                           │
│  Goal: Basic donation-request-match flow working                            │
│                                                                              │
│  Backend:                                                                    │
│  □ Identity Service (phone OTP auth)                                        │
│  □ Donation Service (CRUD + image upload)                                   │
│  □ Request Service (CRUD)                                                   │
│  □ Simple Matching (exact name, same city)                                  │
│  □ Basic Notification (SMS via Twilio)                                      │
│                                                                              │
│  Frontend:                                                                   │
│  □ Update donate form (add phone, OTP)                                      │
│  □ Update request form (add phone, OTP)                                     │
│  □ Build /track/:code page                                                  │
│  □ Build basic admin panel (verify donations)                               │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────────│
│                                                                              │
│  PHASE 2: Partners & Smart Matching                                         │
│  ────────────────────────────────────                                        │
│  Goal: Partner network operational, smart matching                          │
│                                                                              │
│  Backend:                                                                    │
│  □ Partner Service (registration, verification)                             │
│  □ Enhanced Matching Engine (scoring algorithm)                             │
│  □ WhatsApp integration                                                     │
│  □ Partner dashboard API                                                    │
│                                                                              │
│  Frontend:                                                                   │
│  □ Partner login & dashboard                                                │
│  □ Partner registration flow                                                │
│  □ Enhanced admin panel (partner management)                                │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────────│
│                                                                              │
│  PHASE 3: Scale & Analytics                                                 │
│  ───────────────────────────                                                 │
│  Goal: Production-ready, analytics, optimization                            │
│                                                                              │
│  □ Medicine name ML matching                                                │
│  □ Demand forecasting                                                       │
│  □ Partner API for inventory systems                                        │
│  □ Mobile apps (React Native)                                               │
│  □ Advanced analytics dashboard                                             │
│  □ Multi-language support (Arabic)                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 15.2 Phase 1 Task Breakdown

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PHASE 1: MVP TASKS                                       │
│                                                                              │
│  WEEK 1-2: Backend Foundation                                               │
│  ─────────────────────────────────                                          │
│  □ Set up Spring Boot project structure                                     │
│  □ Configure PostgreSQL + Flyway migrations                                 │
│  □ Implement Identity Service                                               │
│    □ Phone + OTP endpoints                                                  │
│    □ Twilio SMS integration                                                 │
│  □ Set up Docker Compose for local dev                                      │
│                                                                              │
│  WEEK 3-4: Core Services                                                    │
│  ───────────────────────────                                                │
│  □ Implement Donation Service                                               │
│    □ CRUD endpoints                                                         │
│    □ S3 image upload                                                        │
│    □ Tracking code generation                                               │
│  □ Implement Request Service                                                │
│    □ CRUD endpoints                                                         │
│    □ SLA calculation                                                        │
│  □ Basic Matching Engine                                                    │
│    □ Simple name matching                                                   │
│    □ Same-city filtering                                                    │
│                                                                              │
│  WEEK 5-6: Frontend Updates                                                 │
│  ─────────────────────────────                                              │
│  □ Update Donate page                                                       │
│    □ Add phone input                                                        │
│    □ Add OTP verification step                                              │
│    □ Show tracking code on success                                          │
│  □ Update Request page (same as above)                                      │
│  □ Build Track page (/track/:code)                                          │
│    □ Status timeline                                                        │
│    □ Match details                                                          │
│                                                                              │
│  WEEK 7-8: Admin & Polish                                                   │
│  ─────────────────────────────                                              │
│  □ Build Admin login                                                        │
│  □ Build Admin donations list                                               │
│    □ View pending donations                                                 │
│    □ Verify/reject actions                                                  │
│  □ Notification Service                                                     │
│    □ Send SMS on key events                                                 │
│  □ Testing & bug fixes                                                      │
│  □ Deploy to staging                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Donor** | Person donating unused medicine |
| **Requester** | Person needing medicine |
| **Partner** | Pharmacy, NGO, or volunteer that handles physical logistics |
| **Match** | A pairing between a donation and a request |
| **Tracking Code** | Unique identifier for donations (DON-XXXXX) and requests (REQ-XXXXX) |
| **SLA** | Service Level Agreement - the deadline for fulfilling a request based on urgency |
| **Matching Engine** | Core algorithm that finds optimal donation-request pairs |

---

## Appendix B: Contact

For questions about this architecture document, contact:

- **Technical Lead**: [TBD]
- **Product Owner**: [TBD]
- **Repository**: [GitHub URL]

---

*This document is a living specification and will be updated as the system evolves.*
