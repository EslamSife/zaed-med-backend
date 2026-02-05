# ADR-001: Microservices Architecture

## Status
Accepted

## Date
2026-02-05

## Context

Zaed Med Connect is a medicine donation coordination platform for Egypt. We need to decide on the overall system architecture. The platform has the following characteristics:

- **MVP stage**: Initial launch with uncertain traffic patterns
- **Team composition**: Developers looking to gain microservices experience
- **Domain complexity**: Multiple bounded contexts (Auth, Donation, Request, Matching, Partner, Notification)
- **Scale uncertainty**: Target 500 matches/month initially, growth unknown

The traditional recommendation for MVPs is to start with a modular monolith and extract microservices when scale demands it. However, this project has unique considerations.

## Decision

We will implement a **microservices architecture** from the start, despite the MVP stage and uncertain traffic volume.

### Service Decomposition

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   IDENTITY   │  │   DONATION   │  │   REQUEST    │
│   SERVICE    │  │   SERVICE    │  │   SERVICE    │
│  Port: 8081  │  │  Port: 8082  │  │  Port: 8083  │
└──────────────┘  └──────────────┘  └──────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   MATCHING   │  │   PARTNER    │  │ NOTIFICATION │
│    ENGINE    │  │   SERVICE    │  │   SERVICE    │
│  Port: 8084  │  │  Port: 8085  │  │  Port: 8086  │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Communication Patterns
- **Synchronous**: REST APIs via API Gateway for client-facing operations
- **Asynchronous**: Kafka/RabbitMQ for event-driven communication between services

## Consequences

### Positive

1. **Team Skill Development**: Production experience with microservices patterns, which is the primary driver for this decision
2. **Real-World Learning**: Team learns distributed systems challenges with real stakes (production system)
3. **Clear Boundaries**: Forces proper domain separation from day one
4. **Independent Deployment**: Services can be deployed independently once CI/CD is established
5. **Technology Flexibility**: Each service can evolve its tech stack independently if needed
6. **Fault Isolation**: Failures in one service don't cascade to others

### Negative

1. **Higher Initial Complexity**: More infrastructure to set up and manage
2. **Operational Overhead**: Need container orchestration (Kubernetes), service discovery, distributed tracing
3. **Network Latency**: Inter-service communication adds latency compared to in-process calls
4. **Data Consistency**: Must handle eventual consistency, distributed transactions are complex
5. **Development Velocity**: Initially slower than monolith due to infrastructure setup
6. **Cost**: More cloud resources than a single monolith

### Risks

| Risk | Mitigation |
|------|------------|
| Distributed monolith (tightly coupled services) | Strict API contracts, async communication, each service owns its data |
| Team overwhelmed by complexity | Start with 2-3 core services, add others incrementally |
| Debugging difficulty | Invest in observability: distributed tracing (Jaeger), centralized logging (ELK) |
| Data consistency issues | Use Saga pattern for cross-service transactions, accept eventual consistency |

## Alternatives Considered

### Alternative 1: Modular Monolith
**Description**: Single deployable with well-defined module boundaries, extract to microservices later.

**Pros**:
- Faster initial development
- Simpler debugging (single process)
- Lower infrastructure cost
- Easier refactoring within modules

**Cons**:
- Team doesn't gain microservices experience
- Module boundaries may blur over time
- Extraction to microservices is non-trivial

**Why Rejected**: Primary project goal includes team skill development in microservices. This is a conscious trade-off of short-term velocity for long-term capability building.

### Alternative 2: Serverless (AWS Lambda / Cloud Functions)
**Description**: Function-as-a-Service architecture with managed infrastructure.

**Pros**:
- Zero infrastructure management
- Auto-scaling built-in
- Pay-per-use pricing

**Cons**:
- Cold start latency issues
- Vendor lock-in
- Less applicable microservices learning
- Debugging complexity

**Why Rejected**: Team wants to learn container-based microservices patterns (Kubernetes, Docker) which are more broadly applicable than serverless-specific patterns.

## Implementation Notes

1. **Phase 1**: Implement Identity Service + Notification Service (foundation)
2. **Phase 2**: Add Donation Service + Request Service
3. **Phase 3**: Implement Matching Engine (core domain)
4. **Phase 4**: Add Partner Service + API Gateway

## References

- [Microservices Patterns by Chris Richardson](https://microservices.io/patterns/)
- [Building Microservices by Sam Newman](https://samnewman.io/books/building_microservices_2nd_edition/)
- [Martin Fowler: Monolith First](https://martinfowler.com/bliki/MonolithFirst.html) (counterargument we're consciously overriding)
