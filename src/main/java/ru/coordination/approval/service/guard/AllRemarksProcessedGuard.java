package ru.coordination.approval.service.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * «Все замечания процесса обработаны» — предусловие возобновления процесса после возврата на
 * доработку (PHASE-08, обновлён в PHASE-11). Резолвится {@code guard_registry.handler =
 * 'allRemarksProcessedGuard'} (миграция V23), включён в guards перехода {@code ResumeProcess}
 * ({@code OnRework → InProgress}, {@code USER_ACTION}) — препятствует возобновлению, пока в
 * процессе есть незакрытые замечания.
 *
 * <p><b>Контракт.</b> {@code true} — незакрытых замечаний нет, процесс можно возобновить;
 * {@code false} — есть замечания в статусах {@code Open} или {@code InProgress}. Побочных
 * эффектов нет; проверка идёт отдельным запросом к {@code remark}, а не по коллекции в памяти —
 * {@link RemarkRepository} вызывается из перехода процесса, когда связанные замечания в
 * загруженном графе могут быть неактуальны.
 *
 * <p><b>Кода в спецификации нет, реализация неполная.</b> В {@code 05_guards_actions_registry.md}
 * ближайшее по смыслу условие — {@code G-P-006 AllActiveRemarksHandled} §4.1 («нет замечаний в
 * статусах {@code Active} или {@code InProgress}»); код {@code G-P-008} занят условием
 * {@code AllStagesApprovedWithComments}, поэтому ссылка на §4.1 в реестре V23 неточна.
 *
 * <p><b>Известное расхождение по статусам.</b> {@code RemarkService} создаёт замечание сразу в
 * статусе {@code Active}, а статуса {@code Open} в {@code status_registry} для
 * {@code entityType = REMARK} (V24) нет вообще. Поэтому «действующее» замечание ({@code Active})
 * этой проверкой не видно: guard возвращает {@code true}, и процесс возобновляется с
 * необработанными замечаниями. Буквальная реализация G-P-006 должна проверять {@code Active} и
 * {@code InProgress}; правка вынесена в отдельную задачу, комментарий фиксирует дефект, а не
 * закрепляет его.
 *
 * <p><b>Описание в реестре устарело.</b> Строка {@code guard_registry} из V23 называет действие
 * заглушкой, которая «всегда true», — это не так: запрос к {@code remark} выполняется. Устаревшее
 * описание стоит поправить вместе с исправлением набора статусов.
 *
 * <p>Ветка «сущность не {@code ProcessInstance}» возвращает {@code true}: guard объявлен только в
 * процессе, и отказ здесь заблокировал бы возобновление из-за чужого контекста.
 */
@Component("allRemarksProcessedGuard")
@RequiredArgsConstructor
public class AllRemarksProcessedGuard implements Guard {

    private final RemarkRepository remarkRepository;

    /**
     * @return {@code true}, если в процессе нет замечаний в статусах {@code Open} или
     *         {@code InProgress} (см. известное расхождение по статусу {@code Active} в
     *         описании класса)
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return true;
        }

        return !remarkRepository.existsByProcessIdAndStatusIn(
                process.getId(),
                java.util.List.of("Open", "InProgress"));
    }
}
