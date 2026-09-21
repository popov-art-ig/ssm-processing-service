package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;

class NoActiveProcessesGuardTest {

    private final ProcessRepository processRepository = mock(ProcessRepository.class);
    private final NoActiveProcessesGuard guard = new NoActiveProcessesGuard(processRepository);

    @Test
    void returnsTrueWhenNoProcessesExist() {
        Template template = template();
        when(processRepository.findByTemplateRef(template.getId())).thenReturn(List.of());

        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenProcessInProgress() {
        Template template = template();
        ProcessInstance process = process("InProgress");
        when(processRepository.findByTemplateRef(template.getId())).thenReturn(List.of(process));

        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueWhenProcessIsTerminal() {
        Template template = template();
        ProcessInstance process = process("Approved");
        when(processRepository.findByTemplateRef(template.getId())).thenReturn(List.of(process));

        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenEntityIsNotTemplate() {
        TransitionContext context = new TransitionContext("not a template", UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private Template template() {
        return Template.builder()
                .id(UUID.randomUUID())
                .name("T")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.DEPRECATED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .build();
    }

    private ProcessInstance process(String status) {
        return ProcessInstance.builder()
                .id(UUID.randomUUID())
                .entityType("DOCUMENT")
                .entityId(UUID.randomUUID())
                .templateRef(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status(status)
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }
}
