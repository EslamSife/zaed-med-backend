# Observability with Micrometer 2 / OpenTelemetry

## Overview

**Feature**: Spring Boot 4 Observability (Micrometer 2, OpenTelemetry)
**Priority**: P2 - Nice to Have
**Effort**: Medium (4-6 hours)
**Risk**: Low

---

## Current State

### Actuator Dependency Exists

**pom.xml (lines 52-55)**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### What's Likely Missing
- Custom metrics for auth events
- Tracing configuration
- Prometheus endpoint exposure
- Health indicators for dependencies

---

## Target Implementation

### Step 1: Add Observability Dependencies

**pom.xml additions**
```xml
<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- OpenTelemetry (optional, for distributed tracing) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OTLP Exporter (for Jaeger/Tempo) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

### Step 2: Configure Actuator Endpoints

**application.yml**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true  # Kubernetes probes
  metrics:
    tags:
      application: identity-service
      environment: ${SPRING_PROFILES_ACTIVE:local}
    distribution:
      percentiles-histogram:
        http.server.requests: true
  health:
    redis:
      enabled: true
    db:
      enabled: true
```

### Step 3: Configure Tracing

**application.yml (tracing section)**
```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # 100% in dev, reduce in prod
    propagation:
      type: w3c  # W3C Trace Context
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

### Step 4: Add Custom Metrics

**AuthMetrics.java**
```java
package health.zaed.identity.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter otpSentCounter;
    private final Counter otpVerifiedCounter;
    private final Counter twoFactorVerifiedCounter;
    private final Counter tokenRefreshCounter;
    private final Timer loginTimer;

    public AuthMetrics(MeterRegistry registry) {
        this.loginSuccessCounter = Counter.builder("auth.login.success")
            .description("Successful login attempts")
            .register(registry);

        this.loginFailureCounter = Counter.builder("auth.login.failure")
            .description("Failed login attempts")
            .tag("reason", "unknown")  // Will be overridden
            .register(registry);

        this.otpSentCounter = Counter.builder("auth.otp.sent")
            .description("OTPs sent")
            .register(registry);

        this.otpVerifiedCounter = Counter.builder("auth.otp.verified")
            .description("OTPs verified successfully")
            .register(registry);

        this.twoFactorVerifiedCounter = Counter.builder("auth.2fa.verified")
            .description("2FA verifications")
            .register(registry);

        this.tokenRefreshCounter = Counter.builder("auth.token.refresh")
            .description("Token refresh operations")
            .register(registry);

        this.loginTimer = Timer.builder("auth.login.duration")
            .description("Login operation duration")
            .register(registry);
    }

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFailure(String reason) {
        Counter.builder("auth.login.failure")
            .tag("reason", reason)
            .register(loginSuccessCounter.getId().getMeterRegistry())
            .increment();
    }

    public void recordOtpSent(String channel) {
        Counter.builder("auth.otp.sent")
            .tag("channel", channel)
            .register(otpSentCounter.getId().getMeterRegistry())
            .increment();
    }

    public Timer.Sample startLoginTimer() {
        return Timer.start();
    }

    public void stopLoginTimer(Timer.Sample sample) {
        sample.stop(loginTimer);
    }
}
```

### Step 5: Integrate Metrics in Services

**AuthService.java** (with metrics)
```java
@Service
public class AuthService {

    private final AuthMetrics authMetrics;
    // ... other dependencies

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Timer.Sample sample = authMetrics.startLoginTimer();
        try {
            // ... existing login logic
            authMetrics.recordLoginSuccess();
            return response;
        } catch (AuthException e) {
            authMetrics.recordLoginFailure(e.getErrorCode());
            throw e;
        } finally {
            authMetrics.stopLoginTimer(sample);
        }
    }
}
```

### Step 6: Add Custom Health Indicators

**SmsGatewayHealthIndicator.java**
```java
package health.zaed.identity.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SmsGatewayHealthIndicator implements HealthIndicator {

    private final SmsGateway smsGateway;

    public SmsGatewayHealthIndicator(SmsGateway smsGateway) {
        this.smsGateway = smsGateway;
    }

    @Override
    public Health health() {
        try {
            // Check if gateway is configured and reachable
            boolean supportsWhatsApp = smsGateway.supportsWhatsApp();
            return Health.up()
                .withDetail("provider", smsGateway.getClass().getSimpleName())
                .withDetail("whatsapp", supportsWhatsApp)
                .withDetail("costPerSms", smsGateway.getCostPerSms())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## Technical Tasks

### Task 1: Add Dependencies
- [ ] Add micrometer-registry-prometheus
- [ ] Add micrometer-tracing-bridge-otel (optional)
- [ ] Add opentelemetry-exporter-otlp (optional)

### Task 2: Configure Actuator
- [ ] Expose health, metrics, prometheus endpoints
- [ ] Configure health probes for Kubernetes
- [ ] Add application tags

### Task 3: Create Custom Metrics
- [ ] Create `AuthMetrics.java`
- [ ] Define counters for login, OTP, 2FA, refresh
- [ ] Define timers for operation duration

### Task 4: Integrate Metrics
- [ ] Add metrics to `AuthService.java`
- [ ] Add metrics to `OtpService.java`
- [ ] Add metrics to `TwoFactorService.java`
- [ ] Add metrics to SMS gateways

### Task 5: Add Health Indicators
- [ ] Create `SmsGatewayHealthIndicator.java`
- [ ] Verify Redis health indicator works
- [ ] Verify PostgreSQL health indicator works

### Task 6: Configure Tracing (Optional)
- [ ] Add tracing config to application.yml
- [ ] Set up OTLP endpoint
- [ ] Verify traces in Jaeger/Tempo

---

## Files to Create/Modify

| File | Action |
|------|--------|
| `pom.xml` | MODIFY - Add observability deps |
| `application.yml` | MODIFY - Add management config |
| `AuthMetrics.java` | CREATE - Custom metrics |
| `SmsGatewayHealthIndicator.java` | CREATE - Health check |
| `AuthService.java` | MODIFY - Integrate metrics |
| `OtpService.java` | MODIFY - Integrate metrics |
| `TwoFactorService.java` | MODIFY - Integrate metrics |

---

## Verification

### Check Endpoints

```bash
# Health endpoint
curl http://localhost:8080/actuator/health

# Metrics endpoint
curl http://localhost:8080/actuator/metrics

# Prometheus endpoint
curl http://localhost:8080/actuator/prometheus

# Specific metric
curl http://localhost:8080/actuator/metrics/auth.login.success
```

### Expected Prometheus Output

```
# HELP auth_login_success_total Successful login attempts
# TYPE auth_login_success_total counter
auth_login_success_total{application="identity-service"} 42.0

# HELP auth_login_duration_seconds Login operation duration
# TYPE auth_login_duration_seconds histogram
auth_login_duration_seconds_bucket{le="0.05"} 10.0
auth_login_duration_seconds_bucket{le="0.1"} 35.0
auth_login_duration_seconds_count 42.0
auth_login_duration_seconds_sum 3.456
```

---

## Grafana Dashboard Queries

```promql
# Login success rate (last 5 min)
rate(auth_login_success_total[5m])

# Login failure rate by reason
sum by (reason) (rate(auth_login_failure_total[5m]))

# OTP send rate by channel
sum by (channel) (rate(auth_otp_sent_total[5m]))

# Login latency P95
histogram_quantile(0.95, rate(auth_login_duration_seconds_bucket[5m]))
```
