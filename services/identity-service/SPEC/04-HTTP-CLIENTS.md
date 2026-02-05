# Declarative HTTP Clients with @ImportHttpServices

## Overview

**Feature**: Spring Boot 4 @ImportHttpServices / @HttpExchange
**Priority**: P3 - Future (Low)
**Effort**: Low (2-3 hours)
**Risk**: Low

---

## Current State

### RestClient Usage (SmsMisrGateway.java)

```java
@Service
@Profile("smsmisr")
public class SmsMisrGateway implements SmsGateway {

    private final RestClient restClient;
    private final SmsMisrConfig config;

    @Retryable(...)
    @ConcurrencyLimit(10)
    @Override
    public boolean sendOtp(String phone, String otp, String channel) {
        String response = restClient.post()
            .uri(config.getBaseUrl() + "/sms/send")
            .body(buildFormData(phone, otp))
            .retrieve()
            .body(String.class);

        return parseResponse(response);
    }
}
```

### Twilio SDK Usage (Direct SDK, not HTTP)

```java
@Service
@ConditionalOnProperty(name = "zaed.sms.provider", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {

    // Uses Twilio SDK directly - NOT a candidate for @HttpExchange
    Message message = Message.creator(
        new PhoneNumber(phone),
        new PhoneNumber(config.getFromNumber()),
        formatMessage(otp)
    ).create();
}
```

---

## Why This is Low Priority

1. **Twilio uses SDK** - Not HTTP client, no benefit from @HttpExchange
2. **SMS Misr is Phase 2** - Not actively used yet
3. **Current RestClient works** - No bugs or issues
4. **Resilience already applied** - @Retryable works on current impl

---

## Target Implementation (For Future Reference)

### Step 1: Define HTTP Interface

**SmsMisrApi.java**
```java
package health.zaed.identity.client;

import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.bind.annotation.RequestParam;

@HttpExchange
public interface SmsMisrApi {

    @PostExchange("/sms/send")
    SmsMisrResponse sendSms(
        @RequestParam("username") String username,
        @RequestParam("password") String password,
        @RequestParam("sender") String sender,
        @RequestParam("mobile") String mobile,
        @RequestParam("message") String message
    );
}
```

### Step 2: Define Response DTO

**SmsMisrResponse.java**
```java
public record SmsMisrResponse(
    String code,
    String message,
    String smsId
) {
    public boolean isSuccess() {
        return "1901".equals(code) || "Success".equalsIgnoreCase(message);
    }
}
```

### Step 3: Register with @ImportHttpServices

**IdentityServiceApplication.java**
```java
@SpringBootApplication
@ConfigurationPropertiesScan
@ImportHttpServices(
    clients = SmsMisrApi.class,
    baseUrl = "${zaed.sms.smsmisr.base-url}"
)
public class IdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

### Step 4: Use in Gateway

**SmsMisrGateway.java** (refactored)
```java
@Service
@Profile("smsmisr")
public class SmsMisrGateway implements SmsGateway {

    private final SmsMisrApi smsMisrApi;  // Injected declarative client
    private final SmsMisrConfig config;

    public SmsMisrGateway(SmsMisrApi smsMisrApi, SmsMisrConfig config) {
        this.smsMisrApi = smsMisrApi;
        this.config = config;
    }

    @Retryable(...)
    @ConcurrencyLimit(10)
    @Override
    public boolean sendOtp(String phone, String otp, String channel) {
        SmsMisrResponse response = smsMisrApi.sendSms(
            config.getUsername(),
            config.getPassword(),
            config.getSender(),
            convertToLocalFormat(phone),
            formatMessage(otp)
        );

        return response.isSuccess();
    }
}
```

---

## Alternative: HttpServiceProxyFactory (More Control)

If you need custom configuration:

```java
@Configuration
public class HttpClientConfig {

    @Bean
    public SmsMisrApi smsMisrApi(SmsMisrConfig config) {
        RestClient restClient = RestClient.builder()
            .baseUrl(config.getBaseUrl())
            .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
            .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();

        return factory.createClient(SmsMisrApi.class);
    }
}
```

---

## Technical Tasks (When Implemented)

### Task 1: Create HTTP Interface
- [ ] Create `SmsMisrApi.java` interface
- [ ] Define `@PostExchange` methods
- [ ] Create response DTOs

### Task 2: Register Client
- [ ] Add `@ImportHttpServices` to main application
- [ ] Or create `HttpClientConfig.java` bean

### Task 3: Refactor Gateway
- [ ] Inject declarative client
- [ ] Remove manual RestClient calls
- [ ] Keep resilience annotations

### Task 4: Test
- [ ] Verify SMS sending works
- [ ] Verify retries work
- [ ] Verify rate limiting works

---

## Files to Create/Modify

| File | Action |
|------|--------|
| `SmsMisrApi.java` | CREATE - HTTP interface |
| `SmsMisrResponse.java` | CREATE - Response DTO |
| `SmsMisrGateway.java` | MODIFY - Use declarative client |
| `IdentityServiceApplication.java` | MODIFY - Add @ImportHttpServices |
| `RestClientConfig.java` | DELETE or MODIFY - May not need |

---

## Benefits

1. **Less boilerplate** - No manual URI building
2. **Type-safe** - Interface defines contract
3. **Testable** - Easy to mock interface
4. **Consistent** - Standard Spring pattern

---

## When to Implement

Implement when:
- SMS Misr becomes primary provider
- Adding new external HTTP services
- Refactoring for consistency

Skip if:
- Current implementation works fine
- No new HTTP clients needed
- Time constraints
