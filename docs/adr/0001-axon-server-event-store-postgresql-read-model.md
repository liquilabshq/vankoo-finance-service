# ADR-0001: Axon Server como Event Store y PostgreSQL como Read Model

- **Estado:** Aceptado
- **Fecha:** 2026-07-25
- **Contexto:** `vankoo-finance-service` / bounded context Finance
- **Decisores:** Salim y Anjali

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
     Axon Server, denominado `finance`.
   - En producción se utilizará un cluster de Axon Server para evitar que una
     única instancia sea un punto único de falla. En desarrollo podrá utilizarse
     una instancia standalone.

2. **PostgreSQL será el Read Model durable de Finance.**
   - Las proyecciones consumirán eventos de Axon Server y mantendrán tablas
     optimizadas para las consultas de la API.
   - El Read Model será reconstruible eliminando sus tablas y reproduciendo el
     historial de eventos.
   - El Read Model no será la fuente de verdad ni se utilizará para validar
     invariantes críticas de los agregados.

3. **Kafka será el broker de mensajería de integración.**
   - Se utilizará para publicar eventos que deban cruzar el límite del bounded
     context Finance.
   - No se utilizará como mecanismo principal para distribuir eventos entre las
     instancias internas de Finance; esa responsabilidad corresponde a Axon
     Server.

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
- El cluster de Axon Server requiere configuración de persistencia, backups,
  control de acceso y TLS para producción.
- Al reutilizar eventos de dominio como contratos Kafka, los cambios del evento
  deben considerar simultáneamente la rehidratación de agregados y la
  compatibilidad con consumidores externos.
- La publicación en Kafka y la persistencia en Axon Server no forman
  automáticamente una única transacción distribuida; se deberán definir
  reintentos, deduplicación y manejo de mensajes fallidos.

## Alternativas consideradas

### PostgreSQL como Event Store

Se descarta como opción principal porque el despliegue objetivo utiliza Axon
Server para el almacenamiento y routing distribuido. PostgreSQL seguirá siendo
el almacenamiento del Read Model.

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

- Catálogo inicial de agregados, comandos y eventos.
- Identificación de los eventos de dominio que también serán contratos Kafka.
- Convención de nombres, esquema y versionado de eventos.
- Estrategia de snapshots y replay.
- Configuración de proyecciones, token store, reintentos y dead-letter queue.
- Configuración del cluster de Axon Server, backups, TLS y control de acceso.
- Actualización de `finance-technical-story.md` para reflejar esta decisión.

## Referencias

- [Axon Framework: infraestructura de eventos](https://docs.axoniq.io/axon-framework-reference/5.1/events/infrastructure/)
- [Axon Framework: command bus distribuido](https://docs.axoniq.io/axon-framework-reference/5.0/commands/infrastructure/)
- [Axon Server: instalación y clustering](https://docs.axoniq.io/axon-server-reference/v2026.0/axon-server/installation/local-installation/)
