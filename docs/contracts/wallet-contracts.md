# Contrato del agregado Wallet

- **Estado:** Propuesto para revisión conjunta
- **Versión:** 0.1-inicial
- **Fecha:** 2026-08-21
- **Bounded Context:** Finance
- **Servicio:** `vankoo-finance-service`
- **Depende de:** [`finance-contracts.md`](finance-contracts.md) (agregado `Deposit`, ya implementado)

## Propósito

`Deposit` termina en `DepositSucceededEvent` y no acredita saldo. El saldo es
una invariante de negocio —«no puedes invertir más de lo que tienes»— y no
puede validarse contra una proyección (ADR-0001), así que necesita su propio
agregado event-sourced: `Wallet`.

Este documento sigue el mismo formato que `finance-contracts.md`, con el mismo
nivel de detalle que ese contrato tenía antes de implementar `Deposit`: fija
las decisiones de diseño, no los comandos/eventos exactos — eso se termina de
precisar al escribir el código, igual que pasó con `Deposit`.

Va dentro de `Finance`, no en otro microservicio: saldo y recargas comparten
invariante contable, y separarlos obligaría a coordinar dos servicios para
acreditar una recarga con consistencia eventual en algo que se quiere
transaccional. `Wallet` recibe `DepositSucceededEvent` por el **event bus
interno de Axon**, no por Kafka.

---

## Identidad y multi-moneda

**Un `Wallet` por `(accountId, currency)`, no un `Wallet` con saldo-por-moneda
adentro.** Un inversionista con PEN y USD tiene dos agregados `Wallet`
distintos. Se descarta el diseño de un solo `Wallet` multi-moneda porque
obligaría a resolver tipo de cambio, redondeo y fuente de tasas dentro del
propio agregado de saldo — complejidad que hoy no hace falta.

**`AccountId` no es lo mismo que `WalletId`.** `AccountId` es la identidad del
inversionista: llega de otro servicio (Profile), Finance nunca la genera. Con
`Wallet` por moneda, un mismo `AccountId` puede tener hasta dos `WalletId`
distintos — así que `Wallet` necesita su propia identidad, nueva.

**`WalletId` se deriva determinísticamente de `(accountId, currency)`**, no es
un UUID aleatorio como `DepositId`. Esto evita necesitar una tabla de
traducción tipo `deposit_provider_references`: esa tabla existe porque traduce
un identificador *externo y opaco* (de Stripe) que Finance no controla —
`accountId` y `currency` son datos que Finance ya tiene en la mano, no hay
nada externo que traducir. Con el id derivado, «¿existe el `Wallet`?» es un
`existsById` directo.

**Sin financiamiento cruzado entre monedas en esta fase.** Una recarga en PEN
solo abona el `Wallet` en PEN; una recarga en USD solo abona el `Wallet` en
USD. Si más adelante se quiere permitir financiar una factura en PEN con saldo
en USD, se modela como una operación explícita de **intercambio de moneda**
entre los dos `Wallet` del mismo inversionista — un movimiento más de la lista
de abajo, con su propio contrato de tasa y redondeo — no como una variante de
la recarga.

---

## Creación del Wallet

⚠️ **Esta sección describe una solución temporal.** Se reemplaza en cuanto
termine el trabajo de Kafka de Anjali («Publicar eventos de integración»,
que deja realmente montada la infraestructura de Kafka en el servicio —
hoy ni siquiera está la dependencia).

**Por ahora, temporalmente:** `Wallet` se crea de forma perezosa, dentro de
Finance, la primera vez que el inversionista deposita en una moneda dada. El
mismo `@EventHandler` que reacciona a `DepositSucceededEvent`:

1. Calcula el `WalletId` derivado de `(accountId, currency)`.
2. Si no existe (`existsById`), despacha `OpenWalletCommand` (crea el
   `Wallet` con saldo 0).
3. Despacha `CreditWalletCommand`.

Sigue siendo un `@EventHandler` plano, no una saga: no hay espera ni nada que
compensar, los dos pasos ocurren en secuencia inmediata dentro del mismo
manejo de evento.

**Modelo objetivo, cuando el Kafka de Anjali esté listo:** `Wallet` nace
cuando Profile crea la cuenta del inversionista, no cuando deposita. Finance
escuchará el evento de creación de cuenta vía Kafka
(`interfaces/messaging/eventhandlers`, vacío hoy) y abrirá el/los `Wallet`
correspondientes ahí. Sigue sin necesitar saga — sigue siendo «cuando pasa X,
hacé Y» — pero introduce una carrera real entre dos streams asíncronos
independientes: `DepositSucceededEvent` podría llegar antes de que Finance
termine de procesar el evento de creación de cuenta. La solución es la misma
que ya existe para el caso equivalente de los webhooks de Stripe: **aparcar y
reintentar**, nunca rechazar ni crear el crédito sin destino — mismo
mecanismo que `WebhookInboxService`, aplicado a este otro par de eventos.

---

## Snapshots

`Deposit` no los necesita porque muere joven (2-4 eventos). `Wallet` acumula
un evento por cada movimiento y vive para siempre — es el candidato que la
sección de Snapshots de `finance-contracts.md` ya señalaba para reevaluar.

**Umbral inicial: cada 100 eventos**, vía
`EventCountSnapshotTriggerDefinition` de Axon. Es un número provisional,
elegido para que quede escrito y no como un silencio del contrato — se ajusta
si el comportamiento real difiere.

Un snapshot **nunca borra eventos**: es un archivo adicional y descartable
para acelerar la carga del agregado (misma categoría que `deposit_views`,
pero para reconstrucción del agregado en vez de para consultas). El event
store sigue guardando el historial completo para siempre; la auditoría
siempre lee de ahí, nunca del snapshot.

---

## Concurrencia

Dos comandos contra el mismo `Wallet` casi al mismo tiempo (ej. dos
inversiones simultáneas del mismo inversionista) es el caso real que
`Deposit` no tenía. La protección es la que Axon ya da de fábrica: la misma
concurrencia optimista que prueba `AggregateOptimisticConcurrencyTest` sobre
`Deposit` — el segundo comando que intenta escribir contra una versión
desactualizada del agregado es rechazado automáticamente, sin código de
locking adicional.

La regla de negocio (`amount > balance` → rechazar) vive en el
`@CommandHandler` del comando de débito, protegida por esa serialización.

**Decisión pendiente, de aplicación, no del agregado:** qué le devuelve el
sistema al inversionista cuando su comando choca por concurrencia —
reintento automático transparente, o error explícito de "intentá de nuevo".
Se define al implementar la capa de interfaces, no acá.

---

## Alcance contable — tipos de movimiento

Además de la recarga (crédito, ya cubierto arriba), `Wallet` reconoce:

| Movimiento | Efecto | Origen |
|---|---|---|
| Recarga (`Deposit` exitoso) | Crédito | `DepositSucceededEvent` (interno) |
| Inversión | Débito | comando del bounded context Investment (por definir su contrato) |
| Retiro | Débito | comando propio de `Wallet` |
| Comisión | Débito | comando propio de `Wallet` |
| Reverso | Crédito o débito compensatorio | corrige un movimiento previo ya aplicado (ej. chargeback de Stripe sobre una recarga ya acreditada) |

Cada uno es un evento propio del agregado, en el mismo espíritu que los 7
eventos de `Deposit` — el diseño detallado de cada comando/evento queda para
"Implementar el agregado Wallet", no para este contrato.

---

## Pendiente para cuando se implemente

- Contrato exacto de comandos/eventos de `Wallet` (`OpenWalletCommand`,
  `CreditWalletCommand`, `DebitWalletCommand`, y sus eventos correspondientes).
- Cómo Investment solicita un débito (comando directo, o su propio evento que
  Finance escucha).
- Atributos de `Wallet` en `docs/uml/finance-domain-model-diagram.puml` (hoy
  dibujado a propósito sin ellos).
