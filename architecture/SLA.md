# Zaed Med Connect - Service Level Agreement (SLA)

> **Version:** 1.0
> **Created:** February 2026
> **Last Updated:** February 2026
> **Status:** Approved for Implementation

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Decisions](#2-architecture-decisions)
3. [Technology Stack](#3-technology-stack)
4. [Authentication & Authorization SLA](#4-authentication--authorization-sla)
5. [SMS Gateway SLA](#5-sms-gateway-sla)
6. [Medicine Matching Engine SLA](#6-medicine-matching-engine-sla)
7. [Arabic Language Support SLA](#7-arabic-language-support-sla)
8. [Performance SLA](#8-performance-sla)
9. [Cost Projections](#9-cost-projections)
10. [Implementation Checklist](#10-implementation-checklist)

---

## 1. Executive Summary

### 1.1 Purpose

This document defines the Service Level Agreement for Zaed Med Connect, a medicine donation coordination platform for Egypt. It captures all technical decisions, requirements, and commitments agreed upon during the architecture planning phase.

### 1.2 Key Decisions Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KEY ARCHITECTURE DECISIONS                           │
│                                                                              │
│  ARCHITECTURE:      Microservices (for team skill development)              │
│  BACKEND:           Java 21 + Spring Boot 4 + Spring Framework 7            │
│  AUTHENTICATION:    Phone-first (OTP) for Egypt market                      │
│  SMS PROVIDER:      Twilio (MVP) → SMS Misr (Production scale)              │
│  LANGUAGE:          Full Arabic support (SMS + Medicine Matching)           │
│  MATCHING:          Levenshtein ONLY (no brand/generic substitution)        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Stakeholder Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Product Owner | | | |
| Tech Lead | | | |
| Backend Lead | | | |
| DevOps Lead | | | |

---

## 2. Architecture Decisions

### 2.1 Microservices Architecture

**Decision:** Implement microservices architecture regardless of initial traffic volume.

**Rationale:**
- Production project with real stakes = real learning opportunity
- Team leveling up on microservices practices
- Conscious decision (not hype-driven)

**Trade-offs Accepted:**
- Higher initial complexity
- More infrastructure to manage
- Increased deployment complexity

**Commitment:**
- Implement microservices correctly with proper service boundaries
- Each service owns its data
- Async communication via message broker (Kafka/RabbitMQ)

### 2.2 Service Decomposition

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MICROSERVICES MAP                                    │
│                                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│   │   IDENTITY   │  │   DONATION   │  │   REQUEST    │  │   MATCHING   │   │
│   │   SERVICE    │  │   SERVICE    │  │   SERVICE    │  │    ENGINE    │   │
│   │              │  │              │  │              │  │    (CORE)    │   │
│   │ • OTP        │  │ • CRUD       │  │ • CRUD       │  │ • Algorithm  │   │
│   │ • JWT        │  │ • Images     │  │ • SLA        │  │ • Scoring    │   │
│   │ • 2FA        │  │ • Verify     │  │ • Urgency    │  │ • Arabic     │   │
│   │              │  │              │  │              │  │              │   │
│   │ Port: 8081   │  │ Port: 8082   │  │ Port: 8083   │  │ Port: 8084   │   │
│   └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│                                                                              │
│   ┌──────────────┐  ┌──────────────┐                                        │
│   │   PARTNER    │  │ NOTIFICATION │                                        │
│   │   SERVICE    │  │   SERVICE    │                                        │
│   │              │  │              │                                        │
│   │ • Dashboard  │  │ • SMS        │                                        │
│   │ • Pickup     │  │ • WhatsApp   │                                        │
│   │ • Delivery   │  │ • Arabic     │                                        │
│   │              │  │              │                                        │
│   │ Port: 8085   │  │ Port: 8086   │                                        │
│   └──────────────┘  └──────────────┘                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

### 3.1 Core Technologies

| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| **Language** | Java | 21 (LTS) | Virtual Threads support |
| **Framework** | Spring Boot | 4.0.x | Released Nov 2025 |
| **Framework** | Spring Framework | 7.0.x | Released Nov 2025 |
| **Security** | Spring Security | 7.x | MFA built-in |
| **Database** | PostgreSQL | 16+ | Primary data store |
| **Cache** | Redis | 7+ | OTP storage, sessions |
| **Message Broker** | Kafka/RabbitMQ | TBD | Event-driven communication |
| **Search** | PostgreSQL FTS | - | Medicine name search |

### 3.2 Verified Versions (Web Search Confirmed)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VERIFIED TECHNOLOGY VERSIONS                              │
│                    (Confirmed via spring.io - February 2026)                │
│                                                                              │
│  Spring Boot 4.0.0        Released: November 20, 2025                       │
│  Spring Boot 4.0.2        Current stable (as of Jan 2026)                   │
│                                                                              │
│  Spring Framework 7.0.0   Released: November 13, 2025                       │
│  Spring Framework 7.0.2   Current stable (as of Dec 2025)                   │
│                                                                              │
│  Sources:                                                                    │
│  • https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now       │
│  • https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Authentication & Authorization SLA

### 4.1 Authentication Strategy

**Decision:** Phone-first authentication for Egyptian market.

**Rationale:**
- 90%+ WhatsApp penetration in Egypt
- Phone verification feels more legitimate than email
- No password to remember = more donations
- Can send updates directly via WhatsApp/SMS
- Works for users with low tech literacy

### 4.2 User Type Authentication Matrix

| User Type | Auth Method | Token Type | Expiry | 2FA Required |
|-----------|-------------|------------|--------|--------------|
| **Donor** | Phone OTP | Temp JWT | 15 min | No |
| **Requester** | Phone OTP | Temp JWT | 15 min | No |
| **Partner** | Email + Password | Access + Refresh | 1h / 7d | No |
| **Admin** | Email + Password + TOTP | Access + Refresh | 1h / 7d | **Yes** |

### 4.3 OTP SLA Commitments

| Metric | Target | Maximum |
|--------|--------|---------|
| OTP Delivery Time | < 10 seconds | 30 seconds |
| OTP Length | 6 digits | - |
| OTP Expiry | 5 minutes | - |
| Max Attempts per OTP | 3 | - |
| Rate Limit (per phone) | 3 OTPs/hour | - |
| Rate Limit (per IP) | 10 OTPs/hour | - |

### 4.4 JWT Token Specifications

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         JWT TOKEN SPECIFICATIONS                             │
│                                                                              │
│  ACCESS TOKEN (Partner/Admin)                                               │
│  ────────────────────────────                                               │
│  {                                                                           │
│    "sub": "user-uuid",                                                      │
│    "type": "access",                                                        │
│    "role": "PARTNER_PHARMACY",                                              │
│    "permissions": ["MATCH_VIEW_ASSIGNED", "MATCH_CONFIRM_PICKUP"],          │
│    "partnerId": "partner-uuid",                                             │
│    "iss": "zaed.org",                                                       │
│    "exp": 3600                          // 1 hour                           │
│  }                                                                           │
│                                                                              │
│  TEMP TOKEN (OTP-verified Donor/Requester)                                  │
│  ─────────────────────────────────────────                                  │
│  {                                                                           │
│    "sub": "phone:+201234567890",                                            │
│    "type": "temp",                                                          │
│    "context": "DONATION",                                                   │
│    "referenceId": "donation-uuid",                                          │
│    "trackingCode": "DON-ABC123",                                            │
│    "permissions": ["DONATION_UPLOAD_IMAGE"],                                │
│    "exp": 900                           // 15 minutes                       │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Security Requirements

| Requirement | Implementation |
|-------------|----------------|
| Password Hashing | BCrypt, cost factor 12 |
| Password Policy | Min 8 chars, 1 uppercase, 1 number |
| Account Lockout | 10 failed attempts → 30 min lock |
| TOTP Window | 30 seconds, ±1 for clock drift |
| Refresh Token | Stored in DB, rotated on use |
| HTTPS | Required for all endpoints |
| CORS | Whitelist specific domains only |

---

## 5. SMS Gateway SLA

### 5.1 Gateway Strategy

**Decision:** Start with Twilio, migrate to local provider at scale.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SMS GATEWAY MIGRATION PATH                                │
│                                                                              │
│  PHASE 1: MVP                                                               │
│  ────────────────                                                           │
│  • Provider: Twilio                                                         │
│  • Reason: Fastest setup (~2 hours)                                         │
│  • Cost: ~$0.40/SMS to Egypt                                                │
│  • Includes: WhatsApp Business API                                          │
│                                                                              │
│  PHASE 2: Production Scale (5,000+ SMS/month)                               │
│  ────────────────────────────────────────────                               │
│  • Provider: SMS Misr (or similar local)                                    │
│  • Reason: 87% cost reduction                                               │
│  • Cost: ~$0.02-0.05/SMS                                                    │
│  • Requires: NTRA registration                                              │
│                                                                              │
│  IMPLEMENTATION:                                                             │
│  • Abstract gateway interface (SmsGateway)                                  │
│  • Switch via Spring profile: spring.profiles.active=smsmisr                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 SMS Cost Projections

**Assumptions:**
- 500 matches/month (target)
- 6 SMS per match (OTP + notifications)
- 10% retry rate
- 80% Arabic messages (2 segments due to 70 char limit)

| Provider | Cost/SMS | 3,500 SMS/mo | 10,000 SMS/mo | 50,000 SMS/mo |
|----------|----------|--------------|---------------|---------------|
| **Twilio** | $0.3959 | $1,386 | $3,959 | $19,795 |
| **Twilio + Arabic** | $0.3959 × 1.8 | **$2,494** | **$7,126** | **$35,631** |
| **SMS Misr** | ~$0.035 | $122 | $350 | $1,750 |
| **SMS Misr + Arabic** | ~$0.035 × 1.8 | **$220** | **$630** | **$3,150** |

### 5.3 Arabic SMS Requirements

**Decision:** Full Arabic SMS support required.

**Technical Implications:**

| Factor | English (GSM-7) | Arabic (Unicode) |
|--------|-----------------|------------------|
| Characters per SMS | 160 | **70** |
| Multi-part threshold | 153/segment | **67/segment** |
| Cost multiplier | 1x | **~2x** |

**Mitigation:** All Arabic templates MUST be under 70 characters.

### 5.4 Arabic SMS Templates

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ARABIC SMS TEMPLATES (Under 70 chars)                     │
│                                                                              │
│  OTP:                                                                        │
│  "زائد: رمز التحقق الخاص بك هو %s"                    (52 chars) ✓          │
│                                                                              │
│  DONATION RECEIVED:                                                          │
│  "تم استلام تبرعك. كود التتبع: %s"                    (45 chars) ✓          │
│                                                                              │
│  DONATION VERIFIED:                                                          │
│  "تم التحقق من تبرعك وهو متاح الآن للتوزيع"           (42 chars) ✓          │
│                                                                              │
│  MATCH FOUND (Donor):                                                        │
│  "تم مطابقة تبرعك! يرجى التوصيل إلى: %s"              (65 chars) ✓          │
│                                                                              │
│  MATCH FOUND (Requester):                                                    │
│  "تم العثور على دوائك! استلم من: %s"                  (58 chars) ✓          │
│                                                                              │
│  READY FOR PICKUP:                                                           │
│  "دواؤك جاهز للاستلام من: %s"                         (45 chars) ✓          │
│                                                                              │
│  DELIVERY CONFIRMED:                                                         │
│  "تم التسليم بنجاح. شكراً لاستخدام زائد"              (35 chars) ✓          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.5 NTRA Registration Requirements

| Requirement | Details | Timeline |
|-------------|---------|----------|
| Sender ID | "ZAED" approval required | 2-4 weeks |
| Business Registration | Egyptian commercial registration | Required |
| Template Approval | All SMS templates pre-approved | 1-2 weeks |
| Annual Renewal | Re-registration annually | Ongoing |

---

## 6. Medicine Matching Engine SLA

### 6.1 Matching Philosophy

**CRITICAL DECISION:** Levenshtein distance for typo correction ONLY.

**Rationale (Domain Knowledge):**
- Egyptian patients want their EXACT medicine
- Patients requesting "Panadol" want Panadol, NOT generic Paracetamol
- Giving alternatives (even same drug) may be rejected
- Trust > Optimization
- Better to have no match than wrong match

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MATCHING PHILOSOPHY                                       │
│                                                                              │
│  ✅ ALLOWED MATCHES:                                                        │
│  ─────────────────                                                          │
│  • "Panadol" → "Panadol"         (Exact match)                              │
│  • "Panadol" → "panadol"         (Case difference)                          │
│  • "Panadol" → "Panadoll"        (Typo - 1 char)                            │
│  • "بانادول" → "باندول"          (Arabic typo - 1 char)                     │
│                                                                              │
│  ❌ FORBIDDEN MATCHES:                                                      │
│  ──────────────────                                                         │
│  • "Panadol" → "Paracetamol"     (Same drug, different brand - REJECTED)   │
│  • "Brufen" → "Ibuprofen"        (Same drug, different brand - REJECTED)   │
│  • "بانادول" → "باراسيتامول"     (Same drug, different brand - REJECTED)   │
│                                                                              │
│  TRUST > OPTIMIZATION                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Matching Algorithm Specification

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Algorithm | Levenshtein Distance | Industry standard for typo detection |
| Max Distance | 2 characters | Catches typos without false positives |
| Minimum Score | 60/100 | Below this, no match is made |

### 6.3 Matching Score Formula

```
MatchScore = (NameSimilarity × 0.40)
           + (ProximityScore × 0.30)
           + (UrgencyBonus   × 0.20)
           + (FreshnessScore × 0.10)

Minimum Threshold: 60 (below this, no match)
```

| Component | Weight | Calculation |
|-----------|--------|-------------|
| **Name Similarity** | 40% | Levenshtein-based, 0-100 |
| **Proximity** | 30% | Distance-based, same city preferred |
| **Urgency** | 20% | HIGH=100, MEDIUM=50, LOW=20 |
| **Freshness** | 10% | Days until expiry / 180, capped at 100 |

### 6.4 Matching SLA Commitments

| Metric | Target | Maximum |
|--------|--------|---------|
| Average Match Time | < 24 hours | 72 hours |
| Match Accuracy | > 95% | - |
| False Positive Rate | < 1% | 2% |
| Algorithm Execution | < 500ms | 2 seconds |

---

## 7. Arabic Language Support SLA

### 7.1 Arabic Text Normalization

**Decision:** Full Arabic text normalization for medicine matching.

**Normalizations Applied:**

| Transformation | Example | Rationale |
|----------------|---------|-----------|
| Remove diacritics | بَانَادُول → بانادول | Users rarely type diacritics |
| Normalize Alef | أ إ آ → ا | All Alef variants treated equal |
| Normalize Yaa | ى → ي | Alef Maksura = Yaa for matching |
| Normalize Taa Marbuta | ة → ه | End-of-word equivalence |
| Convert numerals | ٥٠٠ → 500 | Arabic/Western numeral equivalence |

### 7.2 Arabic Normalization Examples

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ARABIC NORMALIZATION EXAMPLES                             │
│                                                                              │
│  INPUT                          NORMALIZED                                   │
│  ─────                          ──────────                                   │
│  "بَانَادُول ٥٠٠ مِلجِرَام"       "بانادول 500 ملجرام"                        │
│  "أوجمنتين"                     "اوجمنتين"                                   │
│  "جلوكوفاج ١٠٠٠"                "جلوكوفاج 1000"                              │
│  "لِيبِيتُور"                    "ليبيتور"                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Bilingual Database Requirements

```sql
-- Medicine table MUST store both Arabic and English
CREATE TABLE medicines (
    id UUID PRIMARY KEY,

    -- English (required)
    name_en VARCHAR(255) NOT NULL,
    name_en_normalized VARCHAR(255) NOT NULL,

    -- Arabic (required for Egyptian market)
    name_ar VARCHAR(255) NOT NULL,
    name_ar_normalized VARCHAR(255) NOT NULL,

    -- Aliases for common typos/variations
    aliases JSONB DEFAULT '[]',

    -- Generic name (for reference only, NOT for matching)
    generic_name_en VARCHAR(255),
    generic_name_ar VARCHAR(255)
);
```

### 7.4 Cross-Language Matching

| Scenario | Supported | Method |
|----------|-----------|--------|
| Arabic → Arabic | ✅ Yes | Direct Levenshtein |
| English → English | ✅ Yes | Direct Levenshtein |
| Arabic → English | ✅ Yes | Via medicine database lookup |
| English → Arabic | ✅ Yes | Via medicine database lookup |

---

## 8. Performance SLA

### 8.1 API Response Times

| Endpoint Category | Target P50 | Target P95 | Maximum |
|-------------------|------------|------------|---------|
| Public endpoints | 100ms | 300ms | 1s |
| Authenticated endpoints | 150ms | 400ms | 1.5s |
| Matching engine | 300ms | 800ms | 2s |
| File upload | 500ms | 2s | 5s |

### 8.2 Availability Targets

| Service | Target Uptime | Maximum Downtime/Month |
|---------|---------------|------------------------|
| API Gateway | 99.9% | 43 minutes |
| Identity Service | 99.9% | 43 minutes |
| Matching Engine | 99.5% | 3.6 hours |
| Notification Service | 99.0% | 7.2 hours |

### 8.3 Scalability Targets

| Metric | MVP Target | Growth Target |
|--------|------------|---------------|
| Concurrent Users | 100 | 1,000 |
| Requests/Second | 50 | 500 |
| Monthly Matches | 500 | 5,000 |
| Database Size | 10 GB | 100 GB |

---

## 9. Cost Projections

### 9.1 Monthly Infrastructure Costs (Estimated)

| Component | MVP | Growth | Scale |
|-----------|-----|--------|-------|
| **Cloud Infrastructure** | $200 | $500 | $1,500 |
| **Database (PostgreSQL)** | $50 | $150 | $400 |
| **Redis** | $30 | $80 | $200 |
| **SMS (Twilio)** | $2,500 | - | - |
| **SMS (Local)** | - | $600 | $3,000 |
| **Monitoring** | $50 | $100 | $300 |
| **TOTAL** | **$2,830** | **$1,430** | **$5,400** |

### 9.2 Cost Optimization Timeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COST OPTIMIZATION TIMELINE                                │
│                                                                              │
│  MONTH 1-3: MVP Launch                                                      │
│  • Twilio for SMS ($2,500/mo)                                               │
│  • Focus on functionality, not cost                                          │
│                                                                              │
│  MONTH 4-6: NTRA Registration                                               │
│  • Apply for sender ID registration                                         │
│  • Set up SMS Misr account                                                  │
│  • Run parallel testing                                                      │
│                                                                              │
│  MONTH 6+: Migration                                                        │
│  • Switch to local SMS provider                                             │
│  • Cost drops from $2,500 → $600/mo                                         │
│  • 76% savings on SMS                                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Implementation Checklist

### 10.1 Phase 1: Foundation (Weeks 1-4)

```
IDENTITY SERVICE
☐ Spring Security 7 configuration
☐ OTP generation and verification (Redis)
☐ JWT token service (access, refresh, temp)
☐ Phone number validation (Egyptian format)
☐ Rate limiting implementation
☐ 2FA/TOTP for admins

SMS GATEWAY
☐ SmsGateway interface (abstraction)
☐ TwilioSmsGateway implementation
☐ Arabic SMS templates (under 70 chars)
☐ Character counter and validator
☐ Cost tracking per message

DATABASE
☐ PostgreSQL setup with UTF8MB4
☐ Auth tables (users, credentials, tokens, audit)
☐ Medicine reference table (Arabic + English)
☐ Flyway migrations
```

### 10.2 Phase 2: Core Services (Weeks 5-8)

```
DONATION SERVICE
☐ Donation CRUD operations
☐ Image upload (S3/MinIO)
☐ Status workflow
☐ Event publishing (Kafka)

REQUEST SERVICE
☐ Request CRUD operations
☐ Urgency levels and SLA calculation
☐ Status workflow
☐ Event publishing

PARTNER SERVICE
☐ Partner management
☐ Dashboard endpoints
☐ Pickup/delivery confirmation
```

### 10.3 Phase 3: Matching Engine (Weeks 9-12)

```
MATCHING ENGINE
☐ Arabic text normalizer
☐ Levenshtein distance implementation
☐ Bilingual medicine matcher
☐ Scoring algorithm (name, proximity, urgency, freshness)
☐ Event-driven matching triggers
☐ Match creation and notification

NOTIFICATION SERVICE
☐ Event consumers (Kafka)
☐ SMS sending orchestration
☐ WhatsApp integration (if Twilio)
☐ Notification logging and cost tracking
```

### 10.4 Phase 4: Production Readiness (Weeks 13-16)

```
INFRASTRUCTURE
☐ Kubernetes deployment manifests
☐ CI/CD pipeline
☐ Monitoring (Prometheus/Grafana)
☐ Logging (ELK/Loki)
☐ Alerting rules

SECURITY
☐ NTRA sender ID registration
☐ SSL/TLS certificates
☐ Security audit
☐ Penetration testing

DOCUMENTATION
☐ API documentation (OpenAPI)
☐ Runbooks
☐ Incident response procedures
```

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **OTP** | One-Time Password (6-digit SMS code) |
| **JWT** | JSON Web Token (authentication token) |
| **TOTP** | Time-based One-Time Password (2FA) |
| **Levenshtein** | String similarity algorithm measuring edit distance |
| **NTRA** | National Telecom Regulatory Authority (Egypt) |
| **Tashkeel** | Arabic diacritical marks (فَتحة، ضَمة، كَسرة) |
| **GSM-7** | Standard SMS encoding (160 chars) |
| **Unicode/UCS-2** | SMS encoding for Arabic (70 chars) |

---

## Appendix B: Reference Documents

| Document | Location | Description |
|----------|----------|-------------|
| ARCHITECTURE.md | /architecture/ | System architecture overview |
| AUTH.md | /architecture/ | Authentication implementation guide |
| SLA.md | /architecture/ | This document |

---

## Appendix C: Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Feb 2026 | Team | Initial SLA document |

---

*This SLA document captures the technical decisions made during architecture planning. Any changes to these commitments require stakeholder approval.*
