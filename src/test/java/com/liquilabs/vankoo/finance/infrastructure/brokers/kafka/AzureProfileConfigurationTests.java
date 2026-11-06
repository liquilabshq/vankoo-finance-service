package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Comprueba cómo resuelve el perfil {@code azure} la conexión con Azure Event Hubs, la base
 * de datos y Axon Server a partir de las variables de la Container App, sin levantar el
 * contexto.
 */
class AzureProfileConfigurationTests {

    private static final String BINDER = "spring.cloud.stream.kafka.binder.";
    private static final String CONNECTION_STRING = "Endpoint=sb://vankoo-events.servicebus.windows.net/;"
            + "SharedAccessKeyName=finance;SharedAccessKey=secret";

    @Test
    void connectsToEventHubsWithTheContainerAppVariables() throws IOException {
        StandardEnvironment environment = azureEnvironment();

        assertThat(environment.getProperty(BINDER + "brokers"))
                .isEqualTo("vankoo-events.servicebus.windows.net:9093");
        assertThat(environment.getProperty(BINDER + "auto-create-topics")).isEqualTo("false");
        assertThat(environment.getProperty(BINDER + "configuration.security.protocol")).isEqualTo("SASL_SSL");
        assertThat(environment.getProperty(BINDER + "configuration.sasl.mechanism")).isEqualTo("PLAIN");
        assertThat(environment.getProperty(BINDER + "configuration.sasl.jaas.config"))
                .isEqualTo("org.apache.kafka.common.security.plain.PlainLoginModule required "
                        + "username=\"$ConnectionString\" password=\"" + CONNECTION_STRING + "\";");
        assertThat(environment.getProperty("spring.cloud.stream.bindings.finance-out-0.destination"))
                .isEqualTo("vankoo.finance.events.v1");
        assertThat(environment.getProperty(BINDER.replace("binder.", "") + "bindings.finance-out-0.producer.sync"))
                .isEqualTo("true");
    }

    @Test
    void givesUpOnKafkaBeforeAxonTimesOutTheHandler() throws IOException {
        StandardEnvironment environment = azureEnvironment();
        String producer = BINDER + "producer-properties.";

        int requestTimeout = environment.getRequiredProperty(producer + "request.timeout.ms", Integer.class);
        int deliveryTimeout = environment.getRequiredProperty(producer + "delivery.timeout.ms", Integer.class);
        int maxBlock = environment.getRequiredProperty(producer + "max.block.ms", Integer.class);
        int axonHandlerTimeout = environment.getRequiredProperty("axon.timeout.handler.events.timeout-ms", Integer.class);

        // Event Hubs: más de 20 s por petición y al menos 60 s con el productor idempotente.
        assertThat(requestTimeout).isGreaterThanOrEqualTo(60_000);
        assertThat(deliveryTimeout).isGreaterThanOrEqualTo(requestTimeout);
        assertThat(maxBlock + deliveryTimeout).isLessThan(axonHandlerTimeout);
        assertThat(environment.getRequiredProperty(producer + "max.request.size", Integer.class))
                .isLessThan(1_046_528);
        assertThat(environment.getRequiredProperty(BINDER + "configuration.connections.max.idle.ms", Integer.class))
                .isLessThan(240_000);
    }

    @Test
    void reachesPostgresAndAxonServerByTheirAzureNames() throws IOException {
        StandardEnvironment environment = azureEnvironment();

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://vankoo-finance-db.postgres.database.azure.com:5432/vankoo_finance_db"
                        + "?sslmode=require");
        assertThat(environment.getProperty("axon.axonserver.servers")).isEqualTo("vankoo-axon-server:8124");
        assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("false");
    }

    private static StandardEnvironment azureEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("containerApp", Map.of(
                "KAFKA_BROKERS", "vankoo-events.servicebus.windows.net:9093",
                "KAFKA_SASL_PASSWORD", CONNECTION_STRING,
                "FINANCE_DB_HOST", "vankoo-finance-db.postgres.database.azure.com",
                "FINANCE_DB_NAME", "vankoo_finance_db",
                "AXON_SERVER_HOST", "vankoo-axon-server")));
        new YamlPropertySourceLoader()
                .load("application-azure", new ClassPathResource("application-azure.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
