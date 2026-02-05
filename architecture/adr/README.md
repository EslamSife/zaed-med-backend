# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records for Zaed Med Connect.

## What is an ADR?

An Architecture Decision Record (ADR) captures an important architectural decision made along with its context and consequences.

## ADR Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-001](001-microservices-architecture.md) | Microservices Architecture | Accepted | Feb 2026 |
| [ADR-002](002-phone-first-authentication.md) | Phone-First Authentication for Egypt | Accepted | Feb 2026 |
| [ADR-003](003-spring-security-over-keycloak.md) | Spring Security over Keycloak (Phase 1) | Accepted | Feb 2026 |
| [ADR-004](004-sms-gateway-strategy.md) | SMS Gateway Strategy (Twilio → Local) | Accepted | Feb 2026 |
| [ADR-005](005-levenshtein-only-matching.md) | Levenshtein-Only Medicine Matching | Accepted | Feb 2026 |
| [ADR-006](006-arabic-language-support.md) | Full Arabic Language Support | Accepted | Feb 2026 |
| [ADR-007](007-spring-boot-4-java-21.md) | Spring Boot 4 + Java 21 Tech Stack | Accepted | Feb 2026 |
| [ADR-008](008-jwt-rs256-migration.md) | JWT RS256 Signing with Migration Plan | Accepted | Feb 2026 |
| [ADR-009](009-identity-service-architecture.md) | Identity Service Architecture | Accepted | Feb 2026 |

## ADR Status Definitions

- **Proposed**: Under discussion
- **Accepted**: Approved and in effect
- **Deprecated**: No longer recommended
- **Superseded**: Replaced by another ADR

## Template

When creating a new ADR, use this template:

```markdown
# ADR-XXX: Title

## Status
[Proposed | Accepted | Deprecated | Superseded by ADR-XXX]

## Date
YYYY-MM-DD

## Context
What is the issue that we're seeing that is motivating this decision?

## Decision
What is the change that we're proposing and/or doing?

## Consequences

### Positive
- Benefit 1
- Benefit 2

### Negative
- Trade-off 1
- Trade-off 2

### Risks
- Risk 1 and mitigation

## Alternatives Considered

### Alternative 1
Description and why it was rejected.

### Alternative 2
Description and why it was rejected.

## References
- Link 1
- Link 2
```
