package com.liquilabs.vankoo.finance.infrastructure.configuration;

import com.liquilabs.vankoo.finance.interfaces.rest.interceptors.CallerOwnershipInterceptor;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires {@link CallerOwnershipInterceptor} onto every account-scoped route.
 *
 * <p>The pattern is the whole {@code /accounts/**} subtree on purpose — reads
 * included — so that a new account-scoped endpoint is protected by default
 * rather than by remembering to add it. Deposits' own {@code /deposits/**}
 * routes are not account-scoped in the path and stay outside; the Stripe
 * webhook never carries a caller and is outside too.
 *
 * <p>A {@code WebMvcConfigurer} bean is picked up by {@code @WebMvcTest}
 * slices, so controller tests exercise the interceptor as well.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    static final String ACCOUNT_SCOPED_ROUTES = "/api/v1/accounts/**";

    @Override
    public void addInterceptors(@Nonnull InterceptorRegistry registry) {
        registry.addInterceptor(new CallerOwnershipInterceptor())
                .addPathPatterns(ACCOUNT_SCOPED_ROUTES);
    }
}
