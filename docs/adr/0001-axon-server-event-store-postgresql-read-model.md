# ADR-0001: Axon Server como Event Store y PostgreSQL como Read Model

- **Estado:** Aceptado
- **Fecha:** 2026-07-25 (revisado 2026-07-26, 2026-07-29)
- **Contexto:** `vankoo-finance-service` / bounded context Finance
- **Decisores:** Salim y Anjali
- **Complementado por:** [ADR-0002 — Versión de Axon y licencia de Axon Server](0002-axon-version-and-server-licensing.md)

## Contexto

Finance utilizará Event Sourcing y CQRS, y será desplegado con múltiples
instancias de `finance-service`. Los agregados deben reconstruirse a partir de
su historial de eventos, mientras que las consultas necesitan modelos de
lectura optimizados y reconstruibles.

También es necesario comunicar hechos de negocio a otros bounded contexts de
Vankoo mediante Kafka. La solución debe conservar la separación por capas de
la guía de la clase, manteniendo los detalles de Axon, Kafka y persistencia
fuera del dominio.

## Decisión

Adoptamos la siguiente arquitectura:

1. **Axon Server será el Event Store y el router interno de Axon.**
   - Guardará los eventos de los agregados event-sourced y permitirá
     rehidratarlos mediante replay.
   - Gestionará la distribución interna de comandos y eventos entre las
     instancias de `finance-service`.
   - Todas las instancias de Finance se conectarán al mismo contexto lógico de
     Axon Server, denominado **`default`**. La edición gratuita de Axon Server
     (Standard/Developer) limita a un solo contexto por instancia
     (`maxContexts: 1`) y lo fija con ese nombre — no admite renombrarlo a
     `finance` ni crear uno adicional, eso es exclusivo de Enterprise.
     Verificado arrancando un Axon Server standalone real e inspeccionando
     `/v1/public/me`. Detalle en el
     [ADR-0002](0002-axon-version-and-server-licensing.md).
   - Se desplegará como **instancia única (standalone)**, tanto en desarrollo
     como en el despliegue académico. **El clustering queda descartado**: los
     términos de AxonIQ clasifican cualquier cluster de más de un nodo fuera del
     uso de desarrollo del plan gratuito. Detalle en el
     [ADR-0002](0002-axon-version-and-server-licensing.md).
   - Consecuencia asumida: Axon Server es un **punto único de falla**. Es
     aceptable para un proyecto académico; dejaría de serlo en operación real.

2. **PostgreSQL será el Read Model durable de Finance.**
   - Las proyecciones consumirán eventos de Axon Server y mantendrán tablas
     optimizadas para las consultas de la API.
   - El Read Model será reconstruible eliminando sus tablas y reproduciendo el
     historial de eventos.
   - El Read Model no será la fuente de verdad ni se utilizará para validar
     invariantes críticas de los agregados.
   - Las proyecciones de consulta viven en el schema **`finance_read_model`**.

2b. **Las tablas operativas del borde viven en un schema separado
    `finance_ops`.**
   - Incluyen el inbox de webhooks, la idempotencia de comandos de cliente y la
     resolución de referencias del proveedor.
   - Se separan del Read Model porque **sí** participan en decisiones de
     admisión, y el punto 2 prohíbe que el Read Model haga eso. Mezclarlas en el
     mismo schema haría ambigua la regla.
   - No son fuente de verdad: son reconstruibles desde el historial de eventos y
     el log de webhooks. Su pérdida degrada las garantías de deduplicación, pero
     no corrompe el historial.

3. **Kafka será el broker de mensajería de integración.**
   - Se utilizará para publicar eventos que deban cruzar el límite del bounded
     context Finance.
   - No se utilizará como mecanismo principal para distribuir eventos entre las
     instancias internas de Finance; esa responsabilidad corresponde a Axon
     Server.
   - **Los agregados de Finance se comunican entre sí por el event bus de Axon,
     no por Kafka.** `Wallet`/`Ledger` será un segundo agregado de este mismo
     bounded context —saldo y recargas comparten invariante contable, y separarlos
     obligaría a coordinar dos servicios para acreditar una recarga— así que
     recibirá `DepositSucceededEvent` internamente.
   - **Consecuencia asumida:** en la v1, Kafka queda provisionado sin consumidor.
     Se publica desde el principio para fijar el contrato antes de que exista el
     primer consumidor externo, no porque haya uno. La Tarjeta 8 puede posponerse
     sin bloquear ningún flujo de producto.

4. **Los eventos de dominio públicos también serán eventos de integración.**
   - Un hecho de negocio seleccionado para salir por Kafka podrá utilizar el
     mismo payload y contrato que el evento generado por el agregado.
   - No se crearán DTOs de integración paralelos para esos eventos durante la
     primera versión.
   - Los eventos puramente internos no se publicarán automáticamente en Kafka.
   - El `EventMessage` de Axon y el registro/envelope de Kafka son envoltorios
     técnicos; no constituyen nuevos eventos de negocio.

5. **Redis queda fuera del alcance inicial.**
   - Podrá evaluarse posteriormente como caché o Read Model de alta velocidad.
   - No será utilizado como fuente de verdad ni como requisito de la primera
     implementación.

## Flujo de referencia

```text
Command
   |
   v
Aggregate rehidratado desde Axon Server
   |
   v
Domain Event
   |
   +--> Axon Server Event Store
   |
   +--> Proyección --> PostgreSQL Read Model
   |
   +--> Kafka, si el evento es público para otro bounded context
```

Este flujo, con los nombres reales de Finance y los dos suscriptores del mismo
stream, está en
[`uml/finance-cqrs-event-sourcing-diagram.puml`](../uml/finance-cqrs-event-sourcing-diagram.puml).

## Reglas para los eventos publicados en Kafka

Un evento de dominio podrá publicarse también en Kafka únicamente si:

- representa un hecho de negocio relevante para otro bounded context;
- su payload no expone detalles internos de Axon ni de infraestructura;
- no contiene información sensible que no deba cruzar el límite del contexto;
- tiene un contrato documentado y versionado;
- los consumidores pueden procesarlo de forma idempotente.

La reutilización del evento no implica que todos los eventos internos de Axon
se conviertan en contratos públicos. La decisión de publicar debe formar parte
del catálogo de contratos de Finance.

## Consecuencias positivas

- La arquitectura coincide con el modelo de Event Sourcing y CQRS de la guía.
- Las múltiples instancias de Finance comparten una única fuente de verdad y
  pueden enrutar comandos mediante Axon Server.
- Las consultas no dependen de reconstruir agregados desde todo su historial.
- Los eventos públicos mantienen un modelo sencillo y evitan mapeos duplicados
  durante la primera versión.
- Los Read Models pueden reconstruirse ante cambios de proyección.

## Consecuencias y riesgos

- Axon Server se convierte en una dependencia crítica de ejecución y operación.
- Al ser instancia única, Axon Server es un punto único de falla y requiere
  configuración de persistencia y backups propios, separados de los de PostgreSQL.
- El plan gratuito de AxonIQ cubre uso de desarrollo y evaluación, no producción.
  Si Vankoo dejara de ser académico habría que contratar plan (desde $150/mes) o
  migrar el event store a PostgreSQL con `JpaEventStorageEngine`.
- Al reutilizar eventos de dominio como contratos Kafka, los cambios del evento
  deben considerar simultáneamente la rehidratación de agregados y la
  compatibilidad con consumidores externos.
- La publicación en Kafka y la persistencia en Axon Server no forman
  automáticamente una única transacción distribuida; se deberán definir
  reintentos, deduplicación y manejo de mensajes fallidos.

## Alternativas consideradas

### PostgreSQL como Event Store

Se descarta como opción principal porque el capítulo 6 de la guía desarrolla
Axon Server y el objetivo del proyecto es aprender siguiendo ese material.
PostgreSQL seguirá siendo el almacenamiento del Read Model.

Queda registrada como **salida** si el proyecto dejara de ser académico: la
figura 6-8 de la guía contempla *SQL Database(s)* como Event Store, y Axon
Framework lo soporta vía `JpaEventStorageEngine`, sin techo de licencia. Ver
[ADR-0002](0002-axon-version-and-server-licensing.md).

### Kafka para la distribución interna de Finance

Se descarta porque mezclaría el transporte de integración con la infraestructura
interna de Axon y complicaría el routing de comandos hacia los agregados.

### Axon Server como Read Model

Se descarta. Axon Server almacena y distribuye el historial de eventos, pero no
reemplaza las proyecciones denormalizadas necesarias para las consultas de
Finance. El Read Model continuará en PostgreSQL.

### Redis como Read Model inicial

Se descarta temporalmente para reducir componentes operativos. Podrá añadirse
como optimización cuando existan necesidades concretas de latencia o volumen de
consultas.

## Decisiones pendientes derivadas

- ~~Catálogo inicial de agregados, comandos y eventos~~ → definido en
  [`docs/contracts/finance-contracts.md`](../contracts/finance-contracts.md).
- ~~Identificación de los eventos de dominio que también serán contratos
  Kafka~~ → definida en el catálogo de contratos.
- ~~Estrategia de snapshots~~ → sin snapshots en la v1; decisión y motivo en el
  catálogo de contratos.
- ~~Versión mayor de Axon Framework~~ → fijada en el
  [ADR-0002](0002-axon-version-and-server-licensing.md).
- Convención de nombres, esquema y versionado de eventos.
- Configuración de proyecciones, token store, reintentos y dead-letter queue.
- Configuración de backups, TLS y control de acceso de la instancia de Axon Server.
- Política de retención del payload crudo en el inbox de webhooks.
- Contrato del agregado `Wallet`/`Ledger` que consumirá `DepositSucceededEvent`
  y acreditará el saldo del inversionista.

## Referencias

Todas las referencias de Axon Framework apuntan a la línea **4.x**, fijada en el
[ADR-0002](0002-axon-version-and-server-licensing.md).

- [Axon Framework 4: infraestructura de eventos](https://docs.axoniq.io/axon-framework-reference/4.11/events/infrastructure/)
- [Axon Framework 4: agregados](https://docs.axoniq.io/axon-framework-reference/4.10/axon-framework-commands/modeling/aggregate/)
- [Axon Server: instalación y clustering](https://docs.axoniq.io/axon-server-reference/v2026.0/axon-server/installation/local-installation/)
