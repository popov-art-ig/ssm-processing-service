package ru.coordination.approval.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Настройка CORS для REST API: фронтенд ходит в сервис с другого origin, поэтому без
 * {@link #addCorsMappings} браузер отклоняет запросы.
 *
 * <p>Разрешённые источники захардкожены ({@code localhost:3000} — React, {@code localhost:4200} —
 * Angular) и закрывают только локальную разработку: для реальных окружений список нужно
 * выносить в конфигурацию. {@code allowCredentials(true)} вместе с конкретными origin (а не
 * {@code *}) — осознанное сочетание: браузеры отвергают передачу учётных данных при wildcard.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
