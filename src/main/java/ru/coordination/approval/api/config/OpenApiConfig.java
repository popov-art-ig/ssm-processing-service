package ru.coordination.approval.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Метаданные OpenAPI-описания REST API (springdoc): заголовок, версия, контакты и список
 * серверов, попадающие в Swagger UI и в {@code /v3/api-docs}.
 *
 * <p>Список серверов здесь статический: dev и «production» прописаны строкой
 * ({@code 07_api_contract.md} §2). Если адрес сервиса различается по окружениям, это место
 * придётся параметризовать — сейчас переопределить его через конфигурацию нельзя.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI approvalServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Approval Service API")
                        .description("REST API для системы согласования документов и процессов")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Coordination Team")
                                .email("coordination@example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server"),
                        new Server()
                                .url("https://api.coordination.example.com")
                                .description("Production server")
                ));
    }
}
