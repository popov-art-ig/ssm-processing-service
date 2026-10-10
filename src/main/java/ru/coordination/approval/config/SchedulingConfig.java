package ru.coordination.approval.config;

import java.time.Duration;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Базовая инфраструктура планировщика: распределённые блокировки через Shedlock
 * поверх таблицы {@code shedlock} (08_db_schema.md §18, ADR-013).
 *
 * <p>Сами задачи (публикация Outbox, автоархивация, напоминания и т.д. — 10_architecture.md
 * §11) реализуются в последующих фазах; здесь фиксируется только базовая конфигурация.
 *
 * <p><b>Реальных задач планировщика в коде пока нет.</b> {@code @EnableScheduling} активирует
 * инфраструктуру Spring, но ни одного метода с {@code @Scheduled} в проекте не объявлено, и
 * {@code @SchedulerLock} не используется нигде. То есть блокировку сейчас накладывать не на что,
 * таблица {@code shedlock} остаётся пустой, а {@code defaultLockAtMostFor} начнёт действовать
 * только с появлением первой задачи. Включение планировщика без задач безвредно, но проверять
 * Shedlock-конфигурацию в рантайме до реализации джобов нечем.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    /**
     * Провайдер блокировок Shedlock поверх таблицы {@code shedlock} (DDL — миграция
     * {@code V13__shedlock.sql}, описание — {@code 08_db_schema.md} §18.1).
     *
     * <p>{@code usingDbTime()} берёт время из БД, а не из JVM: при нескольких экземплярах
     * приложения расхождение их системных часов иначе приводило бы к взаимному перехвату одной
     * блокировки. Бин используется только аннотацией {@code @SchedulerLock}; сам он ничего не
     * блокирует и в отсутствие {@code @Scheduled}-методов не вызывается.
     *
     * @param dataSource источник данных приложения; таблица {@code shedlock} создаётся Flyway
     *                   до старта задач, поэтому к моменту первого вызова она уже есть
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
