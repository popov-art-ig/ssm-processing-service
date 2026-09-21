package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionResult;

/**
 * Интеграционный тест для PHASE-11: жизненный цикл замечаний.
 * Проверяет полный цикл: создание → обработка → завершение замечания.
 */
@SpringBootTest
@ActiveProfiles("test")
class RemarkLifecycleIntegrationTest {

    @Autowired
    private RemarkService remarkService;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private RemarkRepository remarkRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    void fullLifecycleCreateAndProcess() {
        UUID authorId = UUID.randomUUID();
        ProcessInstance process = createProcess(authorId);
        StageInstance stage = process.getStages().get(0);

        Remark remark = remarkService.createRemark(
                process.getId(),
                stage.getId(),
                "Исправить опечатку в пункте 2.3",
                authorId);

        assertThat(remark.getStatus()).isEqualTo("Open");
        assertThat(remark.getActivatedBy()).isEqualTo(authorId);
        assertThat(remark.getActivatedAt()).isNotNull();
        assertThat(remark.getText()).isEqualTo("Исправить опечатку в пункте 2.3");

        TransitionResult processResult = remarkService.processRemark(
                remark.getId(),
                "Исправлено",
                authorId);

        assertThat(processResult.performed()).isTrue();

        remark = remarkRepository.findById(remark.getId()).orElseThrow();
        assertThat(remark.getStatus()).isEqualTo("Processed");
    }

    @Test
    void createAndReject() {
        UUID authorId = UUID.randomUUID();
        ProcessInstance process = createProcess(authorId);
        StageInstance stage = process.getStages().get(0);

        Remark remark = remarkService.createRemark(
                process.getId(),
                stage.getId(),
                "Неактуально",
                authorId);

        assertThat(remark.getStatus()).isEqualTo("Open");

        TransitionResult rejectResult = remarkService.rejectRemark(
                remark.getId(),
                "Уже исправлено",
                authorId);

        assertThat(rejectResult.performed()).isTrue();

        remark = remarkRepository.findById(remark.getId()).orElseThrow();
        assertThat(remark.getStatus()).isEqualTo("Rejected");
    }

    @Test
    void processRemarkBlockedWhenNotAssignee() {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        ProcessInstance process = createProcess(authorId);
        StageInstance stage = process.getStages().get(0);

        Remark remark = remarkService.createRemark(
                process.getId(),
                stage.getId(),
                "Test remark",
                authorId);

        TransitionResult processResult = remarkService.processRemark(
                remark.getId(),
                "Done",
                otherUserId);

        assertThat(processResult.performed()).isFalse();

        remark = remarkRepository.findById(remark.getId()).orElseThrow();
        assertThat(remark.getStatus()).isEqualTo("Open");
    }

    @Test
    void rejectRemarkBlockedWhenNotAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        ProcessInstance process = createProcess(authorId);
        StageInstance stage = process.getStages().get(0);

        Remark remark = remarkService.createRemark(
                process.getId(),
                stage.getId(),
                "Test remark",
                authorId);

        TransitionResult rejectResult = remarkService.rejectRemark(
                remark.getId(),
                "Not valid",
                otherUserId);

        assertThat(rejectResult.performed()).isFalse();

        remark = remarkRepository.findById(remark.getId()).orElseThrow();
        assertThat(remark.getStatus()).isEqualTo("Open");
    }

    private ProcessInstance createProcess(UUID initiatorId) {
        Template template = templateRepository.save(Template.builder()
                .id(UUID.randomUUID())
                .name("Test template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .build());

        Instant now = Instant.now();
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .entityType("DOCUMENT")
                .entityId(UUID.randomUUID())
                .templateRef(template.getId())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(initiatorId)
                .createdAt(now)
                .stages(new ArrayList<>())
                .build();

        StageInstance stage = StageInstance.builder()
                .id(UUID.randomUUID())
                .process(process)
                .orderIdx(0)
                .originalOrderIdx(0)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .decisionMode(DecisionMode.AND)
                .mandatory(true)
                .executionOrder(ExecutionOrder.PARALLEL)
                .status("Active")
                .createdAt(now)
                .build();

        process.getStages().add(stage);

        return processRepository.save(process);
    }
}
