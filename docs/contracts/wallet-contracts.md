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
| Inversión | Débito | `DebitWalletCommand`, despachado por `POST /api/v1/accounts/{accountId}/wallets/{currency}/debits` a pedido del propio inversionista (ver «Débito por REST» abajo) |
| Retiro | Débito | comando propio de `Wallet` |
| Comisión | Débito | comando propio de `Wallet` |
| Reverso | Crédito o débito compensatorio | corrige un movimiento previo ya aplicado (ej. chargeback de Stripe sobre una recarga ya acreditada) |

Cada uno es un evento propio del agregado, en el mismo espíritu que los 7
eventos de `Deposit` — el diseño detallado de cada comando/evento queda para
"Implementar el agregado Wallet", no para este contrato.

---

## Lectura (Read Model)

**El saldo se consulta por moneda**, nunca agregado entre monedas — coherente
con que cada `Wallet` ya vive scoped a `(accountId, currency)`. El cliente
siempre especifica la moneda.

```text
GET  /api/v1/accounts/{accountId}/wallets/{currency}            -> saldo
GET  /api/v1/accounts/{accountId}/wallets/{currency}/movements  -> historial paginado
POST /api/v1/accounts/{accountId}/wallets/{currency}/debits     -> débito (escritura, ver abajo)
```

**`getWalletBalance` responde `404 wallet-not-found`** cuando el wallet no
existe todavía — caso normal dado que se abre perezosamente, no un error. **`listWalletMovements`
nunca responde `404`**: una cuenta sin wallet en esa moneda recibe `200` con
página vacía, simétrico con `listDeposits`, que tampoco falla para una cuenta
sin depósitos. El primero direcciona un recurso puntual; el segundo, una
colección.

**Dos tablas en `finance_read_model`, dos formas de proyección** desde los
mismos tres eventos:

- `wallet_views` — upsert en el lugar, una fila por wallet, mismo patrón que
  `deposit_views` (guard `last_event_id`/`alreadyApplied` contra redelivery).
- `wallet_movements` — append-only, una fila nueva por evento, nunca se
  actualiza. Es la primera proyección append-only del servicio: no hay fila
  previa contra la que comparar "¿ya se aplicó?", así que la barrera de
  deduplicación es `insertIfAbsent` (`UNIQUE(event_id)` + `ON CONFLICT DO
  NOTHING`), el mismo idioma que ya usan las tablas de `finance_ops`, aplicado
  acá por primera vez a una tabla de lectura.

Una sola clase (`WalletProjection`) escucha los tres eventos y mantiene las
dos tablas en la misma transacción — no dos processing groups separados.

**Vocabulario de movimiento del lado de lectura, distinto del de escritura.**
`WalletMovementType` (el que usa `DebitWalletCommand`) solo tiene motivos de
débito — `RECARGA` no está ahí porque acreditar es su propio comando. El
historial necesita etiquetar también los créditos, así que expone su propio
catálogo (`RECARGA`, `INVERSION`, `RETIRO`, `COMISION`) más una dirección
(`CREDIT`/`DEBIT`) explícita.

**`GetWalletBalanceQuery`/`ListWalletMovementsQuery` llevan `(accountId,
currency)` en crudo, no un `WalletId` ya derivado** — a diferencia de
`GetDepositByIdQuery`, acá el id es una función pura de esos dos datos
(`WalletId.derive`, sin I/O), así que forzar a cada llamador a derivarlo
primero solo repite lógica sin necesidad. `WalletProjection` deriva el
`WalletId` internamente en cada handler de query.

---

## Débito por REST (Tarjeta 55)

**Investment no pide el débito: lo pide el inversionista, desde el móvil,
antes de invertir.** La app llama primero a Finance para bajar el saldo, y
recién con el `debitId` que Finance le devuelve llama a Investment, pasándolo
como `transactionId` — que Investment ya trata como clave de idempotencia de la
participación. Así ningún bounded context despacha comandos del otro.

```text
POST /api/v1/accounts/{accountId}/wallets/{currency}/debits
Idempotency-Key: <opaca, elegida por el cliente>
X-User-Id: <inyectado por el gateway>

{ "amountMinor": 500000, "reason": "INVERSION" }

201 Created
{ "debitId": "…", "walletId": "…", "accountId": "…",
  "currency": "PEN", "amountMinor": 500000, "reason": "INVERSION" }
```

- La moneda y la cuenta van en el path, no en el body: no hay nada que pueda
  contradecirse. `reason` es un `WalletMovementType` (`INVERSION` para la
  app).
- **Síncrono.** `WalletCommandServiceImpl` hace `sendAndWait`: si responde
  `201`, el saldo ya bajó. El body se construye de los inputs del comando,
  nunca del Read Model (la proyección corre aparte y puede ir atrás).
- **Idempotencia**, calcada de `POST /deposits`: `Idempotency-Key`
  obligatoria, barrera `finance_ops.wallet_debit_command_idempotencies`
  (`UNIQUE (account_id, idempotency_key)`, `INSERT … ON CONFLICT DO NOTHING`,
  insert primero y despacho después, `@Transactional` abarcando el
  `sendAndWait`). Misma clave y mismo contenido → mismo `201` y el **mismo
  `debitId`**, sin volver a debitar. Misma clave y contenido distinto →
  `409 idempotency-key-conflict`. Un débito rechazado (saldo insuficiente)
  revierte la reserva de la clave: el inversionista recarga y reintenta con
  la misma.
- **Referencia en el evento.** `DebitWalletCommand` lleva `DebitId` y
  `WalletDebitedEvent` ganó `debitId` como último campo, **opcional**: los
  eventos ya persistidos cargan con `null` y nada se renombró. La proyección
  lo copia a `wallet_movements.debit_id` y `GET …/movements` lo expone como
  `debitId` en cada movimiento de débito.
- **Errores**, en `application/problem+json` con `code` (ver
  `finance-contracts.md`, «Formato de errores HTTP»): `400 invalid-request` /
  `validation-failed`, `403 forbidden`, `404 wallet-not-found` (nunca hubo
  depósito en esa moneda: no hay nada que debitar), `409 insufficient-balance`
  (distinguible del otro 409 por el `code`).

**Las excepciones del agregado no cruzan Axon Server como tales.** El bus de
comandos es `AxonServerCommandBus`: lo que el `@CommandHandler` lanza llega
al `sendAndWait` como un `CommandExecutionException` genérico, sin la clase
original. `CommandRejectionHandlerInterceptor` (lado handler) reenvuelve las
excepciones de dominio conocidas con un `CommandRejection(code, message)`
como `details`, que sí sobrevive el viaje; `WalletCommandServiceImpl` lo
traduce a `CommandRejectedException` y el advice REST lo mapea por `code`.
El agregado sigue lanzando excepciones de dominio planas; sus pruebas no
cambian.

### Quién pregunta: `X-User-Id`

Desde la Tarjeta 53 el gateway valida el JWT en `/finance/api/v1/accounts/**`
e inyecta `X-User-Id` (el `sub` del token, que es el mismo UUID que la app usa
como `accountId`). `CallerOwnershipInterceptor` lo compara con `{accountId}`
en **todas** las rutas `/api/v1/accounts/**` — lecturas incluidas — y responde
`403 forbidden` si falta, no es un UUID o no coincide. Un `{accountId}` mal
formado no lo decide el interceptor: el controller responde `400` como
siempre.

---

## Pendiente para cuando se implemente

- ~~Cómo Investment solicita un débito (comando directo, o su propio evento que
  Finance escucha).~~ → **resuelto en la Tarjeta 55: ninguno de los dos.** El
  móvil pide el débito por REST y le pasa el `debitId` a Investment.
- Qué pasa con el dinero si el débito sale bien y la inversión falla
  después (subasta ya llena, por ejemplo). **No hay endpoint de reverso.** Es la
  decisión 3 de la Tarjeta 56 (móvil); acá queda anotado como hueco conocido,
  no resuelto.
