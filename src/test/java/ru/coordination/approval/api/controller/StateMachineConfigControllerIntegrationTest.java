package ru.coordination.approval.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.api.exception.EntityNotFoundException;

/**
 * Рантайм-проверка чтения конфигураций конечных автоматов.
 *
 * <p>Тест вызывает методы контроллера напрямую (без MockMvc и без @Transactional на тесте), то
 * есть в тех же условиях, что и HTTP-вызов: сервисная/контроллерная транзакция уже закрыта.
 * Именно так воспроизводится дефект, найденный при первом деплое в Docker, —
 * {@code LazyInitializationException} на {@code StateMachineConfig.states} в
 * {@code toDto}, потому что методы контроллера не были транзакционными, а
 * {@code spring.jpa.open-in-view=false}.
 */
@SpringBootTest
@ActiveProfiles("test")
class StateMachineConfigControllerIntegrationTest {

    @Autowired
    private StateMachineConfigController controller;

    @Test
    void listConfigsMapsStatesAndTransitionsOutsideSession() {
        assertThatCode(() -> {
            var response = controller.listConfigs();
            List<?> configs = response.getBody();
            assertThat(configs).isNotNull().isNotEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void listConfigsReturnsStatesAndTransitionsPopulated() {
        var body = controller.listConfigs().getBody();
        assertThat(body).isNotNull().isNotEmpty();

        // Именно эти коллекции ленивые: обращение к ним вне сессии и давало 500.
        assertThat(body)
                .allSatisfy(dto -> {
                    assertThat(dto.states()).isNotNull();
                    assertThat(dto.transitions()).isNotNull();
                });

        assertThat(body.stream().mapToInt(dto -> dto.states().size()).sum())
                .as("в сид-картах должны быть состояния")
                .isGreaterThan(0);
        assertThat(body.stream().mapToInt(dto -> dto.transitions().size()).sum())
                .as("в сид-картах должны быть переходы")
                .isGreaterThan(0);
    }

    @Test
    void getConfigMapsStatesAndTransitionsOutsideSession() {
        UUID configId = controller.listConfigs().getBody().get(0).id();

        var dto = controller.getConfig(configId).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.states()).isNotEmpty();
        assertThat(dto.transitions()).isNotEmpty();
    }

    @Test
    void getConfigThrowsForUnknownId() {
        UUID unknown = UUID.randomUUID();

        assertThatCode(() -> controller.getConfig(unknown))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void validateConfigReportsSeededMapsAsValid() {
        List<UUID> configIds = controller.listConfigs().getBody().stream()
                .map(dto -> dto.id())
                .toList();
        assertThat(configIds).isNotEmpty();

        for (UUID configId : configIds) {
            var result = controller.validateConfig(configId).getBody();
            assertThat(result).isNotNull();
            assertThat(result.valid())
                    .as("карта %s должна быть валидна: %s", configId, result.violations())
                    .isTrue();
        }
    }
}
