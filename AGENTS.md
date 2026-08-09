# AGENTS.md — vankoo-finance-service

Finance bounded context of **Vankoo**, a crowdfactoring platform by LiquiLabs.
Event-sourced with Axon Framework 4 and CQRS, on Spring Boot 4.1 / Java 25.

**Read `docs/contracts/finance-contracts.md` before changing anything in
`domain/`.** It is the authoritative catalogue of aggregates, commands, events,
invariants and integration contracts. This file only covers what you cannot infer
from the code.

---

## The one thing that surprises everyone

**This service does not model payments. It models one thing: an investor topping
up their balance.** The aggregate is `Deposit`, not `Payment`.

Vankoo moves money at four moments — investor top-up, disbursement to the MYPE,
collection from the debtor, payout to the investor. **Only the first is in v1.**
"Payment" is deliberately reserved for the outbound movements, which will need
their own aggregate.

And `Deposit` stops at `DepositSucceededEvent`: **it does not credit any balance.**
Balance is an invariant ("you cannot invest more than you hold") and cannot be
validated against a projection, so it belongs to a second aggregate, `Wallet`,
which is not designed yet. It appears in the domain diagram without members on
purpose — do not invent them.

Consequence: in v1 a successful top-up leaves no usable balance. That is a known,
documented gap, not a bug.

## Layout, and why it looks like this

The package tree follows the course guide the team is learning from
(`microservices-guide.pdf`, figures 5-10 to 5-14), not the more common
hexagonal conventions. Two axes govern it:

**`interfaces` classifies by transport.** `rest` (HTTP) and `messaging` (broker)
are siblings. Webhooks live under `rest` — they are HTTP — but in their own
subpackage, because they are not our API surface.

**`infrastructure` classifies by role first, technology second**, mirroring the
guide's `brokers/rabbitmq`, `repositories/jdbc`. Hence `eventstore/axon`,
`persistence/jpa`, `brokers/kafka`, `providers/stripe`. No technology-named
package sits at the first level.

```
com.liquilabs.vankoo.finance
├── interfaces/
│   ├── rest/{controllers, webhooks, resources, transform}
│   └── messaging/eventhandlers      # events from OTHER bounded contexts (empty in v1)
├── application/internal/{commandservices, queryservices, outboundservices}
├── domain/
│   ├── model/{aggregates, entities, commands, queries, events, valueobjects}
│   ├── exceptions/                  # business exceptions the aggregate throws
│   │                                 # (InvalidDepositAmountException, ...) — NOT the
│   │                                 # PaymentProvider port's own exceptions, see below
│   └── services/                    # INTERFACES ONLY
└── infrastructure/{eventstore/axon, persistence/jpa/repositories,
                    brokers/kafka, providers/stripe, configuration}
```

### Dependency rules that differ from the usual advice

- **`application` may depend on `infrastructure` directly** for repositories.
  This follows the guide, where `CargoBookingCommandService` imports
  `CargoRepository`. Do not introduce repository interfaces in the domain to
  "fix" this.
- **The one deliberate inversion is `PaymentProvider`**: declared in
  `application/internal/outboundservices`, implemented in
  `infrastructure/providers/stripe`, so the use case is testable against a fake.
- **`domain/services` holds interfaces only.** Implementations live in
  `application/internal/{commandservices,queryservices}`.
- **There is no repository for the aggregate.** `Deposit` is event-sourced; it is
  rehydrated by replay, never loaded from a table. There is no `deposit.save()`.

### Three event-handler roles, three different homes

The guide classifies by direction, not by who authored the event:

| Role | Listens to | Package |
|---|---|---|
| Integration handler | events from **other** bounded contexts | `interfaces/messaging/eventhandlers` |
| Projection + query handlers | **our own** events | `application/internal/queryservices` |
| Integration publisher | **our own** events | `application/internal/outboundservices` |

Infrastructure only holds the technology contact surface: the JPA repository and
the broker channel binding. **The handlers themselves live in `application`.**

## Conventions you will get wrong by default

**Identifiers are value objects, never bare `String`.** Ours (`DepositId`,
`AccountId`) wrap **UUID v7** — time-ordered, for B-tree insert locality. Store
them as native `uuid` in Postgres, not `text`, or the benefit disappears.
Provider-side identifiers (`ProviderDepositId`, `ProviderEventId`,
`IdempotencyKey`) are **opaque**: never parse or validate their shape, since the
provider promises no format.

**Money is `Money(long amountMinor, Currency)`.** No `float`, no `double`. v1
supports `PEN` and `USD`, and **arithmetic between different currencies must
fail**, never silently convert.

**Events are facts in the past tense** — `DepositSucceededEvent`, not
`DepositProcessing`. No `Axon`, `Kafka`, `Stripe` or `Postgres` in an event name.

**HTTP resources:** `CreateXResource` for a request, `XResource` for a response.

**Validation goes in the compact constructor of a record.** See
`CreateProviderDepositRequest`, which rejects a non-positive amount but only
checks the ISO 4217 *shape* of the currency — whether it is in the supported
catalogue is a business invariant the aggregate enforces before the port is ever
called. That split is deliberate; keep it.

**Provider errors are a `sealed` hierarchy** rooted at
`PaymentProviderException`, with `RetryablePaymentProviderException` as a marker
interface. Callers decide whether to retry by type, never by `instanceof` chains
against concrete classes or by matching message text. Because there is no
`module-info.java`, the `permits` classes must live in that same package.

**Assemblers are static**, like the guide's `BookCargoCommandDTOAssembler`. Note
that the `Idempotency-Key` arrives as an HTTP header, not in the body, so the
controller passes it into the assembler explicitly.

## Aggregate rules (Axon 4)

The annotations match the course slides literally — see
`docs/adr/0002-axon-version-and-server-licensing.md` for why Axon 5 was rejected
despite being GA.

```java
@Aggregate
public class Deposit {
    @AggregateIdentifier private String depositId;

    protected Deposit() { }

    @CommandHandler
    public Deposit(InitiateDepositCommand command) {
        // invariants HERE, before emitting
        apply(new DepositInitiatedEvent(...));
    }

    @EventSourcingHandler
    public void on(DepositInitiatedEvent event) {
        // assigns state only — never validates, never throws
    }
}
```

Commands carry `@TargetAggregateIdentifier` on `depositId` for routing.

**Projections must declare `@ProcessingGroup("deposit-read-model")` explicitly.**
The default group name is the handler's package name and it is written into the
token store — without an explicit name, moving the package silently triggers a
full replay.

## Two Postgres schemas, and the difference matters

| Schema | Contents | Rule |
|---|---|---|
| `finance_read_model` | `deposit_view` projection | Disposable. Drop and replay to rebuild. **Never** used to validate an invariant. |
| `finance_ops` | webhook inbox, command idempotency, provider reference resolution | **Does** take part in admission decisions — which is exactly why it is not in the read model. |

## The webhook flow has three independent barriers

Fully drawn in `docs/uml/finance-webhook-sequence-diagram.puml`. The parts that
are easy to break:

1. The `200` is returned **after inserting into the inbox, not after the
   command**. Stripe retries on slow responses, so waiting manufactures more
   duplicates.
2. A webhook arriving **before** the provider reference is registered is parked
   and retried, never rejected — and **never creates a deposit**.
3. Terminal aggregate states make a repeat harmless even if a duplicate slips
   past the inbox.

## Commands and workflows

```bash
sh ./mvnw test          # the wrapper may not be executable; `sh` avoids that
sh ./mvnw spring-boot:run
```

**`VankooFinanceServiceApplicationTests.contextLoads` currently fails.** It is
pre-existing on `develop`, not something you broke: `spring-boot-starter-data-jpa`
is on the classpath with no datasource configured. The bootstrap work fixes it.
Every other test passes.

Diagrams are PlantUML under `docs/uml/` and `docs/architecture/`:

```bash
plantuml -checkonly docs/uml/*.puml docs/architecture/*.puml
```

**`-checkonly` is not enough** — render to PNG and look at it. PlantUML fails
silently in ways that pass syntax checks: a reference to a nested package that
does not resolve creates a *new* package rather than erroring, and two packages
sharing a leaf name resolve to the wrong one. Use full paths from the root
package in relations. Also, `@startuml <name>` sets the output filename, not the
title. Class diagrams with high fan-out need `left to right direction` or they
clip against PlantUML's 4096px width limit.

## Integration points

- **Stripe** — only ever behind `PaymentProvider`. No SDK type crosses that
  interface, in parameters, return values or exceptions. Signature verification
  and raw payload retention stay in `infrastructure`.
- **Axon Server** — event store and internal router, **standalone only**.
  Clustering requires a paid plan; the free tier is non-production use, which
  suits an academic project. See ADR-0002.
- **Kafka** — `vankoo.finance.events.v1`, keyed by `depositId`. **It has no
  consumer in v1**: `Wallet` lives inside this same bounded context and will
  receive events over Axon's internal event bus. Kafka is published to fix the
  contract, not because anything reads it. Only the three outcomes
  (`Succeeded`, `Failed`, `Cancelled`) are public; the other four events are
  internal, each with its reason recorded in the contract.
- **Eureka** — the service registers with Vankoo's discovery server.

## Working with the team

Two people: Salim owns Axon, event sourcing, CQRS and the aggregate; Anjali owns
Stripe, webhooks and integrations.

Feature branches come off `develop` and go back to `develop` by PR — **never into
each other**. `main` is untouched.

**`pom.xml` is a coordination point**: team rule 9 forbids both people editing it
at once. Check whether bootstrap work is in flight before adding a dependency.

Code and Javadoc are in **English**; `docs/` is in **Spanish**. Commit messages
are conventional commits in English, with a body explaining the why.

## Where decisions live

- `docs/contracts/finance-contracts.md` — the domain contract; authoritative
- `docs/adr/0001-...md` — Axon Server as event store, Postgres read model, Kafka
- `docs/adr/0002-...md` — Axon 4 vs 5, and Axon Server licensing
- `docs/uml/`, `docs/architecture/` — domain model, flows, sequence, C4

The contract has a **"Decisiones pendientes"** section listing what is genuinely
undecided, each with the reason it was deferred rather than guessed. Two examples
worth knowing, both touched by Card 3 (the aggregate): `ProviderDepositId`,
`ProviderEventId`, `IdempotencyKey`, `NormalizedDepositStatus` and `FailureReason`
now live in `domain/model/valueobjects` — but the port itself
(`PaymentProvider`, `StripePaymentProvider`, `StripePaymentProperties`) was
**deliberately left untouched**, still on its provisional, Stripe-SDK-owner's
copy. Adapting the port to import the real domain types is that owner's work,
not something to do opportunistically from elsewhere.
