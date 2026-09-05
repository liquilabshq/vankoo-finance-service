package com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe deployment configuration: credentials and the URLs Checkout redirects
 * the investor to.
 *
 * <p>Configuration only. The {@code PaymentProvider} port's types used to be
 * nested here while {@code domain/} did not exist; they now live next to the
 * port, in {@code application/internal/outboundservices/paymentprovider}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripePaymentProperties {
    private boolean enabled =  true;
    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "";
    // Success and cancel only: Checkout has no failure URL. A declined payment
    // is retried inside Checkout itself and, if it does fail for good, arrives
    // as a webhook rather than a redirect.
    private String cancelUrl = "";
}
