# ADR-0001: Start with a modular monolith

Status: Accepted
Date: 2026-07-09
Deciders: Founders

## Context

The initial team is two founders using AI coding agents. The product has multiple domains but no validated production scale. Microservices would add deployment, networking, observability, consistency and local-development overhead before value is proven.

## Decision

Implement the backend as one deployable Spring Boot application with enforced module boundaries for identity, properties, reviews, verification, moderation, media, search and notifications.

## Consequences

### Positive

- faster MVP delivery;
- simple transactions and local development;
- lower infrastructure cost;
- easier end-to-end debugging;
- AI agents can understand the system with less distributed context.

### Trade-offs

- independent scaling/deployment is not available initially;
- boundaries must be enforced by architecture tests and code review;
- careless shared database access could create a distributed-monolith problem later.

## Revisit when

A module has measured independent scaling, security isolation, ownership or release requirements that cannot be addressed cleanly within the monolith.
