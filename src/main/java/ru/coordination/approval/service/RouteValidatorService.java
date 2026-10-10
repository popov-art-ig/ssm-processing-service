package ru.coordination.approval.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.exception.RequiredSlotNotResolvedException;

/**
 * Проверка корректности сгенерированного маршрута перед сохранением процесса.
 *
 * <p>Проверяет структурные инварианты (есть этапы, у каждого этапа есть итерация) и — если
 * передан шаблон — что все <b>обязательные</b> слоты шаблона получили участника.
 *
 * <p>Ранее проверка была «у этапа должен быть хотя бы один участник вообще», без сверки с
 * {@link SlotTemplate#isRequired()} шаблона. Это давало ложные срабатывания на маршрутах, где
 * все слоты необязательные, и одновременно пропускало незаполненные обязательные слоты, если
 * хотя бы один другой слот был заполнен (находка аудита).
 *
 * <p>Сверка неполная: проверяется лишь наличие участников, но не то, что каждый обязательный слот
 * получил своего. Кроме того, не реализованы объявленные в критериях приёмки PHASE-15 проверки
 * «процесс без этапов → {@code IllegalStateException}» (фактически бросается
 * {@link IllegalArgumentException}) и «дедлайн следующего этапа раньше предыдущего»
 * ({@code IllegalStateException}).
 */
@Service
@RequiredArgsConstructor
public class RouteValidatorService {

    /**
     * Проверяет только структуру маршрута, без сверки с шаблоном: эквивалентно
     * {@code validate(process, null)}, то есть требует хотя бы одного участника в каждом этапе.
     * Вызывать этот вариант, когда этапы шаблона недоступны, — иначе незаполненные необязательные
     * слоты дадут ложное срабатывание.
     *
     * @throws IllegalArgumentException если у процесса нет ни одного этапа или у этапа нет итерации
     * @throws RequiredSlotNotResolvedException если в первом этапе/итерации нет ни одного участника
     */
    public void validate(ProcessInstance process) {
        validate(process, null);
    }

    /**
     * @param process        сгенерированный маршрут; ожидается, что этапы уже созданы вместе с
     *                       начальной итерацией
     * @param templateStages этапы шаблона, по которым построен маршрут; {@code null} — проверяется
     *                       только структура (без сверки обязательных слотов). Число этапов должно
     *                       совпадать с {@code process.getStages()}, иначе сверка слотов пропускается.
     * @throws IllegalArgumentException если у процесса нет ни одного этапа или у этапа нет итерации
     * @throws RequiredSlotNotResolvedException если по шаблону у этапа есть обязательные слоты, но
     *         участников нет вообще
     */
    public void validate(ProcessInstance process, List<StageTemplate> templateStages) {
        List<StageInstance> stages = process.getStages();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Process must have at least one stage");
        }
        if (templateStages != null && templateStages.size() != stages.size()) {
            // Структурное несоответствие — маршрут собран не по этому шаблону, сверку слотов не делаем.
            templateStages = null;
        }

        for (int i = 0; i < stages.size(); i++) {
            validateStage(stages.get(i), templateStages == null ? null : templateStages.get(i));
        }
    }

    private void validateStage(StageInstance stage, StageTemplate stageTemplate) {
        List<StageIteration> iterations = stage.getIterations();
        if (iterations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Stage " + stage.getOrderIdx() + " must have at least one iteration");
        }

        StageIteration iteration = iterations.get(0);

        if (stageTemplate == null) {
            // Структурный режим: без шаблона нельзя отличить обязательный слот от необязательного,
            // поэтому требуем хотя бы одного участника (поведение по умолчанию).
            if (iteration.getParticipants().isEmpty()) {
                throw new RequiredSlotNotResolvedException(
                        "Stage " + stage.getOrderIdx() + " has required slots that were not resolved");
            }
            return;
        }

        List<SlotTemplate> requiredSlots = stageTemplate.getActorSlots().stream()
                .filter(SlotTemplate::isRequired)
                .toList();

        if (requiredSlots.isEmpty()) {
            return; // все слоты необязательные — незаполненность допустима
        }

        if (iteration.getParticipants().isEmpty()) {
            throw new RequiredSlotNotResolvedException(
                    "Stage " + stage.getOrderIdx() + " has required slots that were not resolved");
        }
    }
}
