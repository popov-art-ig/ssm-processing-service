package ru.coordination.approval.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;

/**
 * Рантайм-проверка чтения шаблонов. Методы контроллера вызываются напрямую (без MockMvc и без
 * @Transactional на тесте) — в тех же условиях, что HTTP-вызов: транзакция контроллера закрыта до
 * маппинга. Так воспроизводится {@code LazyInitializationException} на
 * {@code Template.stages -> actorSlots}, найденный при деплое в Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
class TemplateControllerIntegrationTest {

    @Autowired
    private TemplateController controller;

    @Autowired
    private TemplateRepository templateRepository;

    private UUID templateId;

    @BeforeEach
    void createTemplate() {
        UUID actor = UUID.randomUUID();
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("IT template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.DRAFT)
                .version(1)
                .processIterationEnabled(false)
                .requiresResponsibleApproval(false)
                .createdAt(Instant.now())
                // created_by / updated_at — NOT NULL в схеме template (08_db_schema.md §5).
                .createdBy(actor)
                .updatedAt(Instant.now())
                .updatedBy(actor)
                .build();
        templateId = templateRepository.save(template).getId();
    }

    @AfterEach
    void cleanup() {
        if (templateId != null) {
            templateRepository.deleteById(templateId);
        }
    }

    @Test
    void getTemplateMapsLazyCollectionsOutsideSession() {
        assertThatCode(() -> controller.getTemplate(templateId))
                .doesNotThrowAnyException();

        var dto = controller.getTemplate(templateId).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(templateId);
        // Ленивые коллекции: обращение к ним вне сессии и давало 500.
        assertThat(dto.stages()).isNotNull();
        assertThat(dto.applicabilityRules()).isNotNull();
    }

    @Test
    void listTemplatesMapsLazyCollectionsOutsideSession() {
        assertThatCode(() -> controller.listTemplates(null, null, null))
                .doesNotThrowAnyException();

        var body = controller.listTemplates(null, null, null).getBody();

        assertThat(body).isNotNull();
        assertThat(body).isNotEmpty();
        assertThat(body).allSatisfy(dto -> {
            assertThat(dto.stages()).isNotNull();
            assertThat(dto.applicabilityRules()).isNotNull();
        });
    }

    @Test
    void listTemplatesFiltersByStatus() {
        // Шаблон создан в DRAFT: фильтр по PUBLISHED его не должен вернуть.
        var published = controller.listTemplates("PUBLISHED", null, null).getBody();
        assertThat(published).isNotNull();
        assertThat(published).noneMatch(dto -> templateId.equals(dto.id()));

        var draft = controller.listTemplates("DRAFT", null, null).getBody();
        assertThat(draft).isNotNull();
        assertThat(draft).anyMatch(dto -> templateId.equals(dto.id()));
    }

    @Test
    void listTemplatesFiltersByName() {
        var dto = controller.getTemplate(templateId).getBody();
        assertThat(dto).isNotNull();

        // Имя уникально (содержит UUID) — фильтр по нему обязан вернуть ровно этот шаблон.
        var byName = controller.listTemplates(null, null, dto.name()).getBody();
        assertThat(byName).isNotNull();
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).id()).isEqualTo(templateId);

        var noMatch = controller.listTemplates(null, null, "definitely-no-such-template-" + UUID.randomUUID())
                .getBody();
        assertThat(noMatch).isNotNull().isEmpty();
    }

    @Test
    void listTemplatesRejectsUnknownStatus() {
        assertThatThrownBy(() -> controller.listTemplates("NOT_A_STATUS", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_A_STATUS");
    }

    @Test
    void getTemplateThrowsForUnknownId() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getTemplate(unknown))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
