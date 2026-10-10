package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Guard публикации шаблона: все обязательные слоты маршрута заполнены — у слота задан
 * конкретный пользователь ({@code userId}) либо список допустимых ролей
 * ({@code acceptableRoles}) (PHASE-14). Резолвится {@code guard_registry.handler =
 * 'allMandatorySlotsValid'} (миграция V25); в карте переходов указан как
 * {@code AllMandatorySlotsValid} в guards перехода {@code PublishTemplate}
 * ({@code DRAFT → PUBLISHED}, {@code USER_ACTION}).
 *
 * <p><b>Кода в спецификации нет.</b> {@code guard_registry.code} и {@code handler} заведены
 * миграцией V25 и в {@code 05_guards_actions_registry.md} не описаны: ближайшее по смыслу
 * условие — {@code G-P-004 AllMandatorySlotsFilled} §4.1, но оно определено на уровне
 * {@code PROCESS} и уже реализовано отдельным {@link AllMandatorySlotsFilledGuard}. Путать их
 * нельзя: там проверяется «есть ли участник в текущей итерации этапа процесса», здесь —
 * статическая корректность {@code actorSlots} шаблона до публикации.
 *
 * <p><b>Отличие от G-P-004.</b> Спецификация формулирует условие как «{@code slot.userId != null}»,
 * то есть требует уже выбранного исполнителя. Здесь это ослаблено до «{@code userId} <i>или</i>
 * непустой список допустимых ролей»: слот, заполняемый по роли, в момент публикации шаблона
 * пользователя ещё не имеет, и буквальная проверка запретила бы публикацию любого такого
 * маршрута. Это осознанное расхождение, зафиксированное описанием реестра в V25.
 *
 * <p>{@code false} означает «шаблон публиковать нельзя»; побочных эффектов у guard'а нет.
 */
@Component("allMandatorySlotsValid")
@RequiredArgsConstructor
public class AllMandatorySlotsValidGuard implements Guard {

    /**
     * @return {@code false}, если у шаблона нет этапов, либо у какого-то обязательного слота не
     *         заданы ни пользователь, ни допустимые роли
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return false;
        }

        List<StageTemplate> stages = template.getStages();
        if (stages == null || stages.isEmpty()) {
            return false;
        }

        for (StageTemplate stage : stages) {
            List<SlotTemplate> slots = stage.getActorSlots();
            if (slots == null) {
                continue;
            }

            for (SlotTemplate slot : slots) {
                if (slot.isRequired()) {
                    boolean hasUserId = slot.getUserId() != null;
                    boolean hasRoles = slot.getAcceptableRoles() != null && !slot.getAcceptableRoles().isEmpty();

                    if (!hasUserId && !hasRoles) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
