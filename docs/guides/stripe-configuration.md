# Configuración de Stripe en Finance

Qué variables de entorno necesita `vankoo-finance-service` para hablar con Stripe,
de dónde sale cada valor, y qué error verás si falta alguna.

Las variables se declaran en `src/main/resources/application.yaml` bajo el prefijo
`stripe:` y las lee `StripePaymentProperties`. El archivo `.env.example` de la raíz
tiene la lista completa de variables del servicio, no solo las de Stripe.

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

## El endpoint de webhooks

```
POST /v1/payment-providers/stripe/webhooks
```

En local, con el perfil `dev`, eso es `http://localhost:8083/v1/payment-providers/stripe/webhooks`.

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
stripe listen --forward-to localhost:8083/v1/payment-providers/stripe/webhooks
```

`stripe listen` imprime al arrancar el `whsec_...` que debes poner en
`STRIPE_WEBHOOK_SECRET`. Déjalo corriendo en una terminal aparte mientras pruebas.

Para disparar un evento concreto sin pagar a mano:

```bash
stripe trigger checkout.session.completed
```

---

## Secretos

- **Nunca** en el repositorio. `.env` está en `.gitignore`; solo se versiona
  `.env.example`, con valores de ejemplo.
- Si una clave se filtra, se revoca desde *Developers → API keys*; rotarla es
  inmediato y no requiere desplegar nada más que la variable nueva.
- Los logs ya están limpios y conviene que sigan así: ante un error de la API,
  `StripePaymentProvider` registra `operation`, `statusCode`, `code`, `requestId`
  y el mensaje —lo que hace falta para encontrar la llamada en el dashboard— y
  nunca la clave ni el cuerpo. Ante una firma inválida registra solo el motivo:
  un cuerpo sin verificar no es evidencia de nada y no debe persistirse.

---

## Ver también

- `.env.example` — todas las variables del servicio, no solo las de Stripe.
- `docs/contracts/finance-contracts.md` — el contrato del puerto `PaymentProvider`
  y la taxonomía de estados.
- `docs/uml/finance-webhook-sequence-diagram.puml` — el flujo completo del webhook.
