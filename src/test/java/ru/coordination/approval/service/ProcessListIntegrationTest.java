package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Рантайм-проверка {@link ProcessService#listProcesses}: список процессов с необязательными
 * фильтрами.
 *
 * <p>Дефект, найденный при деплое в Docker: {@code GET /api/v1/processes} без параметров отвечал
 * 400 «Other specification must not be null», потому что отсутствующий фильтр представлялся
 * {@code null}, а {@code Specification.allOf} null не принимает. Мок-тесты этого не ловили —
 * они всегда передавали хотя бы один фильтр.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessListIntegrationTest {

    @Autowired
    private ProcessService processService;

    /** Главный регрессионный случай: все фильтры отсутствуют. */
    @Test
    void listsWithoutAnyFilters() {
        assertThatCode(() -> processService.listProcesses(null, null, null, 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void listsWithBlankAndEmptyFilters() {
        assertThatCode(() -> processService.listProcesses("", null, "  ", 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void listsWithEachFilterIndividually() {
        assertThatCode(() -> processService.listProcesses("InProgress", null, null, 0, 20))
                .doesNotThrowAnyException();
        assertThatCode(() -> processService.listProcesses(null, UUID.randomUUID(), null, 0, 20))
                .doesNotThrowAnyException();
        assertThatCode(() -> processService.listProcesses(null, null, "CONTRACT", 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void listsWithAllFiltersTogether() {
        assertThatCode(() -> processService.listProcesses(
                "Draft", UUID.randomUUID(), "CONTRACT", 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void respectsPageSizeAndClampsIt() {
        var page = processService.listProcesses(null, null, null, 0, 20);
        assertThat(page).isNotNull();
        assertThat(page.getSize()).isEqualTo(20);

        // size запрашивается больше максимума — сервис ограничивает его (MAX_PAGE_SIZE = 100).
        var clamped = processService.listProcesses(null, null, null, 0, 5000);
        assertThat(clamped.getSize()).isLessThanOrEqualTo(100);

        // неположительный size заменяется значением по умолчанию.
        var defaulted = processService.listProcesses(null, null, null, 0, 0);
        assertThat(defaulted.getSize()).isEqualTo(20);
    }

    @Test
    void negativePageIsTreatedAsFirstPage() {
        var page = processService.listProcesses(null, null, null, -5, 10);
        assertThat(page).isNotNull();
        assertThat(page.getNumber()).isZero();
    }
}
