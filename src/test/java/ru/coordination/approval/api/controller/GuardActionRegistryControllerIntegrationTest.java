package ru.coordination.approval.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Рантайм-проверка реестров guard/action. Раньше эти GET-эндпоинты не покрывались тестами
 * вообще: контроллеры читают репозитории, поэтому проверяем их на реальной БД Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("test")
class GuardActionRegistryControllerIntegrationTest {

    @Autowired
    private GuardActionRegistryController controller;

    @Test
    void listsSeededGuards() {
        var body = controller.listGuards().getBody();

        assertThat(body).isNotNull().isNotEmpty();
        assertThat(body).allSatisfy(dto -> {
            assertThat(dto.guardName()).isNotBlank();
            assertThat(dto.beanName()).isNotBlank();
            assertThat(dto.description()).isNotBlank();
        });
        // Guard-бины, засеянные миграциями V18-V25.
        assertThat(body.stream().map(dto -> dto.guardName()))
                .contains("IsInitiator", "AllMandatorySlotsFilled", "AllParticipantsDecided");
    }

    @Test
    void listsSeededActions() {
        var body = controller.listActions().getBody();

        assertThat(body).isNotNull().isNotEmpty();
        assertThat(body).allSatisfy(dto -> {
            assertThat(dto.actionName()).isNotBlank();
            assertThat(dto.beanName()).isNotBlank();
        });
        assertThat(body.stream().map(dto -> dto.actionName()))
                .contains("SetStartedAt", "RecordDecision", "EvaluateAggregation");
    }
}
