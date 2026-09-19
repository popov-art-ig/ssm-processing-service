package ru.coordination.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа модуля «Согласование» (coordination-processing-module).
 *
 * <p>Стек (ADR-028, зафиксирован 2026-09-18): Spring Boot 4.0.3, Spring Cloud 2025.1.1
 * ("Oakwood"), Hibernate 7.4.0, Gradle 8.8, Java 21. Движок машины состояний — собственная
 * реализация ({@code TransitionEngine}, см. 10_architecture.md §7), заменившая архивированный
 * Spring State Machine (ADR-011, заменено).
 */
@SpringBootApplication
public class CoordinationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoordinationApplication.class, args);
    }
}
