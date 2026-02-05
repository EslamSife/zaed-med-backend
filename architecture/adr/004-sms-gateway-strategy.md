# ADR-004: SMS Gateway Strategy (Twilio → Local Provider)

## Status
Accepted

## Date
2026-02-05

## Context

Zaed Med Connect requires SMS capabilities for:

1. **OTP Verification**: Phone authentication for donors/requesters
2. **Status Notifications**: Match found, ready for pickup, delivered
3. **Arabic Support**: 80% of messages will be in Arabic

### Cost Challenge

SMS to Egypt is expensive through international providers:

| Provider | Cost per SMS (Egypt) | Arabic SMS Cost* |
|----------|---------------------|------------------|
| Twilio | $0.3959 | $0.71 (2 segments) |
| Plivo | $0.1091 | $0.20 (2 segments) |
| Local (SMS Misr) | ~$0.035 | $0.06 (2 segments) |

*Arabic uses Unicode encoding = 70 chars/SMS vs 160 for English

### Volume Projection

| Scenario | Messages/Month | Twilio Cost | Local Cost |
|----------|----------------|-------------|------------|
| MVP (500 matches) | 3,500 | $2,494 | $220 |
| Growth (2,000 matches) | 14,000 | $9,976 | $882 |
| Scale (5,000 matches) | 35,000 | $24,940 | $2,205 |

## Decision

We will implement a **two-phase SMS gateway strategy**:

### Phase 1: Twilio (MVP Launch)

- **Provider**: Twilio
- **Duration**: Months 1-6
- **Rationale**: Fastest setup, reliable, WhatsApp support
- **Budget**: ~$2,500/month

### Phase 2: Local Provider (Production Scale)

- **Provider**: SMS Misr (or similar NTRA-registered provider)
- **Trigger**: When reaching 5,000+ SMS/month OR completing NTRA registration
- **Rationale**: 90% cost reduction
- **Budget**: ~$250/month for same volume

### Implementation: Gateway Abstraction

```java
/**
 * SMS Gateway abstraction for provider-agnostic messaging.
 * Allows switching providers via Spring profile without code changes.
 */
public interface SmsGateway {

    SmsResult sendOtp(String phoneNumber, String otp);

    SmsResult sendNotification(String phoneNumber, String message);

    SmsResult sendWhatsApp(String phoneNumber, String templateId, Map<String, String> params);

    boolean supportsWhatsApp();

    BigDecimal getCostPerSms();
}

// Implementations:
// - TwilioSmsGateway (@Profile("twilio"))
// - SmsMisrGateway (@Profile("smsmisr"))
```

### Configuration

```yaml
# Switch provider via profile
spring:
  profiles:
    active: twilio  # Change to 'smsmisr' when ready
```

## Consequences

### Positive

1. **Fast MVP Launch**: Twilio setup takes ~2 hours
2. **Reliability**: Twilio has excellent delivery rates globally
3. **WhatsApp Support**: Twilio provides WhatsApp Business API
4. **Cost Optimization Path**: Clear migration to 90% cheaper local provider
5. **Provider Agnostic**: Abstraction layer allows easy switching
6. **No Vendor Lock-in**: Can switch providers with config change

### Negative

1. **High Initial Cost**: $2,500/month for MVP is significant
2. **NTRA Registration Required**: 2-4 weeks for local provider approval
3. **Local Provider Limitations**: SMS Misr may not support WhatsApp
4. **Dual Integration Maintenance**: Must maintain both provider implementations
5. **Testing Complexity**: Need to test both providers

### Risks

| Risk | Mitigation |
|------|------------|
| Twilio costs exceed budget | Implement strict rate limiting; prioritize local provider migration |
| NTRA registration delays | Start registration process in Month 1, don't wait |
| Local provider reliability issues | Keep Twilio as fallback; implement circuit breaker |
| Arabic message truncation | Validate all templates under 70 chars; character counter |

## Alternatives Considered

### Alternative 1: Local Provider from Day 1

**Description**: Start with SMS Misr immediately.

**Pros**:
- Lowest cost from start
- Native Arabic support
- Direct carrier connections

**Cons**:
- NTRA registration takes 2-4 weeks
- Delays MVP launch
- Less documentation/support
- No WhatsApp integration

**Why Rejected**: MVP launch timeline is critical; can't wait for NTRA registration.

### Alternative 2: Twilio Only (No Migration)

**Description**: Stay with Twilio permanently.

**Pros**:
- Single provider simplicity
- Best-in-class reliability
- WhatsApp included
- Global coverage if expanding

**Cons**:
- $30,000+/year at scale
- Significant operational expense
- No cost optimization

**Why Rejected**: Cost is unsustainable for NGO/social impact project at scale.

### Alternative 3: AWS SNS

**Description**: Use Amazon Simple Notification Service.

**Pros**:
- AWS ecosystem integration
- Competitive pricing ($0.20/SMS to Egypt)
- Scalable infrastructure

**Cons**:
- Still more expensive than local
- Less Egypt-specific optimization
- No WhatsApp support

**Why Rejected**: Local provider is still significantly cheaper; doesn't solve the core cost problem.

## Implementation Notes

### NTRA Registration Checklist

| Requirement | Status | Notes |
|-------------|--------|-------|
| Egyptian business registration | ☐ | Required for sender ID |
| Sender ID application ("ZAED") | ☐ | 2-3 weeks approval |
| Template pre-approval | ☐ | Submit all SMS templates |
| Technical integration | ☐ | API credentials from provider |

### Arabic SMS Template Guidelines

All Arabic templates MUST be under 70 characters:

```
✓ "زائد: رمز التحقق الخاص بك هو %s"          (52 chars)
✓ "تم استلام تبرعك. كود التتبع: %s"           (45 chars)
✓ "تم مطابقة تبرعك! يرجى التوصيل إلى: %s"    (65 chars)
✗ Long message that exceeds 70 characters... (REJECTED)
```

### Cost Tracking Schema

```sql
CREATE TABLE sms_cost_log (
    id UUID PRIMARY KEY,
    phone VARCHAR(20),
    message_type VARCHAR(50),    -- 'OTP', 'NOTIFICATION'
    provider VARCHAR(50),        -- 'TWILIO', 'SMSMISR'
    segments INTEGER,            -- Number of SMS segments
    is_arabic BOOLEAN,
    cost DECIMAL(10, 4),
    sent_at TIMESTAMP
);

-- Monthly cost report
SELECT
    DATE_TRUNC('month', sent_at) AS month,
    provider,
    SUM(segments) AS total_segments,
    SUM(cost) AS total_cost
FROM sms_cost_log
GROUP BY month, provider;
```

## Migration Timeline

```
┌─────────────────────────────────────────────────────────────────┐
│                    SMS MIGRATION TIMELINE                        │
│                                                                  │
│  MONTH 1: MVP Launch                                            │
│  • Twilio integration complete                                  │
│  • Start NTRA registration process                              │
│                                                                  │
│  MONTH 2-3: NTRA Approval                                       │
│  • Await sender ID approval                                     │
│  • Develop SMS Misr integration                                 │
│                                                                  │
│  MONTH 4-5: Parallel Testing                                    │
│  • Test SMS Misr with internal numbers                          │
│  • Validate delivery rates and latency                          │
│                                                                  │
│  MONTH 6: Migration                                             │
│  • Switch profile to 'smsmisr'                                  │
│  • Keep Twilio as fallback                                      │
│  • Monitor delivery rates                                        │
│                                                                  │
│  ONGOING: Cost Monitoring                                       │
│  • Monthly cost reports                                         │
│  • Alert if costs exceed threshold                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## References

- [Twilio SMS Pricing - Egypt](https://www.twilio.com/en-us/sms/pricing/eg)
- [SMS Misr](https://smsmisr.com/)
- [NTRA Egypt](https://www.tra.gov.eg/)
- [Unicode SMS Encoding](https://www.twilio.com/docs/glossary/what-is-gsm-7-character-encoding)
