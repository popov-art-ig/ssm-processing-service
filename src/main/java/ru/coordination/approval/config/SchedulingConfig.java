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
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
