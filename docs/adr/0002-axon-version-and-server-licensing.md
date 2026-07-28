# ADR-0002: Versión de Axon Framework y licencia de Axon Server

- **Estado:** Aceptado
- **Fecha:** 2026-07-28
- **Contexto:** `vankoo-finance-service` / bounded context Finance
- **Decisores:** Salim y Anjali
- **Relacionado:** [ADR-0001](0001-axon-server-event-store-postgresql-read-model.md)

## Contexto

Hay que fijar dos cosas antes del bootstrap: qué versión de Axon Framework
usamos, y bajo qué licencia corre Axon Server.

La guía de la clase enseña el modelo de programación de **Axon Framework 4**:
`@Aggregate`, `@AggregateIdentifier`, `@CommandHandler` sobre el constructor,
`apply(...)` y `@EventSourcingHandler`. Axon Framework 5 existe y está GA, con un
modelo distinto, así que la elección no era evidente.

## Decisión

**Axon Framework 4.13.2**, y **Axon Server como instancia única bajo el plan
gratuito de AxonIQ**.

## Por qué Axon 4 y no Axon 5

Axon 5 está genuinamente publicado —el núcleo iba por **5.2.2** el 27 de julio de
2026, un día antes de esta decisión— y su modelo resuelve problemas reales:
elimina las dependencias de `ThreadLocal`, hace explícito el punto de emisión de
eventos y desacopla las fronteras de consistencia del diseño inicial.

Lo descartamos por un motivo concreto y verificable: **no existe un starter de
Spring Boot publicado para Axon 5.**

Comprobado contra el repositorio de Maven Central, no contra su índice de
búsqueda, que está desactualizado:

| Artefacto | Estado |
|---|---|
| `axon-messaging`, `axon-modelling`, `axon-eventsourcing` | **5.2.2** GA, publicado 2026-07-27 |
| `axon-framework-bom` | **5.2.2**, declara los módulos de Spring en `${project.version}` |
| `axon-spring-boot-starter` | solo `5.0.0-M2`, `M2.1`, `M3`, `5.0.0-preview` — **último publicado 2025-09-29** |
| `axon-spring-boot-starter-test` | **no existe** en Maven Central |

Dos consecuencias:

1. El BOM 5.2.2 promete un starter en 5.2.2 que **no está publicado**: pedirlo
   falla en resolución.
2. El starter lleva **diez meses parado** mientras el framework avanzaba de 5.0 a
   5.2. Eso sugiere que quien usa Axon 5 hoy no lo hace con autoconfiguración de
   Spring Boot, y por tanto hay poca ayuda disponible cuando algo falle.

Las alternativas dentro de Axon 5 tampoco convencen: usar el núcleo 5.2.2 sin
starter obliga a cablear la configuración a mano —fontanería de una versión en
obras, con caducidad corta— y fijarlo todo en `5.0.0-preview` sería construir
sobre una beta de hace diez meses, dos versiones menores por detrás.

**Y lo que gana Axon 5 no es lo que este proyecto necesita aprender.** Los
conceptos que importan —persistir solo eventos, rehidratar por replay, separar
modelos, proyecciones idempotentes— son idénticos en 4 y en 5. Todo el catálogo
de contratos es agnóstico de la versión.

## Compatibilidad con Spring Boot 4, verificada empíricamente

El pom padre de Axon 4.13.2 fija `spring.boot.version = 2.7.18`, lo que sugiere
incompatibilidad con nuestro Spring Boot 4.1. **Es solo su línea base de
compilación, no una restricción de ejecución.** Verificado arrancando la
aplicación:

```
Starting App using Java 25.0.1
 :: Spring Boot ::  (v4.1.0)
Started App in 0.405 seconds
```

Spring Boot 4.1.0 + Java 25 + `axon-spring-boot-starter` 4.13.2, con la
autoconfiguración de Axon activa. **No hay que bajar ni Spring Boot ni Java**, y
Finance se mantiene alineado con el resto de Vankoo, que va en Boot 4.0.x.

Comprobaciones adicionales sobre el artefacto: incluye
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
—el mecanismo de Boot 3+, no el `spring.factories` antiguo—, tiene clases tanto
`jakarta` como `javax`, y su bytecode apunta a Java 17, que corre en 25.

## Consecuencias del modelo de Axon 4

El código sigue **literalmente** las slides de la guía, sin traducción de por
medio:

```java
@Aggregate
public class Deposit {

    @AggregateIdentifier
    private String depositId;

    protected Deposit() {
    }

    @CommandHandler
    public Deposit(InitiateDepositCommand command) {
        // Invariantes ANTES de emitir, como en la guía
        if (command.getAmount().getAmountMinor() <= 0) {
            throw new InvalidDepositAmountException(command.getAmount());
        }
        apply(new DepositInitiatedEvent(/* ... */));
    }

    @EventSourcingHandler
    public void on(DepositInitiatedEvent event) {
        // Solo asigna estado. No valida, no lanza.
        this.depositId = event.getDepositId();
    }
}
```

Los comandos llevan `@TargetAggregateIdentifier` sobre `depositId` para el
enrutado. Los diagramas de la clase mapean uno a uno con el código.

### Serializador

Axon 4 usa **XStream** por defecto, que en Java 25 emite un aviso de
`sun.misc.Unsafe` deprecado —observado en la prueba de arranque—. Se configurará
**Jackson** en su lugar (`axon.serializer.general=jackson`), que además es lo que
queremos para los eventos en JSON.

### `@ProcessingGroup` explícito

Se mantiene el requisito del catálogo de contratos: la proyección debe declarar
`@ProcessingGroup("deposit-read-model")`. El nombre por defecto del processing
group es el del paquete del handler y queda grabado en el token store, así que
sin declararlo, mover el paquete provoca un replay completo de la proyección.

## Licencia de Axon Server

Leído en los [términos de servicio de AxonIQ, versión septiembre 2025](https://lp.axoniq.io/hubfs/2025%2009%20AXONIQ%20Terms%20of%20Service.pdf).

**Axon Framework no está sujeto a estos términos.** El contrato lo dice
explícitamente: *"For the avoidance of doubt, The core of Axon Framework is
licensed under Apache 2. And these Terms do not apply."*

**Axon Server sí lo está.** El contrato define:

> *"**Development** means use on a single Node, solely by developers testing code
> or use solely in a sandbox environment that is not accessed or in any way used
> by users of the production system. (...) **Any cluster that has more than one
> Node will entail use in either Production or Non-Production.**"*

Y excluye del uso no productivo: *"(i) any use in a live, customer-facing
environment; (ii) any use that directly supports business operations or generates
revenue; (iii) any use in testing or QA environments that validate software
intended for production deployment; (iv) any use where the Software processes,
stores, or transmits data that affects business decisions or customer
experiences."*

**Vankoo encaja en uso de desarrollo y evaluación:** proyecto académico, sin
usuarios reales, sin operación de negocio, sin ingresos, y sus datos no afectan
decisiones ni clientes.

Consecuencias que sí aplican:

- **Instancia única.** El clustering queda descartado: el contrato clasifica
  cualquier cluster de más de un nodo fuera del uso de desarrollo. Esto **corrige
  el ADR-0001**, que asumía cluster en producción.
- **Registro en el plan gratuito.** El texto dice que el uso de desarrollo *"es
  parte de todas las suscripciones AXONIQ sin coste adicional"*, lo que presupone
  tener alguna suscripción. El plan «Individual» es gratuito y cubre uso no
  productivo con 6 instancias de aplicación. Conviene darse de alta, no asumirlo
  por bajar la imagen de Docker.
- **Cláusula de auditoría (§15a).** AxonIQ puede solicitar por escrito una
  autocertificación en 15 días declarando dónde está desplegado el software y
  confirmando que no hubo uso productivo.

> **No existe ninguna limitación de 14 días de despliegue.** Aparecía en un
> resumen de buscador y se comprobó contra el PDF que **no está en el
> documento**: el único «14 days» del contrato se refiere al plazo para reclamar
> facturas. Se deja anotado porque es el tipo de dato que se propaga sin
> verificar.

### Si Vankoo dejara de ser académico

Dos caminos, escritos antes de necesitarlos:

1. Contratar un plan de producción, desde **$150/mes**.
2. Migrar el event store a **PostgreSQL** con `JpaEventStorageEngine`, soportado
   por Axon Framework y contemplado en la figura 6-8 de la guía, que muestra
   *SQL Database(s)* como Event Store alternativo. Sin techo de licencia.

## Alternativa considerada: PostgreSQL como event store desde el inicio

Evitaría cualquier límite de licencia, quitaría un componente stateful del stack
y haría el event store directamente inspeccionable con SQL. Se descarta para la
v1 porque el capítulo 6 de la guía desarrolla Axon Server y el objetivo del
proyecto es aprender siguiendo ese material. Queda registrada arriba como salida
si el proyecto cambiara de naturaleza.

## Referencias

- [Axon Framework 4 — Aggregates](https://docs.axoniq.io/axon-framework-reference/4.10/axon-framework-commands/modeling/aggregate/)
- [AXONIQ Terms of Service, septiembre 2025](https://lp.axoniq.io/hubfs/2025%2009%20AXONIQ%20Terms%20of%20Service.pdf)
- [AxonIQ Pricing](https://www.axoniq.io/pricing)
