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
├── application/internal/{commandservices, queryservices, outboundservices,
│                          eventhandlers}   # handlers of OUR OWN events that are
│                                            # not the read model projection
├── domain/
│   ├── model/{aggregates, entities, commands, queries, events, valueobjects}
│   ├── exceptions/                  # what the aggregate throws
│   │                                 # (InvalidDepositAmountException, ...) AND the
│   │                                 # sealed PaymentProvider hierarchy, see below
│   └── services/                    # INTERFACES ONLY
└── infrastructure/{eventstore/axon, persistence/jpa/{entities, converters,
                    repositories}, brokers/kafka, providers/stripe, configuration}
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

### Four event-handler roles, four different homes

The guide classifies by direction, not by who authored the event. The fourth row
is not in the guide: it is the slot for a handler of our own events that is not
the read model projection, which the guide never needed because it has no
operational tables.

| Role | Listens to | Package |
|---|---|---|
| Integration handler | events from **other** bounded contexts | `interfaces/messaging/eventhandlers` |
| Projection + query handlers | **our own** events, writing the read model | `application/internal/queryservices` |
| Integration publisher | **our own** events | `application/internal/outboundservices` |
| Anything else reacting to **our own** events | e.g. writing a `finance_ops` table | `application/internal/eventhandlers` |

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
`VerifiedProviderDepositUpdate`, which rejects a missing `providerEventId`
outright — without it the inbox has nothing to deduplicate on.

The ISO 4217 shape check the port used to do is gone: `Currency` is an enum, so
an unsupported currency cannot be represented once inside the domain. What
remains of that split is `amountMinor > 0`, still enforced by the aggregate and
repeated in the Stripe adapter, because `Money` deliberately does not enforce it.

**`PaymentProvider.java` is three method signatures and nothing else.** Inputs
travel as loose parameters — there is no `CreateProviderDepositRequest`. The three
return types live in `domain/model/valueobjects`, the errors in
`domain/exceptions`. **`application` declares no types of its own**; do not add a
`model/` subpackage under `outboundservices`. Card 7 tried it and reverted: the
port's whole point is that it speaks the domain's vocabulary, so giving it a
parallel one defeats the inversion it exists for.

**`domain/exceptions` holds two families**, and they are not the same thing: what
the aggregate throws (`InvalidDepositAmountException`, …), and the `sealed`
`PaymentProviderException` hierarchy the Stripe adapter throws. The first rejects
a command; the second produces no domain event at all. Callers decide whether to
retry by type — `RetryablePaymentProviderException` is a marker interface — never
by `instanceof` chains against concrete classes or by matching message text.
Because there is no `module-info.java`, the `permits` classes must stay in that
package. `UnsupportedProviderEventException` is separate from
`PaymentProviderRejectedException` so the webhook endpoint can answer `200` to an
event type we do not handle — an error there would only make Stripe retry it
forever.

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
   duplicates. `WebhookInboxService.accept` therefore does one thing only.
2. A webhook arriving **before** the provider reference is registered is parked
   and retried, never rejected — and **never creates a deposit**.
3. Terminal aggregate states make a repeat harmless even if a duplicate slips
   past the inbox.

Implementation notes that are not obvious from the diagram:

- **Deduplication is one statement**, `INSERT ... ON CONFLICT DO NOTHING`
  returning row count. An `exists()` then `insert` leaves a race in which two
  simultaneous deliveries both insert.
- **`PARKED` is not a failure state.** A row that exhausts its attempts stops
  being picked up (the sweep filters on `attempts`) but stays `PARKED` for a
  human. `DISCARDED` is only for what the aggregate refuses permanently.
- **Table names are plural** — `provider_webhook_inboxes`,
  `deposit_provider_references` — because the naming strategy pluralizes and
  `ddl-auto=validate` compares against that, not against the singular names the
  contract uses in prose.
- **`@EnableJpaAuditing` lives in `JpaAuditingConfiguration`, not on the
  application class.** On the application class it breaks every `@WebMvcTest`
  slice with "JPA metamodel must not be empty".

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
- `docs/guides/stripe-configuration.md` — which operation needs which Stripe
  property, the exact `IllegalStateException` each missing one throws, and how to
  supply the values locally. The authoritative list of what the service reads from
  the environment is `application.yaml` itself — the guide does not repeat it.
  Note that **Spring Boot does not read `.env` files**: local secrets go in a
  gitignored `application-local.yaml` (`SPRING_PROFILES_ACTIVE=dev,local`) or in
  the run configuration's environment variables.

The contract has a **"Decisiones pendientes"** section listing what is genuinely
undecided, each with the reason it was deferred rather than guessed. Card 7 closed
three of them: the port now imports the real domain value objects, `FailureReason`
travels as an enum instead of a `String`, and the inbox keeps a SHA-256 of the raw
payload rather than the payload — so there is no Stripe body to define a retention
policy for.

Two things Card 7 leaves open on purpose:

- **`DepositCommandService` does not exist yet** (Card 4). The inbox dispatches
  through Axon's `CommandGateway` directly, with a `TODO` on the single line that
  changes when the interface lands. The class diagram already shows the intended
  delegation.
- **Exhausted `PARKED` rows and `DISCARDED` rows have no owner.** They log an
  `error` and stay in the table. No alerting, no reprocessing tool.
