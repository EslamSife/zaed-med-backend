# ADR-002: Phone-First Authentication for Egypt

## Status
Accepted

## Date
2026-02-05

## Context

Zaed Med Connect serves the Egyptian market, connecting medicine donors with recipients. We need to decide on the authentication strategy that balances:

1. **User friction**: Donors and requesters should face minimal barriers
2. **Security**: Partners and admins need proper access control
3. **Market fit**: Solution must work for Egyptian users
4. **Notification delivery**: Need reliable channel for status updates

### Egyptian Market Characteristics

| Factor | Data Point | Implication |
|--------|------------|-------------|
| WhatsApp penetration | 90%+ daily users | SMS/WhatsApp are trusted channels |
| Email usage | Lower than Western markets | Email-based auth has friction |
| Tech literacy | Variable, especially older donors | Simple flows essential |
| Phone ownership | Universal | Phone number is reliable identifier |
| Trust factors | Phone verification feels legitimate | OTP builds trust |

## Decision

We will implement a **phone-first authentication strategy** with different flows for different user types:

### Authentication Matrix

| User Type | Auth Method | Token Type | Expiry | Account Required |
|-----------|-------------|------------|--------|------------------|
| Donor | Phone OTP | Temp JWT | 15 min | No |
| Requester | Phone OTP | Temp JWT | 15 min | No |
| Partner | Email + Password | Access + Refresh | 1h / 7d | Yes |
| Admin | Email + Password + 2FA | Access + Refresh | 1h / 7d | Yes |

### Flow: Donors and Requesters

```
┌─────────────────────────────────────────────────────────────────┐
│                    DONOR/REQUESTER FLOW                          │
│                                                                  │
│  1. User fills form (medicine details + phone number)           │
│  2. System sends 6-digit OTP via SMS/WhatsApp                   │
│  3. User enters OTP                                             │
│  4. System issues temp token (15 min) + tracking code           │
│  5. User can upload images, track status                        │
│  6. No password, no account creation required                   │
│                                                                  │
│  TRACKING: Via tracking code (DON-ABC123), not login            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Flow: Partners and Admins

```
┌─────────────────────────────────────────────────────────────────┐
│                    PARTNER/ADMIN FLOW                            │
│                                                                  │
│  1. Pre-registered by admin (email + temp password)             │
│  2. User logs in with email + password                          │
│  3. Admin: Must complete 2FA (TOTP)                             │
│  4. System issues access token (1h) + refresh token (7d)        │
│  5. Full dashboard access                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Consequences

### Positive

1. **Minimal Friction for Donors**: No password to create, remember, or reset
2. **Higher Conversion**: Fewer form fields = more completed donations
3. **Natural Notification Channel**: Same phone receives OTP and status updates
4. **Trust Building**: OTP verification feels legitimate to Egyptian users
5. **Accessibility**: Works for users with low tech literacy
6. **No Password Reset Flow**: For 80% of users (donors/requesters), no password-related support burden

### Negative

1. **SMS Cost**: Every authentication requires paid SMS (~$0.40 with Twilio, ~$0.04 with local)
2. **Delivery Reliability**: SMS delivery depends on carrier reliability
3. **Phone Number Changes**: If user changes phone, they lose access to old tracking
4. **No Persistent Sessions**: Donors must re-verify for each new donation
5. **Rate Limiting Complexity**: Must prevent OTP abuse

### Risks

| Risk | Mitigation |
|------|------------|
| OTP delivery failure | Offer both SMS and WhatsApp channels; retry logic |
| OTP interception (SIM swap) | For donors, risk is low (no sensitive data); for admins, require 2FA |
| Cost escalation | Rate limit: 3 OTPs/phone/hour; migrate to local SMS provider |
| Phone number spoofing | Rate limit by IP; CAPTCHA for suspicious patterns |

## Alternatives Considered

### Alternative 1: Email + Password for Everyone

**Description**: Traditional email/password authentication for all users.

**Pros**:
- Industry standard
- No SMS costs
- Persistent sessions

**Cons**:
- Higher friction for one-time donors
- Password reset support burden
- Email less reliable in Egypt
- Lower conversion expected

**Why Rejected**: Egyptian market research shows phone-based auth has higher trust and lower friction. Most donors are one-time users who shouldn't need accounts.

### Alternative 2: Social Login (Google/Facebook)

**Description**: OAuth2-based social login.

**Pros**:
- No password management
- Trusted providers
- Quick setup with Spring Security

**Cons**:
- Requires Google/Facebook account
- Privacy concerns for some users
- Doesn't work for WhatsApp notifications
- Less accessible for older users

**Why Rejected**: Not universal in Egyptian market; doesn't solve notification channel problem; adds complexity without clear benefit.

### Alternative 3: WhatsApp-Only Authentication

**Description**: Use WhatsApp Business API for both authentication and notifications.

**Pros**:
- Single channel for all communication
- Higher delivery rates than SMS
- Rich messaging capabilities

**Cons**:
- Requires WhatsApp account (not 100% penetration)
- More complex integration
- Higher per-message cost than local SMS
- WhatsApp Business API approval process

**Why Rejected**: SMS is more universal; can add WhatsApp as secondary channel later.

## Implementation Notes

### OTP Specifications

| Parameter | Value |
|-----------|-------|
| Length | 6 digits |
| Expiry | 5 minutes |
| Max attempts | 3 per OTP |
| Rate limit | 3 OTPs per phone per hour |
| Storage | Redis with TTL |
| Hashing | BCrypt (stored hashed, not plain) |

### Temp Token Specifications

| Parameter | Value |
|-----------|-------|
| Type | JWT |
| Expiry | 15 minutes |
| Permissions | Context-specific (upload image for donations) |
| Storage | Stateless (JWT self-contained) |

## References

- [Twilio Verify API](https://www.twilio.com/docs/verify/api)
- [WhatsApp Business API](https://developers.facebook.com/docs/whatsapp)
- [Egyptian Digital Market Report 2025](https://datareportal.com/reports/digital-2025-egypt)
