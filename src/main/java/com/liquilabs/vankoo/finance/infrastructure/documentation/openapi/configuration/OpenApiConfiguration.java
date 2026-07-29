package com.liquilabs.vankoo.finance.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    // General Info
    @Value("${documentation.application.title}")
    private String applicationTitle;

    @Value("${documentation.application.description}")
    private String applicationDescription;

    @Value("${documentation.application.version}")
    private String applicationVersion;

    @Value("${documentation.local-url}")
    private String localUrl;

    @Value("${documentation.gateway-url}")
    private String gatewayUrl;

    @Bean
    public OpenAPI financeServiceOpenApi() {
        // Configure API information
        var info = new Info()
                .title(applicationTitle)
                .description(applicationDescription)
                .version(applicationVersion);

        return new OpenAPI()
                .openapi("3.1.0")
                .info(info)
                .servers(List.of(
                        new Server().url(localUrl).description("Local Server (Direct access)"),
                        new Server().url(gatewayUrl).description("API Gateway (Production/Docker)")
                ));
    }
}
