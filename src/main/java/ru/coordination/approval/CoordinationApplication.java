package ru.coordination.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа модуля «Согласование» (coordination-processing-module).
 *
 * <p>Стек (ADR-028, зафиксирован 2026-09-18): Spring Boot 4.0.3, Spring Cloud 2025.1.1
 * ("Oakwood"), Hibernate 7.4.0, Gradle 8.14, Java 21. Движок машины состояний — собственная
 * реализация ({@code TransitionEngine}, см. 10_architecture.md §7), заменившая архивированный
 * Spring State Machine (ADR-011, заменено).
 *
 * <p><b>Что собирает приложение.</b> {@code @SpringBootApplication} в пакете
 * {@code ru.coordination.approval} включает сканирование компонентов всех подпакетов:
 * {@code engine} (движок и реестры guard/action), {@code service} (use-case сервисы, guard- и
 * action-бины), {@code api} (контроллеры, мапперы, view-сервисы), {@code domain} (JPA-сущности и
 * репозитории), {@code adapter} (порты внешних интеграций — оба подключены заглушками),
 * {@code config}, {@code dto}, {@code exception}. Схема БД поднимается Flyway
 * ({@code classpath:db/migration}) до старта контекста JPA; распределённые блокировки
 * планировщика настраивает {@code config.SchedulingConfig} (ADR-013).
 *
 * <p><b>Проверка на старте.</b> Публикации карты переходов через API нет — карты приходят из
 * сид-миграций, поэтому корректность всех карт в статусе {@code PUBLISHED} проверяется при
 * запуске ({@code TransitionMapStartupValidator}, реализация {@code ApplicationRunner}): при
 * нарушениях приложение не поднимается, вместо молчаливого отказа во время перехода.
 */
@SpringBootApplication
public class CoordinationApplication {

    /**
     * Запускает Spring-контекст приложения (штатные точки входа: {@code java -jar} и
     * {@code ./gradlew bootRun}); отдельной логики инициализации здесь нет — вся она в бинах.
     */
    public static void main(String[] args) {
        SpringApplication.run(CoordinationApplication.class, args);
    }
}
