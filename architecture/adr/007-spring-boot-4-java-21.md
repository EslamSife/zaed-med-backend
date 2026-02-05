# ADR-007: Spring Boot 4 + Java 21 Technology Stack

## Status
Accepted

## Date
2026-02-05

## Context

We need to select the core technology stack for Zaed Med Connect's backend services. Key considerations:

- **Longevity**: Stack should be supported for years
- **Team skills**: Team has Java/Spring experience
- **Features**: Need modern features (virtual threads, records, pattern matching)
- **Ecosystem**: Rich ecosystem for microservices

### Current Spring Ecosystem (February 2026)

| Technology | Version | Release Date | Support Until |
|------------|---------|--------------|---------------|
| Spring Boot | 4.0.2 | Nov 20, 2025 | Nov 2028 (3 years) |
| Spring Framework | 7.0.2 | Nov 13, 2025 | Nov 2028 |
| Java (LTS) | 21 | Sep 2023 | Sep 2031 |
| Java (Latest) | 25 | Sep 2025 | - |

*Versions verified via spring.io web search*

## Decision

We will use **Java 21 (LTS)** with **Spring Boot 4.0.x** and **Spring Framework 7.0.x**.

### Stack Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                    TECHNOLOGY STACK                              │
│                                                                  │
│  LANGUAGE                                                        │
│  ─────────────────────────────────────────                      │
│  Java 21 (LTS)                                                  │
│  • Virtual Threads (Project Loom) - Final                       │
│  • Record Patterns                                               │
│  • Pattern Matching for switch                                   │
│  • Sequenced Collections                                         │
│                                                                  │
│  FRAMEWORK                                                       │
│  ─────────────────────────────────────────                      │
│  Spring Boot 4.0.x                                              │
│  • Built on Spring Framework 7.0                                │
│  • Jakarta EE 11 baseline                                       │
│  • JPA 3.2 + Hibernate ORM 7.0                                  │
│  • HTTP Service Clients                                          │
│  • API Versioning Support                                        │
│                                                                  │
│  Spring Framework 7.0.x                                         │
│  • JSpecify null-safety                                         │
│  • Servlet 6.1, WebSocket 2.2                                   │
│  • Kotlin 2 support                                             │
│                                                                  │
│  Spring Security 7.x                                            │
│  • MFA built-in                                                 │
│  • OAuth 2.1 support                                            │
│  • Passkey support                                              │
│                                                                  │
│  DATA                                                            │
│  ─────────────────────────────────────────                      │
│  PostgreSQL 16+                                                 │
│  Redis 7+                                                       │
│  Hibernate ORM 7.0                                              │
│                                                                  │
│  BUILD                                                           │
│  ─────────────────────────────────────────                      │
│  Gradle 9.x (recommended) or Maven 3.9+                         │
│  GraalVM 24 (optional, for native compilation)                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Why Java 21 (Not Java 25)

| Factor | Java 21 | Java 25 |
|--------|---------|---------|
| LTS Status | ✅ Yes (until 2031) | ❌ No |
| Production Maturity | ✅ Battle-tested | ⚠️ Recently released |
| Library Compatibility | ✅ Excellent | ⚠️ Some catching up |
| Spring Boot 4 Support | ✅ Full | ✅ Full |
| Team Familiarity | ✅ Likely | ⚠️ New features |

**Decision**: Java 21 for stability. Can upgrade to Java 25 LTS (expected 2027) later.

## Consequences

### Positive

1. **Virtual Threads**: Massive scalability improvement for I/O-bound operations
2. **Modern Language Features**: Records, pattern matching, sealed classes
3. **Long-Term Support**: Java 21 LTS until 2031, Spring Boot 4 until 2028
4. **Rich Ecosystem**: Mature libraries, tooling, documentation
5. **Team Productivity**: Familiar stack for Java developers
6. **Native Compilation Option**: GraalVM support for fast startup if needed

### Negative

1. **Migration Effort**: If team is on Java 17/Spring Boot 3, need to upgrade
2. **Dependency Compatibility**: Some libraries may lag behind Boot 4
3. **Learning Curve**: New Spring Boot 4 / Framework 7 features
4. **Early Adopter Risk**: Spring Boot 4 is relatively new (4 months old)

### Risks

| Risk | Mitigation |
|------|------------|
| Spring Boot 4 bugs | Stick to patch releases (4.0.x); monitor release notes |
| Library incompatibility | Check compatibility before adding dependencies |
| Team unfamiliarity with new features | Training sessions; gradual adoption of new features |
| GraalVM native issues | Use JVM mode for MVP; native is optional optimization |

## Key Spring Boot 4 Features We'll Use

### 1. Virtual Threads

```java
// Automatic virtual threads for web requests
@Configuration
public class VirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreads() {
        return handler -> handler.setExecutor(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
```

**Benefit**: Handle thousands of concurrent requests without thread pool tuning.

### 2. HTTP Service Clients

```java
// Declarative HTTP clients (similar to Feign but built-in)
@HttpExchange("/api/inventory")
public interface InventoryClient {

    @GetExchange("/{productId}")
    ProductStock getStock(@PathVariable String productId);

    @PostExchange("/reserve")
    Reservation reserveStock(@RequestBody ReservationRequest request);
}
```

**Benefit**: Clean, type-safe inter-service communication.

### 3. Record-Based DTOs

```java
// Immutable DTOs with built-in validation
public record CreateDonationRequest(
    @NotBlank String phone,
    @NotBlank String medicineName,
    @NotBlank String quantity,
    @Future LocalDate expiryDate,
    @Valid Location location
) {}
```

**Benefit**: Concise, immutable data transfer objects.

### 4. Pattern Matching

```java
// Clean type-based branching
public String formatNotification(Object event) {
    return switch (event) {
        case DonationVerifiedEvent e -> "تم التحقق من تبرعك: " + e.trackingCode();
        case MatchFoundEvent e -> "تم مطابقة تبرعك مع طلب!";
        case DeliveryConfirmedEvent e -> "تم التسليم بنجاح!";
        default -> "Status update";
    };
}
```

### 5. JPA 3.2 + Hibernate ORM 7

```java
// Modern JPA with improved query capabilities
@Repository
public interface MedicineRepository extends JpaRepository<Medicine, UUID> {

    // Simplified projections
    @Query("SELECT m.nameAr, m.nameEn FROM Medicine m WHERE m.id = :id")
    Optional<MedicineNames> findNamesById(UUID id);
}
```

## Alternatives Considered

### Alternative 1: Java 17 + Spring Boot 3.4

**Description**: Stay on previous LTS stack.

**Pros**:
- Battle-tested (2+ years in production)
- Maximum library compatibility
- Team likely already familiar

**Cons**:
- Missing virtual threads (preview only)
- Missing Spring Boot 4 features (HTTP clients, API versioning)
- Will need to migrate eventually anyway

**Why Rejected**: We're starting fresh; might as well use latest LTS-compatible stack. Virtual threads are too valuable to skip.

### Alternative 2: Kotlin + Spring Boot 4

**Description**: Use Kotlin instead of Java.

**Pros**:
- More concise syntax
- Null safety built into language
- Coroutines for async
- Spring has excellent Kotlin support

**Cons**:
- Team retraining required
- Smaller talent pool
- Some Java libraries have Kotlin friction
- Adds language learning curve to microservices learning curve

**Why Rejected**: Team is learning microservices; adding new language is too much. Can consider Kotlin for specific services later.

### Alternative 3: Quarkus or Micronaut

**Description**: Use alternative Java frameworks optimized for cloud-native.

**Pros**:
- Faster startup (native compilation focus)
- Lower memory footprint
- Modern, opinionated

**Cons**:
- Smaller ecosystem than Spring
- Less documentation/community support
- Team retraining required
- Less portable skills

**Why Rejected**: Spring ecosystem advantages outweigh startup time benefits for our use case. Team can learn industry-standard Spring.

### Alternative 4: Node.js/TypeScript or Go

**Description**: Use non-JVM technology.

**Pros**:
- Node: JavaScript everywhere (frontend + backend)
- Go: Excellent for microservices, fast compilation

**Cons**:
- Team has Java expertise
- Would require significant retraining
- Java ecosystem is more mature for enterprise

**Why Rejected**: Leverage existing team skills. Java/Spring is the right choice for this team.

## Dependencies (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.2</version>
</parent>

<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## References

- [Spring Boot 4.0.0 Release Notes](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)
- [Spring Framework 7.0 Release Notes](https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability)
- [Java 21 Features](https://openjdk.org/projects/jdk/21/)
- [Virtual Threads Documentation](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
