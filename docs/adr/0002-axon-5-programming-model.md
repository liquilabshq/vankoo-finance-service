# ADR-0002: Axon Framework 5 y traducción del modelo de la guía de clase

- **Estado:** Aceptado
- **Fecha:** 2026-07-26
- **Contexto:** `vankoo-finance-service` / bounded context Finance
- **Decisores:** Salim y Anjali
- **Relacionado:** [ADR-0001](0001-axon-server-event-store-postgresql-read-model.md)

## Contexto

La guía de la clase (`event-sourcing-guide`, capítulo *Cargo Tracker: Axon
Framework*) enseña el modelo de programación de **Axon Framework 4**:
`@Aggregate`, `@AggregateIdentifier`, `@CommandHandler` sobre el constructor,
`apply(...)` y `@EventSourcingHandler`.

Axon Framework 5 rehízo ese modelo. Los dos elementos centrales de la guía —el
command handler en el constructor y el `apply()` estático— **ya no existen**.

Debemos elegir versión y, si elegimos la 5, dejar escrito cómo se traduce cada
concepto de la guía, para que los diagramas de la clase sigan mapeando al código
que escribimos.

## Decisión

**Adoptamos Axon Framework 5.x**, y documentamos aquí la traducción del modelo
de la guía.

La decisión es deliberada y asume un coste: menos material de referencia y
menos ejemplos que con Axon 4. Se acepta porque el modelo de Axon 5 elimina
dependencias de `ThreadLocal`, hace explícito el punto de emisión de eventos y
no ata las fronteras de consistencia al diseño inicial —lo que importa cuando
aparezca el agregado `Wallet` sobre los mismos eventos.

**La versión mayor queda fijada en este ADR.** No se mezcla documentación de
Axon 4 y Axon 5: toda referencia debe apuntar a la línea 5.x.

## Traducción del modelo de la guía

| Concepto de la guía (Axon 4) | Equivalente en Axon 5 |
|---|---|
| `@Aggregate` | `@EventSourced(idType = String.class)` + `@EventSourcedEntity(tagKey = "depositId")` |
| `@AggregateIdentifier` sobre el campo id | `@EventTag` sobre el campo id **de cada evento** + `tagKey` en la entidad |
| `@CommandHandler` en el **constructor** | `@CommandHandler` en un **método factory estático** |
| Constructor vacío `protected Cargo() {}` | Constructor sin argumentos anotado con `@EntityCreator` |
| `apply(new XxxEvent(...))` (estático, ThreadLocal) | `eventAppender.append(new XxxEvent(...))`, con `EventAppender` **inyectado como parámetro** |
| `@TargetAggregateIdentifier` en el comando | `@Command(routingKey = "depositId")` en el comando |
| `@EventSourcingHandler public void on(...)` | `@EventSourcingHandler public void on(...)` — **sin cambios** |
| Rehidratación por `aggregateIdentifier` | Sourcing por `EventCriteria` sobre tags; Axon la deriva sola en el caso simple |
| Concurrencia optimista por `sequenceNumber` | *Consistency marker* + `AppendCondition`, gestionado por el framework |

### El mecanismo de publicación del capítulo 5 desaparece por completo

Esta es la diferencia conceptual más grande, mayor que cualquier cambio de
anotación, y conviene dejarla explícita porque alguien que venga del capítulo 5
buscará estas piezas y no las encontrará.

El capítulo 5 publica eventos en seis pasos (figura 5-41):

```java
// 1. El agregado extiende la plantilla de Spring Data
public class Cargo extends AbstractAggregateRoot<Cargo> {
    public void addDomainEvent(Object event) { registerEvent(event); }
}

// 2. El command service persiste el agregado completo
cargoRepository.save(cargo);

// 3. Spring publica los eventos registrados al confirmar la transacción
@TransactionalEventListener
public void handleCargoBookedEvent(CargoBookedEvent e) {
    cargoEventSource.cargoBooking().send(...);
}
```

Nada de eso existe en Finance:

| Pieza del capítulo 5 | En Finance |
|---|---|
| `extends AbstractAggregateRoot<T>` | no se usa; `@EventSourcedEntity` lo sustituye |
| `registerEvent(...)` / `addDomainEvent(...)` | `eventAppender.append(...)` |
| `repository.save(aggregate)` | **no existe** — solo se persiste el evento, nunca el agregado |
| `@TransactionalEventListener` | el event processor de Axon lee el stream del event store |
| Publicación acoplada a la transacción del repositorio | publicación desacoplada, at-least-once, con reintentos |

La consecuencia práctica más importante: en el capítulo 5 el evento se publica
**dentro de la transacción** del repositorio, así que persistencia y publicación
fallan juntas. En Finance el evento ya está en el event store cuando el processor
intenta publicarlo, así que **son dos operaciones separadas**. De ahí vienen los
requisitos de idempotencia, reintentos y dead-letter del catálogo de contratos:
no son burocracia, son la consecuencia de haber desacoplado estos dos pasos.

El flujo resultante, en sus dos variantes —creación sin replay y modificación con
replay— está dibujado en
[`uml/finance-event-sourcing-flow-diagram.puml`](../uml/finance-event-sourcing-flow-diagram.puml).

### Lo que no cambia

Estos elementos de la guía siguen siendo válidos tal cual, y son la mayor parte
de lo que enseñan las slides:

- La separación **Command Model / Query Model** de CQRS.
- Que se persiste **solo el evento**, nunca el estado del agregado.
- Que el estado se reconstruye por **replay**.
- Que la **validación de invariantes va en el command handler, antes de emitir**,
  y que el `@EventSourcingHandler` solo asigna estado y nunca lanza excepciones.
- Que el event store hace además de **event router**.
- Que el Read Model se actualiza por **suscripción a eventos**.
- Los eventos son **hechos en pasado**.

### Continuidad importante

La guía coloca los `@CommandHandler` **dentro del agregado**. Axon 5 mantiene esa
posibilidad mediante los *stateful command handlers*: los handlers pueden vivir
sobre la propia entidad event-sourced. **Conservamos ese estilo**, porque es el
que enseñan las figuras de la clase y el que refleja que el agregado es quien
decide.

Axon 5 permite además *stateless command handlers* fuera de la entidad. No los
usamos en la v1 salvo justificación explícita en la PR.

### Ejemplo de referencia

Forma esperada del agregado, con la traducción aplicada:

```java
@EventSourced(idType = String.class)
@EventSourcedEntity(tagKey = "depositId")
public class Deposit {

    private String depositId;
    private String accountId;
    private Money amount;
    private Provider provider;
    private DepositStatus status;
    private String providerDepositId;

    @EntityCreator
    protected Deposit() {
    }

    @CommandHandler
    public static String handle(InitiateDepositCommand command,
                                EventAppender eventAppender) {
        // Invariantes: se validan ANTES de emitir, como en la guía.
        if (command.amount().amountMinor() <= 0) {
            throw new InvalidDepositAmountException(command.amount());
        }
        eventAppender.append(new DepositInitiatedEvent(
                command.depositId(),
                command.accountId(),
                command.amount().amountMinor(),
                command.amount().currency(),
                command.provider()));
        return command.depositId();
    }

    @EventSourcingHandler
    public void on(DepositInitiatedEvent event) {
        // Solo asigna estado. No valida, no lanza.
        this.depositId = event.depositId();
        this.accountId = event.accountId();
        this.amount = new Money(event.amountMinor(), event.currency());
        this.provider = event.provider();
        this.status = DepositStatus.PENDING;
    }
}
```

Y el evento, con el tag que Axon 5 necesita para el sourcing:

```java
@Event(name = "DepositInitiatedEvent")
public record DepositInitiatedEvent(
        @EventTag(key = "depositId") String depositId,
        String accountId,
        long amountMinor,
        String currency,
        Provider provider) {
}
```

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| **`@EventTag` olvidado en un evento nuevo.** El sourcing falla en silencio: el agregado se rehidrata incompleto sin lanzar error. | Test obligatorio de replay por cada evento nuevo. Es la trampa más reportada de la migración a Axon 5 y la incluimos en la Definition of Done. |
| Menos ejemplos y respuestas públicas que con Axon 4. | Este ADR es la referencia interna. Toda desviación se documenta aquí. |
| La guía de clase no coincide literalmente con el código. | La tabla de traducción de arriba es el puente. Los diagramas de la clase se conservan; cambia el nombre de la anotación, no el concepto. |
| Mezclar documentación 4.x y 5.x al buscar. | Versión mayor fijada en este ADR; toda referencia apunta a la línea 5.x. |

## Consecuencias

- El dominio depende de anotaciones de Axon (`@EventSourced`, `@EventTag`,
  `@CommandHandler`). Es una dependencia de **modelado**, no de
  infraestructura: el dominio sigue sin conocer Axon Server, PostgreSQL, Kafka,
  Stripe ni HTTP, que es la regla que exige la guía.
- Los tests de agregado usan las fixtures de Axon 5, no las de Axon 4.
- La versión exacta de Axon, Spring Boot, Java y Axon Server se fija durante el
  bootstrap (Tarjeta 2), respetando la versión mayor decidida aquí.

## Alternativa considerada: Axon Framework 4.x

Descartada. Habría alineado el código 1:1 con las slides y ofrece mucho más
material de referencia. Se descarta por decisión del equipo a favor de la
versión soportada actualmente y de su modelo de fronteras de consistencia, que
encaja mejor con la evolución prevista hacia `Wallet`. El coste —traducir la
guía— se paga una vez y queda resuelto en este documento.

## Referencias

- [Axon Framework 5 — Command Handlers](https://docs.axoniq.io/axon-framework-reference/5.0/commands/command-handlers/)
- [Axon Framework 5 — Event Store Internals: tags y EventCriteria](https://docs.axoniq.io/axon-framework-reference/5.1/events/event-store-internals/)
- [Axon Framework 5 — Problemas de arquitectura resueltos](https://docs.axoniq.io/axon-framework-reference/5.0/migration/solved-architecture-choices/)
- [Migrating from Axon Framework 4 to 5: What We Learned](https://dev.to/petrmacek/migrating-from-axon-framework-4-to-5-what-we-learned-50db)
