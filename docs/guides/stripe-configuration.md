# Configuración de Stripe en Finance

Qué variables de entorno necesita `vankoo-finance-service` para hablar con Stripe,
de dónde sale cada valor, y qué error verás si falta alguna.

Las variables se declaran en `src/main/resources/application.yaml` bajo el prefijo
`stripe:` y las lee `StripePaymentProperties`. **Ese archivo, junto con
`application-dev.yaml`, es la lista autoritativa** de todo lo que el servicio lee
del entorno: cada propiedad aparece ahí con su placeholder `${...}` y su default.
Esta guía no la repite — documenta lo que no se puede deducir leyéndola.

---

## Las cinco variables

| Variable | Propiedad | ¿Obligatoria? | Default |
|---|---|---|---|
| `STRIPE_ENABLED` | `stripe.enabled` | No | `true` |
| `STRIPE_SECRET_KEY` | `stripe.secret-key` | Sí | vacío |
| `STRIPE_WEBHOOK_SECRET` | `stripe.webhook-secret` | Sí | vacío |
| `STRIPE_SUCCESS_URL` | `stripe.success-url` | Sí | vacío |
| `STRIPE_CANCEL_URL` | `stripe.cancel-url` | Sí | vacío |

Los defaults vacíos son deliberados: el servicio **arranca igual** sin ellas. El
fallo no aparece al levantar la aplicación, sino la primera vez que alguien
intenta crear un depósito o llega un webhook. Si estás depurando un
`IllegalStateException` en un entorno recién desplegado, empieza por aquí.

---

## Qué exige cada operación

No todas las operaciones necesitan todas las variables. Esto importa porque
determina qué se rompe y cuándo:

| Operación | Necesita | ¿Respeta `enabled`? |
|---|---|---|
| `createDeposit` | `secret-key`, `success-url`, `cancel-url` | Sí |
| `getDeposit` | `secret-key` | Sí |
| `verifyWebhook` | `webhook-secret` | **No** |

**El matiz que sorprende:** `STRIPE_ENABLED=false` corta las llamadas *salientes*
hacia Stripe, pero **no desactiva la verificación de webhooks entrantes**.
`verifyWebhook` no consulta `enabled` en ningún momento. Es intencional —apagar
la salida no debería hacer que el endpoint acepte cuerpos sin verificar— pero
significa que con `enabled=false` el endpoint sigue exigiendo un
`STRIPE_WEBHOOK_SECRET` válido.

### Errores exactos

Todos son `IllegalStateException`, y todos salen de `StripePaymentProvider`.
Quedan **fuera** de la jerarquía sellada `PaymentProviderException` a propósito:
una configuración ausente es un error de despliegue nuestro, no una respuesta de
Stripe, así que ni se reintenta ni se mapea a un desenlace del depósito.

```
Stripe payments are disabled (stripe.enabled=false)
stripe.secret-key is not configured
stripe.success-url is not configured
stripe.cancel-url is not configured
stripe.webhook-secret is not configured
```

---

## De dónde sale cada valor

### `STRIPE_SECRET_KEY`

Dashboard de Stripe → **Developers → API keys** → *Secret key*.

En desarrollo usa siempre la clave de prueba, que empieza por `sk_test_`. Una
clave `sk_live_` cobra dinero real.

### `STRIPE_WEBHOOK_SECRET`

Empieza por `whsec_`, y **son valores distintos según el entorno**:

- **En local**, lo imprime `stripe listen` al arrancar (ver más abajo). Cambia
  cada vez que reinicias el comando.
- **En un entorno desplegado**, sale de **Developers → Webhooks** → tu endpoint →
  *Signing secret*.

Usar el de un entorno en el otro produce siempre `InvalidWebhookSignatureException`
y un `400`, aunque el payload sea legítimo.

### `STRIPE_SUCCESS_URL` y `STRIPE_CANCEL_URL`

A dónde devuelve Stripe al inversor cuando termina o abandona el Checkout. Deben
ser URLs absolutas —Stripe rechaza la creación de la sesión si no lo son— y
apuntan al frontend, no a este servicio.

No existe una URL de fallo: Stripe Checkout solo tiene esos dos destinos. Un pago
rechazado se reintenta dentro del propio Checkout y, si acaba fallando, llega por
webhook, no por redirección.

---

## Cómo suministrar los valores en local

**Spring Boot no lee archivos `.env`.** No hay nada en el `pom.xml` que lo haga y
el framework no lo trae de serie, así que crear un `.env` en la raíz no surte
ningún efecto: las propiedades se quedan con su default vacío y verás los errores
de la sección anterior. Es una diferencia real con `vankoo-profile-service`, que
es NestJS y ahí `dotenv` sí carga el archivo, y con `vankoo-infra`, donde el que
lo lee es Docker Compose.

Hay dos caminos, y cualquiera de los dos sirve.

### `application-local.yaml` (recomendado)

Crea `config/application-local.yaml` **en la raíz del proyecto** — está en
`.gitignore` — con solo lo que quieras sobrescribir:

```yaml
stripe:
  secret-key: sk_test_...
  webhook-secret: whsec_...
  success-url: http://localhost:5173/deposits/success
  cancel-url: http://localhost:5173/deposits/cancel
```

Y activa el perfil junto al de desarrollo:

```
SPRING_PROFILES_ACTIVE=dev,local
```

El orden importa: `local` va después de `dev`, así que gana. Lo que no declares
ahí conserva el valor de `application-dev.yaml`.

### Variables de entorno en la configuración de ejecución

En IntelliJ, *Run → Edit Configurations → Environment variables*, con los nombres
tal cual aparecen en los placeholders del YAML (`STRIPE_SECRET_KEY`, etc.). Es
para lo que existen los `${...}`, y es el mismo mecanismo que usa el contenedor
(siguiente sección).

Desde una terminal, el equivalente:

```bash
STRIPE_SECRET_KEY=sk_test_... STRIPE_WEBHOOK_SECRET=whsec_... sh ./mvnw spring-boot:run
```

---

## Cómo suministrar los valores cuando Finance corre en el compose

Desde que el servicio tiene `Dockerfile` y bloque `finance-service:` en
`vankoo-infra/docker-compose.yaml`, las cinco variables entran al contenedor por
`environment:`, y el compose las toma del **`.env` de `vankoo-infra`** — no de
este repositorio. `config/application-local.yaml` **no viaja a la imagen** (el
`Dockerfile` solo copia `pom.xml` y `src`, y `.dockerignore` excluye `config/`),
así que lo que tengas ahí no cuenta dentro de Docker.

En `vankoo-infra/.env` (ignorado por git; parte de `.env.example`):

```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_SUCCESS_URL=vankoo://deposits/success
STRIPE_CANCEL_URL=vankoo://deposits/cancel
```

`STRIPE_SUCCESS_URL` y `STRIPE_CANCEL_URL` ya traen esos defaults en el compose:
son el esquema de la app del inversionista, y Chrome vuelve solo a la app tras el
Checkout. Solo hay que sobreescribirlos si pruebas contra un frontend web.

Con las claves vacías el contenedor arranca igual (ver «Qué exige cada
operación»): lo que falla es crear depósitos y verificar webhooks, no el servicio.

El puerto 8083 está publicado en el host, así que **`stripe listen` no cambia**:

```bash
stripe listen --forward-to localhost:8083/api/v1/payment-providers/stripe/webhooks
```

El `whsec_` que imprime va en `STRIPE_WEBHOOK_SECRET` del `.env` de infra, y para
que el contenedor lo lea hay que recrearlo: `docker compose up -d finance-service`
(el compose solo reinyecta `environment:` al crear el contenedor, no en caliente).

Para levantar o reconstruir solo Finance:

```bash
docker compose up -d --build finance-service
```

---

## El endpoint de webhooks

```
POST /api/v1/payment-providers/stripe/webhooks
```

En local, con el perfil `dev`, eso es `http://localhost:8083/api/v1/payment-providers/stripe/webhooks`.

### Eventos a los que suscribirse

`StripePaymentProvider` solo normaliza cuatro tipos, y es la lista exacta a la que
debe estar suscrito el endpoint:

- `checkout.session.completed`
- `checkout.session.async_payment_succeeded`
- `checkout.session.async_payment_failed`
- `checkout.session.expired`

Cualquier otro tipo se acusa con `200` (vía `UnsupportedProviderEventException`),
así que **suscribir de más** solo ensucia el log: no rompe nada, y responder con
error solo conseguiría que Stripe lo reintentara para siempre. **Suscribir de
menos** sí pierde actualizaciones de depósitos, en silencio.

Los eventos `payment_intent.*` no sirven: llevan un id `pi_...`, y lo que
`finance_ops.deposit_provider_references` tiene registrado es el `cs_...` de la
Checkout Session. No resolverían ningún depósito.

### Prueba local con la CLI de Stripe

```bash
stripe login
stripe listen --forward-to localhost:8083/api/v1/payment-providers/stripe/webhooks
```

`stripe listen` imprime al arrancar el `whsec_...` que debes poner en
`STRIPE_WEBHOOK_SECRET`. Déjalo corriendo en una terminal aparte mientras pruebas.

Para disparar un evento concreto sin pagar a mano:

```bash
stripe trigger checkout.session.completed
```

---

## Secretos

- **Nunca** en el repositorio. `config/application-local.yaml` está en `.gitignore`,
  y el `.env` de `vankoo-infra` también; `.env.example` lleva las claves vacías.
- **Y nunca dentro de `src/main/resources/`**, aunque Spring también lo leería
  desde ahí. Maven copia ese directorio a `target/classes`, así que el secreto
  acabaría **empaquetado dentro del jar** — y un jar se comparte y se despliega
  con mucha menos ceremonia que un archivo de configuración. `config/` en la raíz
  es una ubicación por defecto de Spring Boot y no se empaqueta nunca.
- Si una clave se filtra, se revoca desde *Developers → API keys*; rotarla es
  inmediato y no requiere desplegar nada más que la variable nueva.
- Los logs ya están limpios y conviene que sigan así: ante un error de la API,
  `StripePaymentProvider` registra `operation`, `statusCode`, `code`, `requestId`
  y el mensaje —lo que hace falta para encontrar la llamada en el dashboard— y
  nunca la clave ni el cuerpo. Ante una firma inválida registra solo el motivo:
  un cuerpo sin verificar no es evidencia de nada y no debe persistirse.

---

## Ver también

- `src/main/resources/application.yaml` y `application-dev.yaml` — la lista
  completa de variables del servicio, con sus defaults.
- `vankoo-infra/docker-compose.yaml` (bloque `finance-service:`) y
  `vankoo-infra/.env.example` — cómo entran esas variables al contenedor.
- `docs/contracts/finance-contracts.md` — el contrato del puerto `PaymentProvider`
  y la taxonomía de estados.
- `docs/uml/finance-webhook-sequence-diagram.puml` — el flujo completo del webhook.
