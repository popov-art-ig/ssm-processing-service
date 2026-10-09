package ru.coordination.approval.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.api.dto.template.ApplicabilityRuleDto;
import ru.coordination.approval.api.dto.template.ArchiveTemplateRequest;
import ru.coordination.approval.api.dto.template.CreateTemplateRequest;
import ru.coordination.approval.api.dto.template.DeprecateTemplateRequest;
import ru.coordination.approval.api.dto.template.ForkTemplateRequest;
import ru.coordination.approval.api.dto.template.PublishTemplateRequest;
import ru.coordination.approval.api.dto.template.SlotTemplateDto;
import ru.coordination.approval.api.dto.template.StageTemplateDto;
import ru.coordination.approval.api.dto.template.TemplateDto;
import ru.coordination.approval.api.dto.template.UpdateTemplateRequest;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.domain.template.TemplateRepository;

/**
 * Жизненный цикл шаблона через REST-контроллер: создание → обновление → публикация → снятие с
 * публикации → архивация, плюс форк и валидация.
 *
 * <p>Контекст поднимается с {@code spring.jpa.open-in-view=false} (как в проде,
 * {@code application.yml}) — иначе тест не поймал бы {@code LazyInitializationException} при
 * маппинге ленивых коллекций {@code stages -> actorSlots}. Указание свойств здесь создаёт
 * отдельный контекст, поэтому на другие тесты это не влияет.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
class TemplateLifecycleIntegrationTest {

    @Autowired
    private TemplateController controller;

    @Autowired
    private TemplateRepository templateRepository;

    // ------------------------------------------------------------------ создание

    @Test
    void createThenPublishThenDeprecateThenArchive() {
        UUID actor = UUID.randomUUID();

        TemplateDto created = createDraft(actor);
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.parentTemplateId()).isNull();
        assertThat(created.stages()).hasSize(2);
        assertThat(created.stages().get(0).actorSlots()).hasSize(1);

        TemplateDto published = controller.publishTemplate(
                created.id(), new PublishTemplateRequest(actor)).getBody();
        assertThat(published).isNotNull();
        assertThat(published.status()).isEqualTo("PUBLISHED");
        // SetPublishedTimestampAction заполняет publishedAt/publishedBy.
        assertThat(published.publishedAt()).isNotNull();

        TemplateDto deprecated = controller.deprecateTemplate(
                created.id(), new DeprecateTemplateRequest(actor)).getBody();
        assertThat(deprecated).isNotNull();
        assertThat(deprecated.status()).isEqualTo("DEPRECATED");

        TemplateDto archived = controller.archiveTemplate(
                created.id(), new ArchiveTemplateRequest(actor)).getBody();
        assertThat(archived).isNotNull();
        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    // ------------------------------------------------------------------ валидация публикации

    @Test
    void publishRejectsTemplateWithoutExecutionOrderForStandard() {
        // ValidateTemplateStructureAction требует executionOrder для STANDARD.
        StageTemplateDto stage =
                stageWith(1, "APPROVAL", null, null, 3, null, List.of(slot(1, UUID.randomUUID())));
        UUID id = createRaw("IT no-order " + UUID.randomUUID(), List.of(stage), List.of());

        assertThatThrownBy(() -> controller.publishTemplate(id, new PublishTemplateRequest(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionOrder");
    }

    /**
     * {@code duration <= 0} запрещён, причём проверка выполняется до записи: иначе сработал бы
     * констрейнт {@code stage_template_duration_check} и клиент получил бы 500 вместо 400.
     */
    @Test
    void createRejectsTemplateWithNonPositiveDuration() {
        StageTemplateDto stage = stageWith(1, "APPROVAL", "AND", "PARALLEL", 0, null,
                List.of(slot(1, UUID.randomUUID())));

        assertThatThrownBy(() -> createRaw("IT zero-duration " + UUID.randomUUID(), List.of(stage), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }

    /**
     * Пустой список этапов останавливает переход уже на guard'е
     * ({@code AllMandatorySlotsValid} возвращает {@code false}), поэтому действие не выполняется
     * и {@code publish} возвращает {@code notPerformed} — контроллер отвечает 409
     * ({@code IllegalStateException}), а не 400.
     */
    @Test
    void publishRejectsTemplateWithoutStages() {
        UUID id = createRaw("IT no-stages " + UUID.randomUUID(), List.of(), List.of());

        assertThatThrownBy(() -> controller.publishTemplate(id, new PublishTemplateRequest(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not applicable");
    }

    @Test
    void publishRejectsMandatorySlotWithoutUserOrRoles() {
        // required=true, но ни userId, ни acceptableRoles — AllMandatorySlotsValid даёт false.
        SlotTemplateDto emptySlot =
                new SlotTemplateDto(null, 1, null, null, List.of(), true, true, false, false);
        StageTemplateDto stage =
                stageWith(1, "APPROVAL", "AND", "PARALLEL", 3, null, List.of(emptySlot));
        UUID id = createRaw("IT empty-slot " + UUID.randomUUID(), List.of(stage), List.of());

        assertThatThrownBy(() -> controller.publishTemplate(id, new PublishTemplateRequest(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * {@code allowedReturnStages} ограничены прошлыми и текущим этапами. Проверка тоже выполняется
     * при сохранении: в БД индексы этапов возврата констрейнтом не ограничены.
     */
    @Test
    void createRejectsAllowedReturnStageGreaterThanCurrentOrder() {
        StageTemplateDto stage = stageWith(1, "APPROVAL", "AND", "PARALLEL", 3, List.of(5),
                List.of(slot(1, UUID.randomUUID())));

        assertThatThrownBy(() -> createRaw("IT bad-return " + UUID.randomUUID(), List.of(stage), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedReturnStages");
    }

    // ------------------------------------------------------------------ обновление и версионирование

    @Test
    void updateOfDraftKeepsSameRowAndVersion() {
        UUID actor = UUID.randomUUID();
        TemplateDto created = createDraft(actor);

        TemplateDto updated = controller.updateTemplate(created.id(),
                new UpdateTemplateRequest("renamed " + UUID.randomUUID(), false, null, false,
                        List.of(stage(1, "APPROVAL", "AND", List.of(slot(1, UUID.randomUUID())))),
                        List.of())).getBody();

        assertThat(updated).isNotNull();
        assertThat(updated.id()).as("черновик правится на месте").isEqualTo(created.id());
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.stages()).hasSize(1);
    }

    @Test
    void updateOfPublishedCreatesNewVersionAndDeprecatesOld() {
        UUID actor = UUID.randomUUID();
        TemplateDto created = createDraft(actor);
        controller.publishTemplate(created.id(), new PublishTemplateRequest(actor));

        TemplateDto newVersion = controller.updateTemplate(created.id(),
                new UpdateTemplateRequest("v2 " + UUID.randomUUID(), false, null, false,
                        List.of(stage(1, "APPROVAL", "AND", List.of(slot(1, UUID.randomUUID())))),
                        List.of())).getBody();

        assertThat(newVersion).isNotNull();
        assertThat(newVersion.id()).as("ADR-023: новая строка").isNotEqualTo(created.id());
        assertThat(newVersion.version()).isEqualTo(2);
        assertThat(newVersion.parentTemplateId()).isEqualTo(created.id());
        assertThat(newVersion.status()).isEqualTo("DRAFT");

        // Fork & Drain: прежняя версия снята с публикации.
        assertThat(controller.getTemplate(created.id()).getBody().status()).isEqualTo("DEPRECATED");
    }

    // ------------------------------------------------------------------ форк

    @Test
    void forkCreatesNewDraftVersionAndKeepsSourceUntouched() {
        UUID actor = UUID.randomUUID();
        TemplateDto source = createDraft(actor);
        controller.publishTemplate(source.id(), new PublishTemplateRequest(actor));

        TemplateDto forked = controller.forkTemplate(source.id(), new ForkTemplateRequest(actor)).getBody();

        assertThat(forked).isNotNull();
        assertThat(forked.id()).isNotEqualTo(source.id());
        assertThat(forked.version()).isEqualTo(2);
        assertThat(forked.parentTemplateId()).isEqualTo(source.id());
        assertThat(forked.status()).isEqualTo("DRAFT");
        // Копируется структура.
        assertThat(forked.stages()).hasSize(source.stages().size());
        assertThat(forked.stages().get(0).actorSlots()).hasSize(1);

        // В отличие от update опубликованного, форк НЕ снимает исходник с публикации.
        assertThat(controller.getTemplate(source.id()).getBody().status()).isEqualTo("PUBLISHED");
    }

    @Test
    void forkOfUnknownTemplateIsNotFound() {
        assertThatThrownBy(() -> controller.forkTemplate(UUID.randomUUID(), new ForkTemplateRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ ошибки клиента

    @Test
    void invalidProcessTypeIsRejectedAsBadRequest() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "IT bad type", "NOT_A_PROCESS_TYPE", false, null, false,
                List.of(stage(1, "APPROVAL", "AND", List.of(slot(1, UUID.randomUUID())))),
                List.of(), UUID.randomUUID());

        assertThatThrownBy(() -> controller.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processType");
    }

    @Test
    void invalidStageTypeIsRejected() {
        StageTemplateDto bad = stage(1, "NOT_A_STAGE_TYPE", "AND", List.of(slot(1, UUID.randomUUID())));
        CreateTemplateRequest request = new CreateTemplateRequest(
                "IT bad stage type", "STANDARD", false, null, false,
                List.of(bad), List.of(), UUID.randomUUID());

        assertThatThrownBy(() -> controller.createTemplate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stageType");
    }

    @Test
    void publishIsRejectedForAlreadyPublishedTemplate() {
        UUID actor = UUID.randomUUID();
        TemplateDto created = createDraft(actor);
        controller.publishTemplate(created.id(), new PublishTemplateRequest(actor));

        // Из PUBLISHED перехода PublishTemplate нет — повторная публикация не должна «проходить».
        assertThatThrownBy(() -> controller.publishTemplate(created.id(), new PublishTemplateRequest(actor)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getUnknownTemplateIsNotFound() {
        assertThatThrownBy(() -> controller.getTemplate(UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ------------------------------------------------------------------ список и фильтры

    @Test
    void listFiltersByProcessTypeAndStatus() {
        UUID actor = UUID.randomUUID();
        TemplateDto created = createDraft(actor);

        var draftStandard = controller.listTemplates("DRAFT", "STANDARD", null).getBody();
        assertThat(draftStandard).isNotNull();
        assertThat(draftStandard).anyMatch(dto -> created.id().equals(dto.id()));

        var published = controller.listTemplates("PUBLISHED", null, null).getBody();
        assertThat(published).isNotNull();
        assertThat(published).noneMatch(dto -> created.id().equals(dto.id()));

        var bogusType = controller.listTemplates(null, "UNIFIED", null).getBody();
        assertThat(bogusType).isNotNull();
        assertThat(bogusType).noneMatch(dto -> created.id().equals(dto.id()));
    }

    @Test
    void listWithUnknownProcessTypeIsRejected() {
        assertThatThrownBy(() -> controller.listTemplates(null, "NOPE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processType");
    }

    // ------------------------------------------------------------------ вспомогательное

    /** Шаблон с двумя этапами и заполненными обязательными слотами: проходит публикацию. */
    private TemplateDto createDraft(UUID actor) {
        StageTemplateDto first = stage(1, "APPROVAL", "AND", List.of(slot(1, UUID.randomUUID())));
        StageTemplateDto second = stage(2, "SIGNING", "ANY_APPROVE", List.of(slot(1, UUID.randomUUID())));

        CreateTemplateRequest request = new CreateTemplateRequest(
                "IT lifecycle " + UUID.randomUUID(),
                "STANDARD",
                true,
                null,
                false,
                List.of(first, second),
                List.of(new ApplicabilityRuleDto(null, 1, List.of("CONTRACT"),
                        List.of("SUPPLY"), java.util.Map.of("amount", java.util.Map.of("gte", 1000)))),
                actor);

        var response = controller.createTemplate(request);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        TemplateDto dto = response.getBody();
        assertThat(dto).isNotNull();
        return dto;
    }

    private UUID createRaw(String name, List<StageTemplateDto> stages, List<ApplicabilityRuleDto> rules) {
        var dto = controller.createTemplate(new CreateTemplateRequest(
                name, "STANDARD", false, null, false, stages, rules, UUID.randomUUID())).getBody();
        assertThat(dto).isNotNull();
        return dto.id();
    }

    /**
     * Этап STANDARD. {@code stageType} и {@code duration} в БД NOT NULL, поэтому в негативных
     * сценариях они задаются явно через {@link #stageWith}: иначе тест падал бы на constraint
     * violation вместо бизнес-валидации шаблона.
     */
    private static StageTemplateDto stage(
            int orderIdx, String stageType, String decisionMode, List<SlotTemplateDto> slots) {
        return stageWith(orderIdx, stageType, decisionMode, "PARALLEL", 3, null, slots);
    }

    private static StageTemplateDto stageWith(
            int orderIdx,
            String stageType,
            String decisionMode,
            String executionOrder,
            Integer duration,
            List<Integer> allowedReturnStages,
            List<SlotTemplateDto> slots) {

        return new StageTemplateDto(
                null,
                orderIdx,
                "Этап " + orderIdx,
                null,
                stageType,
                duration,
                decisionMode,
                executionOrder,
                true,
                true,
                allowedReturnStages,
                slots);
    }

    private static SlotTemplateDto slot(int orderIdx, UUID userId) {
        return new SlotTemplateDto(
                null,
                orderIdx,
                userId,
                null,
                List.of(UUID.randomUUID()),
                true,
                true,
                false,
                false);
    }
}
