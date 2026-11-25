# Salon Management Platform

Multi-tenant salon management system: sales, payments, inventory, commissions, and a double-entry accounting core that owns all financial truth.

## Tech stack

| Layer | Choice |
| ----- | ------ |
| Backend | Java 21, Spring Boot 3.5.x |
| Database | MySQL 8.4 LTS — InnoDB, utf8mb4, READ COMMITTED |
| Schema | Hibernate `ddl-auto` (`update` dev → `validate` prod) |
| Auth | Spring Security + JWT; layered authorization (`@PreAuthorize` + `BusinessAuthorizationService` + row checks) |
| Config | `application.properties` |
| Tests | JUnit 5, Mockito, Testcontainers **MySQL** (never H2) |
| Frontend | Angular + TypeScript |

## Documentation

Build from these docs in order — each phase doc contains its schema contract, tasks, tests, and verification checklist.

- **[docs/blueprint.md](docs/blueprint.md)** — the full technical blueprint: decisions, schema contract, authorization model, transaction workflows, API surface, MySQL operational notes.
- Phase docs (`docs/phases/`):
  1. [Project foundation](docs/phases/phase-01-project-foundation.md)
  2. [Authentication and business isolation](docs/phases/phase-02-authentication-and-business-isolation.md)
  3. [Catalogue and employees](docs/phases/phase-03-catalogue-and-employees.md)
  4. [Accounting foundation](docs/phases/phase-04-accounting-foundation.md)
  5. [Sales and payments](docs/phases/phase-05-sales-and-payments.md)
  6. [Inventory and suppliers](docs/phases/phase-06-inventory-and-suppliers.md)
  7. [Commissions and expenses](docs/phases/phase-07-commissions-and-expenses.md)
  8. [Reporting](docs/phases/phase-08-reporting.md)
  9. [Production readiness](docs/phases/phase-09-production-readiness.md)

## Build order

Phases are **sequential**. Sales and reporting depend on the accounting core — phase 4 (accounting) must land before phase 5, or the sales module will invent its own financial logic. The milestone that matters: create business → catalogue → combined sale with split payment → weighted-average inventory → balanced journal entries → a P&L that reconciles with the ledger.

## Non-negotiables (blueprint §11)

`BigDecimal`/`DECIMAL` for money. `BIGINT` auto-increment ids. `DATETIME(6)` UTC timestamps — never `TIMESTAMP`. Posted journal entries are immutable; corrections are reversals. No financial report from anything but the ledger. `SELECT ... FOR UPDATE` on stock balances and `sequence_counters`. `idempotency_key` on payments. `business_id` never trusted from the client. Real MySQL for integration tests — never H2. `ddl-auto` never writes production schema.
