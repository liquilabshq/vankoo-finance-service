package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing.
 *
 * <p>It used to sit on the application class. It was moved here because
 * {@code @EnableJpaAuditing} eagerly asks for the JPA metamodel, and the
 * application class is loaded by every {@code @WebMvcTest} slice — where there
 * is no persistence at all, so the context failed with "JPA metamodel must not
 * be empty". Slices skip plain {@code @Configuration} classes, so the annotation
 * only takes effect where it makes sense: in the running application.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
