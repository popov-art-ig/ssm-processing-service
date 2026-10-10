package ru.coordination.approval.engine.validation;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Проверяет все карты переходов при старте приложения и не даёт подняться с некорректной картой.
 *
 * <p>Зачем на старте, а не только «при публикации»: в текущем объёме операции публикации карты
 * нет — карты приходят из сид-миграций Flyway. Валидация на старте закрывает именно этот путь
 * и превращает дефект карты из молчаливого отказа во время перехода (что и происходило —
 * переходы «не находились», этапы не закрывались) в явную ошибку запуска со списком нарушений.
 *
 * <p>Когда появится публикация карты через API, тот же {@link TransitionMapValidator} должен
 * вызываться в её транзакции перед сменой статуса карты на {@code PUBLISHED}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransitionMapStartupValidator implements ApplicationRunner {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionMapValidator validator;

    /**
     * Проверяет опубликованные карты и завершает запуск исключением, если хоть одна
     * некорректна: продолжать работу с дефектной картой хуже, чем не подняться — отказ
     * проявился бы позже и как «переход не найден» во время бизнес-операции.
     *
     * <p>Проверяются все карты, а не до первого падения: сообщение содержит нарушения всех карт,
     * чтобы не чинить их по одной за перезапуск.
     */
    @Override
    public void run(ApplicationArguments args) {
        List<StateMachineConfig> published = stateMachineConfigRepository.findAll().stream()
                .filter(config -> config.getStatus() == LifecycleStatus.PUBLISHED)
                .toList();

        if (published.isEmpty()) {
            log.warn("Опубликованные карты переходов не найдены — валидация пропущена");
            return;
        }

        List<String> failures = new ArrayList<>();
        for (StateMachineConfig config : published) {
            List<TransitionMapViolation> violations = validator.validate(config);
            if (!violations.isEmpty()) {
                failures.add("%s/%s v%d: %s".formatted(
                        config.getEntityType(), config.getProcessType(), config.getVersion(),
                        TransitionMapViolation.describe(violations)));
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Найдены некорректные карты переходов (%d из %d):%n%s".formatted(
                            failures.size(), published.size(),
                            String.join(System.lineSeparator(), failures)));
        }

        log.info("Валидация карт переходов пройдена: {} карт(ы)", published.size());
    }
}
